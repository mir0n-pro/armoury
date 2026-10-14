/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: typed config (endpoints, KC client+secret, playground id, prepare/move/super knobs, metrics) loaded from hauberk.properties + optional overlay
 * 05/17/2026 mir0n  added tokenRelay.type (plain | vanilla | phantom) + isBasicAuth() + basicAuthHeader() so the harness can present HTTP Basic at the edge for Vanilla Token Relay runs; overlay javadoc decoupled from JWS+ naming
 * 05/23/2026 mir0n  added the cmd.* infra-command map (COMMANDS) + static command(key) accessor
 *                   (cmd.<key> property, -Dcmd.<key> CLI override; throws if unset) so a Simulation can
 *                   drive the stack via Cmd.run("key").
 * 06/02/2026 mir0n  added KC_ADMIN_USER / KC_ADMIN_PASSWORD (kc.admin.user / kc.admin.password, default
 *                   admin/q) for the master-realm admin REST sims (race-8c verify, KC orphan cleanup)
 * 06/29/2026 mir0n  added ENYMAN_BASE (enyman.base, required) -- enyMan reached directly for the R6 timeout smoke
 * 07/02/2026 mir0n  added PG_URL / PG_USER / PG_PASSWORD (pg.url / pg.user / pg.password) for the kc-reconcile
 *                   utility's direct JDBC read of esq2025
 * 09/07/2026 mir0n  added KC_ADMIN_CLIENT_ID (kc.admin.client.id, default esq-kcMaster) and KC_ADMIN_SECRET,
 *                   the latter read from the environment only (KC_ADMIN_SECRET / KCMASTER_ADMIN_SECRET)
 * 09/24/2026 mir0n  v1.2.16 -- added SUPER_TREE_WORKERS, SUPER_READACCT_WORKERS, SUPER_TREESMALL_WORKERS,
 *                   SUPER_CACHE_WORKERS and SUPER_CACHESMALL_WORKERS (super.tree.workers,
 *                   super.readacct.workers, super.treesmall.workers, super.cache.workers,
 *                   super.cachesmall.workers), each requireIntMin0
 * 10/13/2026 mir0n  v2.0.0 -- moved into Armoury and reduced to the generic half: the file loader, the
 *                   overlay, ${key} expansion, the typed accessors (require / optional / requireInt /
 *                   requireIntMin0 / requireIntMin / keysUnder), the cmd.* map, METRICS_ENABLED,
 *                   SIMULATIONS_PACKAGE and the four perf capture header names. Every Esquire setting
 *                   (gw.base, bff.base, enyman.base, playground.parent.id, prepare.* / move.* / super.*)
 *                   left for the Esquire-side config class, which reads it through these accessors; the
 *                   KC settings moved to AuthConfig so a target with no OIDC never requires them.
 */
package pro.mir0n.hauberk.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The hauberk configuration file: one properties file at the module root,
 * hauberk.properties, plus an optional overlay named by the system property
 * {@code hauberk.config} whose keys win.
 *
 * This class knows no target. It holds the loading, the expansion and the
 * typed accessors; what the settings MEAN is the caller's business, so a
 * consumer declares its own config class over these accessors (the Esquire
 * services do exactly that for gw.base, playground.parent.id and the
 * prepare / move / super shape knobs).
 *
 * A value may refer to another setting as {@code ${other.key}} and is
 * expanded once at load, which is what lets a target write
 * {@code health.probe.gw=${gw.base}/actuator/health} and keep one copy of
 * the base URL.
 *
 * No env vars, no profiles, no fallbacks. Edit the file to point at a
 * different stack.
 */
public final class HauberkConfig {

    private HauberkConfig() {}

    /** Always-loaded base config (module-root canonical settings). */
    private static final String BASE_CONFIG_FILE = "hauberk.properties";

    /**
     * Optional overlay file, named via system property `hauberk.config`.
     * When set to anything other than the base, the file is loaded on top
     * of hauberk.properties -- its keys win, missing keys fall through to
     * the base. Lets a target overlay carry only the deltas.
     */
    private static final String CONFIG_FILE =
        System.getProperty("hauberk.config", BASE_CONFIG_FILE);

