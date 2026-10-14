/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: in-memory snapshot of a perf-matrix CSV (YAML preamble + row arrays) -- consumed by SummaryCommand + DiffCommand
 */
package pro.mir0n.hauberk.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Minimal in-memory model of a perf-matrix CSV file -- the YAML-frontmatter
 * preamble (`# key: value` lines), the column header, the per-request rows
 * (numeric metric columns parsed; non-numeric columns left as raw strings),
 * and the captured reqName per row for per-URL grouping.
 *
 * Shared by `hauberk diff` and `hauberk summary`. Both subcommands read CSVs
 * via {@link #load(Path)}, then call accessors to compute stats.
 */
public final class CsvSnapshot {

    private final Path path;
    private final List<String> preamble;
    private final List<String> header;
    private final List<long[]>  rows;
    private final List<String>  reqNames;
    private final List<String>  numericColumns;

    private CsvSnapshot(Path path, List<String> preamble, List<String> header,
                        List<long[]> rows, List<String> reqNames, List<String> numericColumns) {
        this.path           = path;
        this.preamble       = preamble;
        this.header         = header;
        this.rows           = rows;
        this.reqNames       = reqNames;
        this.numericColumns = numericColumns;
    }

    public Path                fileName()       { return path; }
    public List<String>        preamble()       { return preamble; }
    public List<String>        numericColumns() { return numericColumns; }
    public int                 rowCount()       { return rows.size(); }

    /** All values for one numeric column (all rows). */
    public long[] values(String col) {
        int idx = header.indexOf(col);
        long[] ret = new long[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            ret[i] = rows.get(i)[idx];
        }
        return ret;
    }

    /** Same as values() but restricted to rows where reqName equals the given key. */
    public long[] valuesForReq(String col, String reqName) {
        int idx = header.indexOf(col);
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (reqName.equals(reqNames.get(i))) {
                out.add(rows.get(i)[idx]);
            }
        }
        long[] ret = new long[out.size()];
        for (int i = 0; i < out.size(); i++) ret[i] = out.get(i);
        return ret;
    }

    /** Distinct reqName values present in this CSV (insertion-ordered). */
    public LinkedHashSet<String> reqNames() {
        return new LinkedHashSet<>(reqNames);
    }

    public static CsvSnapshot load(Path path) {
        List<String> preamble = new ArrayList<>();
        List<String> header = new ArrayList<>();
        List<String[]> rawRows = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(path)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                if (line.startsWith("#")) {
                    preamble.add(line);
                } else if (header.isEmpty()) {
                    header.addAll(Arrays.asList(line.split(",")));
                } else {
                    rawRows.add(splitCsvRow(line));
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Cannot read CSV [" + path + "]: " + ex.getMessage(), ex);
        }
        // Detect numeric columns by sampling the first 50 rows.
        List<String> numericCols = new ArrayList<>();
        for (int c = 0; c < header.size(); c++) {
            boolean numeric = !rawRows.isEmpty();
            int probeLimit = Math.min(50, rawRows.size());
            for (int i = 0; i < probeLimit; i++) {
                String v = i < rawRows.size() && c < rawRows.get(i).length ? rawRows.get(i)[c] : "";
                if (v.isEmpty()) continue;
                try { Long.parseLong(v.trim()); }
                catch (NumberFormatException nfe) { numeric = false; break; }
            }
            if (numeric) numericCols.add(header.get(c));
        }
        int reqNameIdx = header.indexOf("reqName");
        List<long[]>  rows = new ArrayList<>(rawRows.size());
        List<String>  reqNames = new ArrayList<>(rawRows.size());
        int[] numIdx = new int[numericCols.size()];
        for (int i = 0; i < numericCols.size(); i++) numIdx[i] = header.indexOf(numericCols.get(i));
        for (String[] raw : rawRows) {
            long[] row = new long[header.size()];
            for (int c = 0; c < header.size(); c++) row[c] = -1;
            for (int n = 0; n < numIdx.length; n++) {
                int idx = numIdx[n];
                if (idx < raw.length) {
                    String v = raw[idx];
                    if (!v.isEmpty()) {
                        try { row[idx] = Long.parseLong(v.trim()); }
                        catch (NumberFormatException nfe) { /* leave -1 */ }
                    }
                }
            }
            rows.add(row);
            String rn = (reqNameIdx >= 0 && reqNameIdx < raw.length) ? raw[reqNameIdx] : "";
            reqNames.add(rn);
        }
        return new CsvSnapshot(path, preamble, header, rows, reqNames, numericCols);
    }

    private static String[] splitCsvRow(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }
}
