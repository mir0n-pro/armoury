/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: classpath scanner for concrete Simulation classes under simulations package; resolves short / kebab / FQCN names
 * 10/13/2026 mir0n  v2.0.0 -- moved into Armoury; the package to scan comes from the simulations.package
 *                   setting, so a consumer keeps its Simulations in its own jar
 */
package pro.mir0n.hauberk.cli;

import io.gatling.javaapi.core.Simulation;

import pro.mir0n.hauberk.config.HauberkConfig;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Discovers all concrete Simulation classes under the configured
 * {@code simulations.package}. Used by both `hauberk list` (output the
 * catalog) and `hauberk run` (resolve a short name like "entity-smoke"
 * to a full class).
 *
 * Discovery walks the classpath -- handles both the development tree
 * (target/classes) and the shaded fat jar (META-INF entries).
 */
public final class SimulationCatalog {

    private static final String SIM_PACKAGE = HauberkConfig.SIMULATIONS_PACKAGE;

    private SimulationCatalog() {}

    /** Returns the discovered Simulation classes, sorted by simple name. */
    public static List<Class<? extends Simulation>> discover() {
        List<Class<? extends Simulation>> ret = new ArrayList<>();
        String pkgPath = SIM_PACKAGE.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            java.util.Enumeration<URL> resources = cl.getResources(pkgPath);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    collectFromDir(new File(URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8)), SIM_PACKAGE, ret);
                } else if ("jar".equals(protocol)) {
                    collectFromJar(url, pkgPath, ret);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot enumerate simulations package: " + ex.getMessage(), ex);
        }
        ret.sort((a, b) -> a.getSimpleName().compareToIgnoreCase(b.getSimpleName()));
        return Collections.unmodifiableList(ret);
    }

    /**
     * Resolves a user-supplied sim name to a class. Accepts:
     *   - full FQCN: com.example.simulations.EntitySmokeSimulation
     *   - simple name: EntitySmokeSimulation
     *   - short form (Simulation suffix dropped): EntitySmoke
     *   - kebab-case short form: entity-smoke
     *   - case-insensitive
     */
    public static Class<? extends Simulation> resolve(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Simulation name is required.");
        }
        Class<? extends Simulation> ret = null;
        String target = name.replace("-", "").toLowerCase();
        for (Class<? extends Simulation> sim : discover()) {
            String simple   = sim.getSimpleName();                                // e.g. EntitySmokeSimulation
            String shortish = simple.replaceFirst("Simulation$", "");             // EntitySmoke
            String simpleLc = simple.toLowerCase();
            String shortLc  = shortish.toLowerCase();
            if (sim.getName().equalsIgnoreCase(name)
                    || simpleLc.equals(name.toLowerCase())
                    || shortLc.equals(name.toLowerCase())
                    || simpleLc.equals(target)
                    || shortLc.equals(target)
                    || (shortLc + "simulation").equals(target)) {
                ret = sim;
                break;
            }
        }
        if (ret == null) {
            StringBuilder msg = new StringBuilder("Unknown simulation: '").append(name).append("'. Available:");
            for (Class<? extends Simulation> sim : discover()) {
                msg.append("\n  ").append(sim.getSimpleName());
            }
            throw new IllegalArgumentException(msg.toString());
        }
        return ret;
    }

    private static void collectFromDir(File dir, String pkg, List<Class<? extends Simulation>> out) {
        if (!dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            String fname = f.getName();
            if (f.isDirectory()) {
                collectFromDir(f, pkg + "." + fname, out);
            } else if (fname.endsWith("Simulation.class")) {
                String fqcn = pkg + "." + fname.substring(0, fname.length() - ".class".length());
                tryAdd(fqcn, out);
            }
        }
    }

    private static void collectFromJar(URL jarUrl, String pkgPath, List<Class<? extends Simulation>> out) {
        try {
            String file = jarUrl.getFile();
            int bang = file.indexOf('!');
            String jarPath = bang > 0 ? file.substring(0, bang) : file;
            if (jarPath.startsWith("file:")) {
                jarPath = jarPath.substring("file:".length());
            }
            jarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);
            try (JarFile jar = new JarFile(jarPath)) {
                java.util.Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith(pkgPath) && name.endsWith("Simulation.class") && !entry.isDirectory()) {
                        String fqcn = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                        tryAdd(fqcn, out);
                    }
                }
            }
        } catch (Exception ignore) {
            // best-effort
        }
    }

    @SuppressWarnings("unchecked")
    private static void tryAdd(String fqcn, List<Class<? extends Simulation>> out) {
        try {
            Class<?> cls = Class.forName(fqcn, false, Thread.currentThread().getContextClassLoader());
            if (Simulation.class.isAssignableFrom(cls)
                    && !java.lang.reflect.Modifier.isAbstract(cls.getModifiers())) {
                out.add((Class<? extends Simulation>) cls);
            }
        } catch (Throwable ignore) {
            // skip unloadable classes
        }
    }
}
