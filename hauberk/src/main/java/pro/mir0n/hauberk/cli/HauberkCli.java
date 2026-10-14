/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: picocli @Command root entry point; dispatches to run / list / summary / diff / help subcommands
 * 07/02/2026 mir0n  registered the kc-reconcile subcommand (KcReconcileCommand)
 * 10/13/2026 mir0n  v2.0.0 -- moved into Armoury; kc-reconcile dropped with the rest of the Esquire
 *                   half, so this root carries the generic four. A consumer that adds subcommands of
 *                   its own declares its own root over the same classes
 */
package pro.mir0n.hauberk.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Top-level entrypoint for the hauberk harness.
 *
 * Run:
 *   java -jar target/hauberk.jar &lt;subcommand&gt;
 *
 * Subcommands:
 *   run   -- launch a Gatling Simulation
 *   list  -- list discovered Simulation classes
 *   diff  -- side-by-side compare of two perf-matrix CSV files
 *
 * Default with no args: print help.
 */
@Command(name = "hauberk",
        mixinStandardHelpOptions = true,
        version = "hauberk 2.0.0",
        description = "Hauberk -- Gatling stress / load test harness.",
        subcommands = {
                RunCommand.class,
                ListCommand.class,
                SummaryCommand.class,
                DiffCommand.class,
                CommandLine.HelpCommand.class
        })
public final class HauberkCli implements Runnable {

    @Override
    public void run() {
        // No subcommand given -> show help (picocli mixin).
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new HauberkCli()).execute(args);
        System.exit(exitCode);
    }
}
