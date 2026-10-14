/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: per-response transformResponse hook capturing the four observability headers; per-scenario CSV + percentile summary; moved into Gatling run dir post-run
 * 06/03/2026 mir0n  printSummary() made synchronized (same monitor as add()) + column() snapshots
 *                   rows.size() once -- fixes AIOOBE in the post-run summary at high sample counts
 * 10/13/2026 mir0n  v2.0.0 -- moved into Armoury: the five capture header names come from the
 *                   properties file (perf.header.capture / perf.header.gw.outer / .gw.inner /
 *                   .srv.outer / .srv.inner) instead of being compiled-in mirrors of EsqConstants,
 *                   a blank name meaning that column is not reported; instrument() takes the target
 *                   base URL; the CSV preamble carries "label" (perf.run.label) and "target" in place
 *                   of the client / kc_base / gw_base lines
 */
package pro.mir0n.hauberk.perf;

import io.gatling.http.response.Response;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.netty.handler.codec.http.HttpHeaders;
import pro.mir0n.hauberk.config.HauberkConfig;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performance-metrics capture for the hauberk harness.
 *
 * Wires into Gatling's per-response transformResponse callback. Each
 * Scenario writes its OWN CSV; the file is keyed by
 * {@link Session#scenario()} -- the string passed to
 * {@code scenario("...")} in the Simulation source. A "super" Simulation
 * that runs N scenarios in parallel produces N CSVs, one per scenario.
 *
 * Output naming: each scenario writes one CSV named &lt;scenario&gt;.csv
 * inside the per-run sub-folder Gatling creates under
 * `hauberk.output.base` (default `./output`, overridable via
 * `hauberk.cmd run ... --output`). RunCommand moves the CSVs into the
 * Gatling sub-folder after the run so the report + the matrices ship
 * together.
 *
 * File layout:
 *   1. YAML-frontmatter preamble (lines beginning with `# `) describing the
 *      run -- simulation class, scenario, run label, target base URL.
 *   2. CSV column header.
 *   3. One CSV data row per HTTP request.
 *
 * At sim end, {@link #printSummary(String)} prints a percentile table per
 * scenario, broken down by request name (URL pattern). It also closes the
 * per-scenario CSV writers.
 *
 * When HauberkConfig.METRICS_ENABLED is false, instrument() is a no-op
 * wrapper -- zero overhead, no header on the wire, no CSV.
 */
public final class PerformanceMatrix {

    // The target names its own timing headers; the harness only has to agree with it,
    // so the five names are configuration (perf.header.*). A blank name means the target
    // does not report that one and the column comes out as -1.
    public  static final String H_CAPTURE   = HauberkConfig.PERF_H_CAPTURE;
    private static final String H_GW_OUTER  = HauberkConfig.PERF_H_GW_OUTER;
    private static final String H_GW_INNER  = HauberkConfig.PERF_H_GW_INNER;
    private static final String H_SRV_OUTER = HauberkConfig.PERF_H_SRV_OUTER;
    private static final String H_SRV_INNER = HauberkConfig.PERF_H_SRV_INNER;

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyMMddHHmmss").withZone(ZoneOffset.UTC);

    /** Per-scenario state -- holds writer + in-memory rows for summary. */
    private static final ConcurrentHashMap<String, ScenarioBucket> BUCKETS = new ConcurrentHashMap<>();

    /** Simulation simple-class name set by instrument(); used in YAML preamble. */
    private static volatile String simClassName = "Simulation";

    /** Target base URL set by instrument(); used in YAML preamble. */
    private static volatile String targetBase = "";

    private PerformanceMatrix() {}

    /**
     * Wraps a Gatling HttpProtocolBuilder with the capture trigger + response
     * hook. simulationClass and base are recorded in the YAML preamble of every
     * CSV this run produces.
     */
    public static HttpProtocolBuilder instrument(Class<?> simulationClass, String base,
                                                 HttpProtocolBuilder builder) {
        HttpProtocolBuilder ret = builder;
        if (HauberkConfig.METRICS_ENABLED) {
            simClassName = simulationClass.getSimpleName();
            targetBase = base;
            ret = builder
                    .header(H_CAPTURE, "yes")
                    .transformResponse((response, session) -> {
                        try {
                            record(response, session);
                        } catch (Throwable t) {
                            System.err.println("[perf-matrix] record-failure: " + t.getMessage());
                        }
                        return response;
                    });
        }
        return ret;
    }

    /**
     * Print a per-scenario percentile summary block to stderr and close all
     * per-scenario CSV writers. Called from the Simulation's after() hook.
     */
    public static void printSummary(String simulationClassName) {
        if (!HauberkConfig.METRICS_ENABLED) {
            return;
        }
        if (BUCKETS.isEmpty()) {
            System.err.println("[perf-matrix] " + simulationClassName
                    + " -- no rows captured (sim did not exercise the instrumented httpProtocol).");
            return;
        }
        // Sorted by scenario name for stable output.
        List<String> scenarios = new ArrayList<>(BUCKETS.keySet());
        java.util.Collections.sort(scenarios);
        for (String scn : scenarios) {
            BUCKETS.get(scn).printSummary();
        }
        for (ScenarioBucket b : BUCKETS.values()) {
            b.close();
        }
    }

    /**
     * After-run hook: move all currently-open CSVs into the given folder,
     * then forget them. RunCommand calls this once Gatling has finished
     * and we know which sub-folder Gatling actually created (Gatling's
     * subfolder naming is internally derived and not overridable from
     * outside, so we discover it after-the-fact instead of pinning it).
     */
    public static void moveFilesInto(File targetDir) {
        if (!HauberkConfig.METRICS_ENABLED || BUCKETS.isEmpty()) {
            return;
        }
        if (!targetDir.exists()) {
            try {
                Files.createDirectories(targetDir.toPath());
            } catch (IOException ex) {
                System.err.println("[perf-matrix] cannot create target dir ["
                        + targetDir + "]: " + ex.getMessage());
                return;
            }
        }
        for (ScenarioBucket b : BUCKETS.values()) {
            b.close();
            File src = b.csvFile;
            if (src.exists()) {
                // strip the .tmp suffix added during the run
                String name = src.getName();
                if (name.endsWith(".csv.tmp")) {
                    name = name.substring(0, name.length() - ".tmp".length());
                }
                File dst = new File(targetDir, name);
                try {
                    Files.move(src.toPath(), dst.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    b.csvFile = dst; // update so the summary prints the final path
                } catch (IOException ex) {
                    System.err.println("[perf-matrix] cannot move ["
                            + src + "] -> [" + dst + "]: " + ex.getMessage());
                }
            }
        }
        // Across multiple --times iterations, ListCommand re-uses the same
        // PerformanceMatrix static state; clear so the next iteration opens
        // fresh files.
        BUCKETS.clear();
    }

    private static void record(Response response, Session session) {
        String scn = session == null ? "(unknown)" : safe(session.scenario());
        ScenarioBucket bucket = BUCKETS.computeIfAbsent(scn, ScenarioBucket::new);
        int status = response.status().code();
        long clientMs = response.endTimestamp() - response.startTimestamp();
        HttpHeaders h = response.headers();
        long gwOuterMs  = headerMs(h, H_GW_OUTER);
        long gwInnerMs  = headerMs(h, H_GW_INNER);
        long srvOuterMs = headerMs(h, H_SRV_OUTER);
        long srvInnerMs = headerMs(h, H_SRV_INNER);
        String reqName = "";
        try {
            reqName = response.request().getName();
            if (reqName == null) reqName = "";
        } catch (Throwable ignore) {}
        bucket.add(reqName, status, clientMs, gwOuterMs, gwInnerMs, srvOuterMs, srvInnerMs);
    }

    // ---------------------------------------------------------------------

    /** Per-scenario writer + in-memory accumulator. */
    private static final class ScenarioBucket {
        private final String scenarioName;
        // Mutable -- moveFilesInto() repoints this from the temp location to
        // the final Gatling-run sub-folder after the run completes.
        private File csvFile;
        private final PrintWriter csv;
        // Per-row arrays: [clientMs, gwOuterMs, gwInnerMs, srvOuterMs, srvInnerMs].
        private final List<long[]>  allRows  = new ArrayList<>();
        // Per-request-name accumulator (insertion order for output stability).
        private final Map<String, List<long[]>> byRequest = new LinkedHashMap<>();
        private volatile boolean closed = false;

        ScenarioBucket(String scn) {
            this.scenarioName = scn;
            // Temp location during the run -- written directly into the
            // output base. Once Gatling completes and we know which
            // sub-folder it created, RunCommand calls
            // PerformanceMatrix.moveFilesInto(<gatlingDir>) which moves
            // these CSVs into the Gatling run folder, so the final layout
            // ships the report + the perf matrices together.
            //
            // Gatling's sub-folder name is internally derived
            // (<simname-lower>-<ms-timestamp>) and not overridable from a
            // system property without timing risks -- move-after-the-fact
            // is the reliable path.
            String base = System.getProperty("hauberk.output.base", "./output");
            File dirFile = new File(base);
            if (!dirFile.exists()) {
                try {
                    Files.createDirectories(dirFile.toPath());
                } catch (IOException ex) {
                    throw new RuntimeException("Cannot create output dir [" + dirFile + "]: " + ex.getMessage(), ex);
                }
            }
            this.csvFile = new File(dirFile, sanitize(scn) + ".csv.tmp");
            try {
                this.csv = new PrintWriter(Files.newBufferedWriter(csvFile.toPath(), StandardCharsets.UTF_8));
            } catch (IOException ex) {
                throw new RuntimeException("Cannot open CSV [" + csvFile + "]: " + ex.getMessage(), ex);
            }
            writePreamble();
        }

        private void writePreamble() {
            csv.println("# perf-matrix YAML preamble (commented; CSV body starts at next non-# line)");
            csv.println("# simulation:    " + simClassName);
            csv.println("# scenario:      " + scenarioName);
            csv.println("# label:         " + HauberkConfig.PERF_RUN_LABEL);
            csv.println("# target:        " + targetBase);
            csv.println("# start_utc:     " + Instant.now().toString());
            csv.println("timestamp,reqName,httpStatus,clientMs,gwOuterMs,gwInnerMs,srvOuterMs,srvInnerMs");
            csv.flush();
        }

        synchronized void add(String reqName, int status, long clientMs, long gwOuterMs,
                              long gwInnerMs, long srvOuterMs, long srvInnerMs) {
            if (closed) {
                return;
            }
            long[] row = new long[] { clientMs, gwOuterMs, gwInnerMs, srvOuterMs, srvInnerMs };
            allRows.add(row);
            byRequest.computeIfAbsent(reqName, k -> new ArrayList<>()).add(row);
            String safe = reqName.replace(',', ' ').replace('\n', ' ').replace('\r', ' ');
            csv.printf("%s,\"%s\",%d,%d,%d,%d,%d,%d%n",
                    Instant.now().toString(),
                    safe,
                    status, clientMs, gwOuterMs, gwInnerMs, srvOuterMs, srvInnerMs);
            csv.flush();
        }

        synchronized void close() {
            if (!closed) {
                closed = true;
                csv.close();
            }
        }

        // synchronized on the same monitor as add(): a straggler response must not grow a
        // request's row list mid-summary (was: AIOOBE in column() at high sample counts).
        synchronized void printSummary() {
            System.err.println();
            System.err.println("[perf-matrix] scenario=" + scenarioName
                    + " (sim=" + simClassName + ", label=" + HauberkConfig.PERF_RUN_LABEL + ")"
                    + " -- " + allRows.size() + " rows across " + byRequest.size() + " request types");
            // Per-URL only. Aggregating heterogeneous request kinds yields a
            // statistic that's an artifact of the sim's request mix, not a
            // property of the system -- omitted by design.
            for (Map.Entry<String, List<long[]>> e : byRequest.entrySet()) {
                printRow(truncate(e.getKey(), 56), e.getValue());
            }
            System.err.println("  CSV: " + csvFile.getAbsolutePath());
        }

        private static void printRow(String label, List<long[]> rows) {
            long[] client = column(rows, 0);
            long[] gwOut  = column(rows, 1);
            long[] gwIn   = column(rows, 2);
            long[] srvOut = column(rows, 3);
            long[] srvIn  = column(rows, 4);
            Arrays.sort(client);
            Arrays.sort(gwOut);
            Arrays.sort(gwIn);
            Arrays.sort(srvOut);
            Arrays.sort(srvIn);
            long med_c   = percentile(client, 0.50);
            long med_gO  = percentile(gwOut,  0.50);
            long med_gI  = percentile(gwIn,   0.50);
            long med_sO  = percentile(srvOut, 0.50);
            long med_sI  = percentile(srvIn,  0.50);
            System.err.printf(
                "  %-58s n=%-6d  c=%-3d gO=%-3d gI=%-3d sO=%-3d sI=%-3d   |  net=%-3d gw_self=%-3d in_cluster=%-3d srv_self=%-3d srv_inner=%-3d%n",
                label, rows.size(),
                med_c, med_gO, med_gI, med_sO, med_sI,
                subPositive(med_c, med_gO),
                subPositive(med_gO, med_gI),
                subPositive(med_gI, med_sO),
                subPositive(med_sO, med_sI),
                med_sI);
        }

        private static long subPositive(long a, long b) {
            long ret;
            if (a < 0 || b < 0) {
                ret = -1;
            } else {
                ret = a - b;
            }
            return ret;
        }
    }

    // ---- helpers ----

    /** -1 when the target was not configured to report this header at all. */
    private static long headerMs(HttpHeaders h, String name) {
        long ret = -1;
        if (name != null && !name.isEmpty()) {
            ret = parseMs(h.get(name));
        }
        return ret;
    }

    private static long parseMs(String raw) {
        long ret = -1;
        if (raw != null && !raw.isEmpty()) {
            String s = raw.endsWith("ms") ? raw.substring(0, raw.length() - 2).trim() : raw.trim();
            try { ret = Long.parseLong(s); }
            catch (NumberFormatException nfe) {
                try { ret = (long) Double.parseDouble(s); }
                catch (NumberFormatException nfe2) { ret = -1; }
            }
        }
        return ret;
    }

    private static long[] column(List<long[]> rows, int idx) {
        int n = rows.size();                 // snapshot size once -- never let the loop
        long[] ret = new long[n];            // bound exceed the allocated array length
        for (int i = 0; i < n; i++) {
            ret[i] = rows.get(i)[idx];
        }
        return ret;
    }

    private static int validCount(long[] sorted) {
        int n = 0;
        for (long v : sorted) {
            if (v >= 0) n++;
        }
        return n;
    }

    private static long percentile(long[] sorted, double pct) {
        int valid = validCount(sorted);
        long ret;
        if (valid == 0) {
            ret = -1;
        } else {
            int offset = sorted.length - valid;
            int idx = offset + (int) Math.ceil(pct * valid) - 1;
            if (idx < offset)         idx = offset;
            if (idx >= sorted.length) idx = sorted.length - 1;
            ret = sorted[idx];
        }
        return ret;
    }

    private static long subMed(long[] a, long[] b) {
        long va = percentile(a, 0.50);
        long vb = percentile(b, 0.50);
        long ret;
        if (va < 0 || vb < 0) {
            ret = -1;
        } else {
            ret = va - vb;
        }
        return ret;
    }

    private static String safe(String s)             { return s == null ? "(unknown)" : s; }
    private static String sanitize(String s)         { return s.replaceAll("[^A-Za-z0-9_.-]", "_"); }
    private static String truncate(String s, int n)  { return s.length() <= n ? s : s.substring(0, n - 1) + "_"; }
}
