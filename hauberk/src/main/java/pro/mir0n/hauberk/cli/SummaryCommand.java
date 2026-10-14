/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: picocli "summary" subcommand; per-URL percentile summary from a saved perf-matrix CSV
 */
package pro.mir0n.hauberk.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * `hauberk summary &lt;csv&gt;` -- print the per-URL percentile summary for a
 * single perf-matrix CSV file. Same shape that PerformanceMatrix produces
 * at sim end, but readable any time after-the-fact from the saved CSV.
 *
 * No aggregate "ALL" row -- mixing heterogeneous request types into a
 * single median is a "4 cans + 5 chairs" sum; only per-URL is honest.
 */
@Command(name = "summary",
        description = "Print the per-URL percentile summary for one perf-matrix CSV.")
public class SummaryCommand implements Callable<Integer> {

    @Parameters(arity = "1",
            paramLabel = "CSV",
            description = "perf-matrix CSV file produced by `hauberk run --metrics`.")
    Path csvFile;

    @Override
    public Integer call() {
        CsvSnapshot snap = CsvSnapshot.load(csvFile);
        System.out.println();
        System.out.println("[summary] " + snap.fileName().getFileName());
        System.out.println();
        System.out.println("--- preamble ---");
        snap.preamble().forEach(line -> System.out.println("  " + line));
        System.out.println();
        java.util.LinkedHashSet<String> reqs = snap.reqNames();
        System.out.printf("per request URL (median values)  --  %d rows across %d request types%n",
                snap.rowCount(), reqs.size());
        for (String rn : reqs) {
            printRow(rn, snap);
        }
        System.out.println();
        return 0;
    }

    private static void printRow(String reqName, CsvSnapshot snap) {
        long[] client = snap.valuesForReq("clientMs",   reqName);
        long[] gwOut  = snap.valuesForReq("gwOuterMs",  reqName);
        long[] gwIn   = snap.valuesForReq("gwInnerMs",  reqName);
        long[] srvOut = snap.valuesForReq("srvOuterMs", reqName);
        long[] srvIn  = snap.valuesForReq("srvInnerMs", reqName);
        Arrays.sort(client);
        Arrays.sort(gwOut);
        Arrays.sort(gwIn);
        Arrays.sort(srvOut);
        Arrays.sort(srvIn);
        long c   = percentile(client, 0.50);
        long gO  = percentile(gwOut,  0.50);
        long gI  = percentile(gwIn,   0.50);
        long sO  = percentile(srvOut, 0.50);
        long sI  = percentile(srvIn,  0.50);
        String label = reqName.length() <= 58 ? reqName : reqName.substring(0, 57) + "_";
        System.out.printf(
                "  %-58s n=%-6d  c=%-3d gO=%-3d gI=%-3d sO=%-3d sI=%-3d   |  net=%-3d gw_self=%-3d in_cluster=%-3d srv_self=%-3d srv_inner=%-3d%n",
                label, client.length,
                c, gO, gI, sO, sI,
                subPositive(c, gO),
                subPositive(gO, gI),
                subPositive(gI, sO),
                subPositive(sO, sI),
                sI);
    }

    private static long percentile(long[] sorted, double pct) {
        long ret;
        if (sorted.length == 0) {
            ret = -1;
        } else {
            int idx = (int) Math.ceil(pct * sorted.length) - 1;
            if (idx < 0)              idx = 0;
            if (idx >= sorted.length) idx = sorted.length - 1;
            ret = sorted[idx];
        }
        return ret;
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
