/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: picocli "run" subcommand; programmatic Gatling launch with --metrics, --output, --config, --times, shape knobs
 * 09/24/2026 mir0n  v1.2.16 -- added --tree, --readacct, --treesmall, --cache and --cachesmall, each
 *                   overriding its super.*.workers property
 * 10/13/2026 mir0n  v2.0.0 -- moved into Armoury and reduced to the knobs that belong to any run
 *                   (--metrics, --output, --config, --times, --no-health-check) plus the general
 *                   --set key=value. The Esquire shape knobs (--read / --update / --create / --move /
 *                   --tx / --tree / --readacct / --treesmall / --cache / --cachesmall, --prep-*,
 *                   --move-*) went with the Esquire half, which adds them back by extending this
 *                   class: applyProperties() and setIfPresent() are protected for exactly that.
 */
package pro.mir0n.hauberk.cli;

import io.gatling.javaapi.core.Simulation;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import pro.mir0n.hauberk.health.HealthPreCheck;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * `hauberk run &lt;sim&gt; [options]` -- launches a Gatling Simulation
 * programmatically via {@link io.gatling.app.Gatling#fromArgs}.
 *
 * Property values are pushed into JVM system properties before Gatling
 * starts, so {@code HauberkConfig}'s static initialiser picks them up at
 * the same JVM startup.
 *
 * A consumer with shape knobs of its own extends this class, adds its
 * {@code @Option} fields, and overrides {@link #applyProperties()} to push
 * them -- picocli reads the inherited options too, so the whole surface
 * stays on one subcommand.
 */
@Command(name = "run",
        description = "Run a Gatling Simulation (programmatic launch).")
public class RunCommand implements Callable<Integer> {

    @Parameters(arity = "1",
            paramLabel = "SIM",
            description = "Simulation to run (FQCN, simple name, or short alias e.g. entity-smoke).")
    String sim;

    // --- metrics (Performance Matrix capture) ---

    @Option(names = "--metrics",
            description = "Enable Performance Matrix capture (per-request CSV + per-scenario summary).")
    boolean metrics;

    @Option(names = "--output",
            paramLabel = "FOLDER",
            description = "Output base folder. Each run goes into "
                    + "<output>/<sim>-<timestamp>/, holding the Gatling "
                    + "report (index.html, simulation.log, req_*.html) and "
                    + "the perf-matrix CSV(s) when --metrics is on. "
                    + "Default: ./output")
    String outputBase;

    // --- config overlay ---

    @Option(names = "--config",
            paramLabel = "FILE",
            description = "Path to a properties overlay (loaded on top of hauberk.properties).")
    String configFile;

    /** Any setting at all, straight through to a system property, which the
     *  config layer reads in preference to the file. */
    @Option(names = "--set",
            paramLabel = "KEY=VALUE",
            description = "Override one setting for this run (repeatable), e.g. --set gw.base=http://host:7070")
    Map<String, String> overrides;

    // --- repeat ---

    @Option(names = {"--times", "-n"},
            paramLabel = "N",
            description = "Run the sim N times in sequence (each gets its own Gatling report). Default 1.")
    int times = 1;

    @Option(names = "--no-health-check",
            description = "Skip the pre-flight HealthPreCheck (the configured health.probe.* probes).")
    boolean noHealthCheck;

    @Override
    public Integer call() {
        applyProperties();
        if (!noHealthCheck) {
            if (!HealthPreCheck.run()) {
                return 3;
            }
        }
        Class<? extends Simulation> simClass = SimulationCatalog.resolve(sim);
        String base = "./output";
        if (outputBase != null && !outputBase.isBlank()) {
            base = outputBase;
        }
        System.setProperty("hauberk.output.base", base);
        int ret = 0;
        for (int i = 1; i <= times; i++) {
            if (times > 1) {
                System.out.printf("--- pass %d / %d ---%n", i, times);
            }
            java.io.File baseDir = new java.io.File(base);
            java.util.Set<String> before = listSubdirs(baseDir);
            // Gatling's Scala companion exposes a Java-callable fromArgs(int)
            // that returns an exit code without System.exit; the Java-class
            // form (Gatling.main) calls System.exit and breaks --times.
            int code = io.gatling.app.Gatling$.MODULE$.fromArgs(
                    new String[] {
                        "-s",  simClass.getName(),
                        "-rd", simClass.getSimpleName(),
                        "-rf", base
                    });
            if (code != 0) {
                ret = code;
            }
            // Gatling created exactly one new subdir for this run; move our
            // perf-matrix CSVs into it so the run's artefacts ship together.
            java.io.File runDir = newestSubdirSince(baseDir, before);
            if (runDir != null) {
                pro.mir0n.hauberk.perf.PerformanceMatrix.moveFilesInto(runDir);
            }
        }
        return ret;
    }

    /** Snapshot of subdir names under {@code dir}. Empty set if dir absent. */
    private static java.util.Set<String> listSubdirs(java.io.File dir) {
        java.util.Set<String> ret = new java.util.HashSet<>();
        java.io.File[] kids = dir.listFiles(java.io.File::isDirectory);
        if (kids != null) {
            for (java.io.File k : kids) {
                ret.add(k.getName());
            }
        }
        return ret;
    }

    /** Return the most recently modified subdir of {@code dir} not in {@code before}. */
    private static java.io.File newestSubdirSince(java.io.File dir, java.util.Set<String> before) {
        java.io.File ret = null;
        java.io.File[] kids = dir.listFiles(java.io.File::isDirectory);
        if (kids != null) {
            long newest = Long.MIN_VALUE;
            for (java.io.File k : kids) {
                if (!before.contains(k.getName()) && k.lastModified() > newest) {
                    newest = k.lastModified();
                    ret = k;
                }
            }
        }
        return ret;
    }

    /**
     * Pushes this run's options into system properties, BEFORE the config layer
     * is first touched. A subclass that adds knobs overrides this, calls
     * {@code super.applyProperties()}, and pushes its own with
     * {@link #setIfPresent(String, Integer)}.
     */
    protected void applyProperties() {
        // --metrics is the on/off toggle. --output only controls WHERE
        // things go; supplying --output without --metrics gives you the
        // Gatling report in the new folder but no perf-matrix CSV.
        if (metrics) {
            System.setProperty("hauberk.metrics", "true");
        }
        if (configFile != null && !configFile.isBlank()) {
            System.setProperty("hauberk.config", configFile);
        }
        if (overrides != null) {
            for (Map.Entry<String, String> e : overrides.entrySet()) {
                System.setProperty(e.getKey(), e.getValue());
            }
        }
    }

    /** Set the system property for {@code key} only when the option was supplied;
     *  an absent option must leave the file's own value standing. */
    protected static void setIfPresent(String key, Integer val) {
        if (val != null) {
            System.setProperty(key, String.valueOf(val));
        }
    }
}