    /** Guard against a ${a} -> ${b} -> ${a} cycle in the file. */
    private static final int MAX_EXPANSION = 32;

    /**
     * Performance-metrics matrix capture (capture-header trigger). Off by
     * default; flipped on via system property hauberk.metrics=true (hauberk.cmd
     * --metrics). When on, every HTTP request from a Simulation that has been
     * wrapped via PerformanceMatrix.instrument(...) carries the trigger header,
     * the target returns its timing headers, and PerformanceMatrix appends a CSV
     * row plus prints a tail-of-run summary.
     */
    public static final boolean METRICS_ENABLED =
        Boolean.parseBoolean(System.getProperty("hauberk.metrics", "false"));

    /**
     * Package the concrete Simulation classes live in -- {@code simulations.package}.
     * SimulationCatalog scans it, so a consumer that keeps its Simulations in its
     * own jar names that package here. Defaults to this harness's own simulations
     * package, which holds only the abstract base and the annotation.
     */
    public static final String SIMULATIONS_PACKAGE;

    /** Capture trigger sent on every instrumented request -- {@code perf.header.capture}. */
    public static final String PERF_H_CAPTURE;

    /**
     * The four timing headers PerformanceMatrix reads off each response, outermost
     * first: the edge as the client sees it, the edge's own inner time, the service
     * behind it, and that service's inner time. Blank means "this target does not
     * report that one" and the column comes out as -1.
     */
    public static final String PERF_H_GW_OUTER;
    public static final String PERF_H_GW_INNER;
    public static final String PERF_H_SRV_OUTER;
    public static final String PERF_H_SRV_INNER;

    /** Optional run label written into every perf-matrix CSV -- {@code perf.run.label}. */
    public static final String PERF_RUN_LABEL;

    private static final Properties PROPS;

    /** Infra-orchestration shell commands (key -> command line), from the cmd.* properties. */
    private static final Map<String, String> COMMANDS;

    static {
        Properties p = new Properties();
        // Always load the base file -- contains canonical settings.
        try (InputStream in = new FileInputStream(BASE_CONFIG_FILE)) {
            p.load(in);
        } catch (IOException e) {
            throw new RuntimeException(
                "Cannot read hauberk base config file '" + BASE_CONFIG_FILE
                + "' (working dir: " + System.getProperty("user.dir") + ")", e);
        }
        // Overlay -- only if a different file was named via hauberk.config.
        if (!BASE_CONFIG_FILE.equals(CONFIG_FILE)) {
            try (InputStream in = new FileInputStream(CONFIG_FILE)) {
                p.load(in);
            } catch (IOException e) {
                throw new RuntimeException(
                    "Cannot read hauberk overlay config file '" + CONFIG_FILE
                    + "' (working dir: " + System.getProperty("user.dir") + ")", e);
            }
        }
        expand(p);
        PROPS = p;

        SIMULATIONS_PACKAGE = optional("simulations.package", "pro.mir0n.hauberk.simulations");

        PERF_H_CAPTURE   = optional("perf.header.capture",   "X-Capture-Metrics");
        PERF_H_GW_OUTER  = optional("perf.header.gw.outer",  "");
        PERF_H_GW_INNER  = optional("perf.header.gw.inner",  "");
        PERF_H_SRV_OUTER = optional("perf.header.srv.outer", "");
        PERF_H_SRV_INNER = optional("perf.header.srv.inner", "");
        PERF_RUN_LABEL   = optional("perf.run.label",        "");

        Map<String, String> cmds = new HashMap<>();
        for (String name : p.stringPropertyNames()) {
            if (name.startsWith("cmd.")) {
                cmds.put(name.substring("cmd.".length()), p.getProperty(name).trim());
            }
        }
        COMMANDS = Map.copyOf(cmds);
    }

