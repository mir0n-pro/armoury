/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: picocli "list" subcommand; catalogue of discovered Simulations with @SimulationInfo descriptions
 */
package pro.mir0n.hauberk.cli;

import io.gatling.javaapi.core.Simulation;
import picocli.CommandLine.Command;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list",
        description = "List available Simulation classes (short alias + one-line description).")
public class ListCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        List<Class<? extends Simulation>> sims = SimulationCatalog.discover();
        if (sims.isEmpty()) {
            System.out.println("No Simulation classes discovered.");
        } else {
            int aliasWidth = 0;
            for (Class<? extends Simulation> sim : sims) {
                int w = toKebab(stripSuffix(sim.getSimpleName())).length();
                if (w > aliasWidth) aliasWidth = w;
            }
            System.out.println("Available Simulations:");
            for (Class<? extends Simulation> sim : sims) {
                String alias = toKebab(stripSuffix(sim.getSimpleName()));
                String desc  = description(sim);
                System.out.printf("  %-" + aliasWidth + "s  %s%n", alias, desc);
            }
        }
        return 0;
    }

    private static String stripSuffix(String simple) {
        return simple.replaceFirst("Simulation$", "");
    }

    private static String toKebab(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /**
     * Read the catalog description from the class-level
     * {@link SimulationInfo @SimulationInfo} annotation. Annotation read
     * works on the {@code Class} object alone -- no instance needed,
     * which matters because Gatling's {@code Simulation} constructor
     * refuses direct {@code new} calls outside the Gatling runner.
     * Returns "" if a sim is missing the annotation (a unit test
     * enforces presence on the build side).
     */
    private static String description(Class<? extends Simulation> cls) {
        String ret = "";
        SimulationInfo info = cls.getAnnotation(SimulationInfo.class);
        if (info != null) {
            ret = info.value();
        }
        return ret;
    }
}
