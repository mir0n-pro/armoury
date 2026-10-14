/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/23/2026 mir0n  created: a sim step that runs a configured infra command (cmd.<key>) through the
 *                   OS shell -- lets a Simulation drive the stack itself (stop/start the broker to lose
 *                   JMS messages, restart bizTree mid-creation for the cache-load race). Cross-platform
 *                   (cmd /c on Windows, sh -c elsewhere); blocks until the command exits; logs to stderr.
 * 10/13/2026 mir0n  v2.0.0 -- moved into Armoury unchanged: the step runs whatever the cmd.<key>
 *                   setting says, so it belongs to the harness and not to any one target
 */
package pro.mir0n.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;
import pro.mir0n.hauberk.config.HauberkConfig;

import java.util.concurrent.TimeUnit;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Runs a configured infra-orchestration command (from {@code cmd.<key>} in hauberk.properties) as a
 * scenario step, synchronously. This is how a Simulation orchestrates the stack itself -- no operator:
 *   {@code .exec(Cmd.run("stop-amq"))} ... {@code .exec(Cmd.run("start-amq"))}.
 *
 * The command runs through the OS shell ({@code cmd /c} on Windows, {@code sh -c} otherwise) so the
 * configured value is a plain command line (e.g. {@code docker stop esq-activemq}). The step blocks
 * until the command exits and logs the exit code + output to stderr; a non-zero exit marks the step
 * failed so {@code exitHereIfFailed()} can stop the scenario.
 */
public final class Cmd {

    private Cmd() {}

    private static final long TIMEOUT_SECONDS = 180;   // k8s rollouts (rollout status) can exceed 60s

    public static ChainBuilder run(String key) {
        return run(key, java.util.Map.of());
    }

    /**
     * Run a configured command with extra environment variables set on the child process -- used to
     * feed {@code docker compose} substitution values (e.g. BIZTREE_ON_MISMATCH=SWAP +
     * BIZTREE_SWEEP_INTERVAL_MS=600000 for the recreate-biztree command). A non-zero exit marks the
     * step failed so {@code exitHereIfFailed()} can stop the scenario.
     */
    public static ChainBuilder run(String key, java.util.Map<String, String> env) {
        return exec(session -> execute(key, env) ? session : session.markAsFailed());
    }

    /**
     * Best-effort run: NEVER marks the step failed. Used when the command is EXPECTED to error --
     * e.g. forcing the sweep in TERMINATE mode, where biztree System.exit()s mid-response and the
     * curl connection resets.
     */
    public static ChainBuilder tryRun(String key) {
        return exec(session -> {
            execute(key, java.util.Map.of());
            return session;
        });
    }

    private static boolean execute(String key, java.util.Map<String, String> env) {
        String command = HauberkConfig.command(key);
        // Substitute {KEY} placeholders in the command from the env map -- lets a target embed the
        // value directly (e.g. k8s `kubectl set env ... =MODE={BIZTREE_ON_MISMATCH}`), independent of
        // shell env propagation (which docker compose ${VAR} relies on, set inline below).
        for (java.util.Map.Entry<String, String> e : env.entrySet()) {
            command = command.replace("{" + e.getKey() + "}", e.getValue());
        }
        boolean ok;
        try {
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            // Set env INLINE in the shell command (Windows `set K=V&&`, Unix `K=V ` prefix) so it
            // reliably reaches docker compose for ${VAR} substitution -- pb.environment() alone did
            // not propagate through cmd /c on this setup.
            StringBuilder full = new StringBuilder();
            for (java.util.Map.Entry<String, String> e : env.entrySet()) {
                full.append(windows ? "set " + e.getKey() + "=" + e.getValue() + "&&"
                                     : e.getKey() + "=" + e.getValue() + " ");
            }
            full.append(command);
            ProcessBuilder pb = windows
                    ? new ProcessBuilder("cmd", "/c", full.toString())
                    : new ProcessBuilder("sh", "-c", full.toString());
            pb.environment().putAll(env);   // belt-and-suspenders
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String out = new String(proc.getInputStream().readAllBytes()).trim();
            boolean exited = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int code = exited ? proc.exitValue() : -1;
            if (!exited) {
                proc.destroyForcibly();
            }
            ok = exited && code == 0;
            System.err.println("[cmd " + key + "] exit=" + code + " :: " + command
                    + (out.isEmpty() ? "" : " :: " + out));
        } catch (Exception e) {
            ok = false;
            System.err.println("[cmd " + key + "] FAILED :: " + command + " :: " + e);
        }
        return ok;
    }
}