    /** The configured infra command for {@code key} (cmd.&lt;key&gt; in the properties);
     *  {@code -Dcmd.<key>=...} overrides. Throws if neither is set. */
    public static String command(String key) {
        String sys = System.getProperty("cmd." + key);
        String ret;
        if (sys != null && !sys.isBlank()) {
            ret = sys.trim();
        } else {
            ret = COMMANDS.get(key);
            if (ret == null || ret.isBlank()) {
                throw new IllegalStateException("Missing command 'cmd." + key + "' in " + CONFIG_FILE);
            }
        }
        return ret;
    }

    /**
     * Required setting. A system property of the same name wins over the file:
     * {@code -D<key>=...} lets the CLI override a value, which is what makes a
     * parameter sweep possible without editing the file on every run.
     */
    public static String require(String key) {
        String ret = System.getProperty(key);
        if (ret == null || ret.isBlank()) {
            ret = PROPS.getProperty(key);
        }
        if (ret == null || ret.isBlank()) {
            throw new IllegalStateException(
                "Missing required setting '" + key + "' in " + CONFIG_FILE);
        }
        return ret;
    }

    /** Optional setting; {@code defaultValue} when neither the system property nor the file has it. */
    public static String optional(String key, String defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            raw = PROPS.getProperty(key);
        }
        String ret = defaultValue;
        if (raw != null && !raw.isBlank()) {
            ret = raw.trim();
        }
        return ret;
    }

    /** Optional setting, lower-cased -- for the small closed vocabularies (a mode, a type). */
    public static String optionalLowercase(String key, String defaultValue) {
        return optional(key, defaultValue).toLowerCase();
    }

    /** Required integer, >= 1. */
    public static int requireInt(String key) {
        return requireIntMin(key, 1);
    }

    /** Required integer, >= 0 -- a knob where zero means "that part is off". */
    public static int requireIntMin0(String key) {
        return requireIntMin(key, 0);
    }

    /** Required integer, >= {@code min}. */
    public static int requireIntMin(String key, int min) {
        String raw = require(key);
        int ret;
        try {
            ret = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "Setting '" + key + "' must be an integer (got: " + raw + ")", e);
        }
        if (ret < min) {
            throw new IllegalStateException(
                "Setting '" + key + "' must be >= " + min + " (got: " + raw + ")");
        }
        return ret;
    }

    /**
     * Every key under {@code prefix}, sorted, prefix included. The way a family of
     * settings is read when the names are not known up front -- health.probe.kc,
     * health.probe.gw, and so on.
     */
    public static List<String> keysUnder(String prefix) {
        List<String> ret = new ArrayList<>();
        for (String name : PROPS.stringPropertyNames()) {
            if (name.startsWith(prefix)) {
                ret.add(name);
            }
        }
        Collections.sort(ret);
        return Collections.unmodifiableList(ret);
    }

    /** The file this configuration came from -- for an error message that can be acted on. */
    public static String configFile() {
        return CONFIG_FILE;
    }

    /**
     * Expands every {@code ${other.key}} reference in place, so the rest of the
     * code never sees one. Package-visible: the config test drives it directly.
     */
    static void expand(Properties p) {
        for (String name : p.stringPropertyNames()) {
            String raw = p.getProperty(name);
            String expanded = expandValue(p, name, raw);
            if (!expanded.equals(raw)) {
                p.setProperty(name, expanded);
            }
        }
    }

    private static String expandValue(Properties p, String ofKey, String raw) {
        String ret = raw;
        int rounds = 0;
        boolean more = true;
        while (more) {
            int open = ret.indexOf("${");
            int close = -1;
            if (open >= 0) {
                close = ret.indexOf('}', open + 2);
            }
            if (open >= 0 && close > open) {
                if (rounds >= MAX_EXPANSION) {
                    throw new IllegalStateException(
                        "Setting '" + ofKey + "' never stops expanding -- a ${} cycle in " + CONFIG_FILE);
                }
                String ref = ret.substring(open + 2, close);
                String val = p.getProperty(ref);
                if (val == null) {
                    throw new IllegalStateException(
                        "Setting '" + ofKey + "' refers to '${" + ref + "}', which is not set in " + CONFIG_FILE);
                }
                ret = ret.substring(0, open) + val + ret.substring(close + 1);
                rounds++;
            } else {
                more = false;
            }
        }
        return ret;
    }
}
