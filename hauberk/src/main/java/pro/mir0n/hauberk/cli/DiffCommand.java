/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: picocli "diff" subcommand; side-by-side per-URL diff of two perf-matrix CSV snapshots
 */
package pro.mir0n.hauberk.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * `hauberk diff &lt;csv-a&gt; &lt;csv-b&gt;` -- side-by-side percentile compare
 * of two perf-matrix CSVs produced by PerformanceMatrix.
 *
 * Reads:
 *   - the YAML-frontmatter preamble (lines starting with #) for metadata
 *     (client, sim, scenario, etc.)
 *   - the column-header row and data rows
 *
 * Output: a Markdown-style table comparing median/p95/p99/mean for each
 * numeric metric column, side by side per CSV.
 */
@Command(name = "diff",
        description = "Side-by-side percentile compare of two perf-matrix CSVs.")
public class DiffCommand implements Callable<Integer> {

    @Parameters(arity = "2",
            paramLabel = "CSV",
            description = "Two perf-matrix CSV files to compare.")
    List<Path> csvFiles;

    @Override
    public Integer call() {
        if (csvFiles.size() != 2) {
            System.err.println("diff requires exactly 2 CSV files.");
            return 2;
        }
        CsvSnapshot a = CsvSnapshot.load(csvFiles.get(0));
        CsvSnapshot b = CsvSnapshot.load(csvFiles.get(1));
        printSideBySide(a, b);
        return 0;
    }

    private static void printSideBySide(CsvSnapshot a, CsvSnapshot b) {
        System.out.println();
        System.out.println("[diff] " + a.fileName().getFileName() + "  vs  " + b.fileName().getFileName());
        System.out.println();
        System.out.println("--- preamble (A) ---");
        a.preamble().forEach(line -> System.out.println("  " + line));
        System.out.println("--- preamble (B) ---");
        b.preamble().forEach(line -> System.out.println("  " + line));
        System.out.println();

        // No global aggregate -- mixing heterogeneous request kinds into a single
        // median/p95 yields a number that's just an artifact of the sim's request
        // mix (4 cans + 5 chairs is not 9 comparable things). Only per-URL is
        // honest. Throughput totals stay (cans count vs cans count is valid).
        LinkedHashSet<String> aReqs = a.reqNames();
        LinkedHashSet<String> bReqs = b.reqNames();
        LinkedHashSet<String> bothReqs = new LinkedHashSet<>();
        LinkedHashSet<String> onlyA = new LinkedHashSet<>();
        LinkedHashSet<String> onlyB = new LinkedHashSet<>();
        for (String rn : aReqs) {
            if (bReqs.contains(rn)) bothReqs.add(rn);
            else onlyA.add(rn);
        }
        for (String rn : bReqs) {
            if (!aReqs.contains(rn)) onlyB.add(rn);
        }
        if (bothReqs.isEmpty()) {
            System.out.println("no request URL is present in both CSVs -- nothing to compare.");
        } else {
            System.out.println("per request URL (median values; n_A / n_B):");
            String[] cols = { "clientMs", "gwOuterMs", "gwInnerMs", "srvOuterMs", "srvInnerMs" };
            System.out.printf("  %-58s | %5s/%-5s ", "url", "n_A", "n_B");
            for (String c : cols) {
                System.out.printf("| %-3s %3s %4s ", shortLabel(c, 'A'), shortLabel(c, 'B'), "delta");
            }
            System.out.println();
            System.out.println("  " + "-".repeat(58 + 14 + cols.length * 16));
            for (String rn : bothReqs) {
                long[] aClient = a.valuesForReq("clientMs", rn);
                long[] bClient = b.valuesForReq("clientMs", rn);
                String label = rn.length() <= 58 ? rn : rn.substring(0, 57) + "_";
                System.out.printf("  %-58s | %5d/%-5d ", label, aClient.length, bClient.length);
                for (String c : cols) {
                    long[] aa = a.valuesForReq(c, rn);
                    long[] bb = b.valuesForReq(c, rn);
                    Arrays.sort(aa);
                    Arrays.sort(bb);
                    long am = percentile(aa, 0.50);
                    long bm = percentile(bb, 0.50);
                    long d  = bm - am;
                    System.out.printf("| %3d %3d %+4d ", am, bm, d);
                }
                System.out.println();
            }
            System.out.println();
        }
        if (!onlyA.isEmpty()) {
            System.out.println("only in A (no counterpart in B, excluded from comparison):");
            for (String rn : onlyA) System.out.println("  - " + rn);
        }
        if (!onlyB.isEmpty()) {
            System.out.println("only in B (no counterpart in A, excluded from comparison):");
            for (String rn : onlyB) System.out.println("  - " + rn);
        }
        System.out.printf("rows: A=%d  B=%d%n", a.rowCount(), b.rowCount());
    }

    /** Compact column label for the per-URL header row -- e.g. clientMs -> "c", gwOuterMs -> "gO". */
    private static String shortLabel(String col, char side) {
        String s;
        switch (col) {
            case "clientMs":   s = "c";  break;
            case "gwOuterMs":  s = "gO"; break;
            case "gwInnerMs":  s = "gI"; break;
            case "srvOuterMs": s = "sO"; break;
            case "srvInnerMs": s = "sI"; break;
            default:           s = col;
        }
        return side + s;
    }

    private static long percentile(long[] sorted, double pct) {
        long ret;
        if (sorted.length == 0) {
            ret = -1;
        } else {
            int idx = (int) Math.ceil(pct * sorted.length) - 1;
            if (idx < 0)               idx = 0;
            if (idx >= sorted.length)  idx = sorted.length - 1;
            ret = sorted[idx];
        }
        return ret;
    }

    private static double mean(long[] arr) {
        double ret;
        if (arr.length == 0) {
            ret = -1.0;
        } else {
            long sum = 0;
            for (long v : arr) sum += v;
            ret = (double) sum / arr.length;
        }
        return ret;
    }

}
