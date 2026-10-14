/*
 *  Armoury
 *  O11y -- the metering and tracing facades
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/07/2026 mir0n  created: the programmatic half of the trace-mark facility (v1.2.11 O2).
 *                   around(name, label, () -> ...) / aroundChecked() wrap a processing step in an
 *                   Observation the tracing handler renders as a span nested in the request trace. The
 *                   twin of the tracing annotation, for code Spring AOP cannot proxy (non-bean or final
 *                   classes) -- the keep writer's apply, and the account transaction / transfer
 *                   processors. Both entry points share the ObservationRegistry handed in at startup;
 *                   when tracing is off the registry is NOOP, so the action runs with zero span overhead.
 * 07/09/2026 mir0n  contextualName(label): the span name no longer carries the instance id
 * 07/17/2026 mir0n  note at the switch: observe() records the WHOLE Throwable hierarchy incl. Error -- the I33
 *                   "misses Error" review claim, checked live, is FALSE.
 * 10/13/2026 mir0n  v2.0.0 -- moved into Armoury as pro.mir0n.o11y.TraceMark (was EsqTraceMark in the Esquire
 *                   common library). The code is unchanged; it only ever imported Micrometer and the JDK, which
 *                   is what let it move below both products that use it. Ships in armoury-utils for now; the package is its own,
 *                   so a jar of its own later is a pom and nothing else
 */

package pro.mir0n.o11y;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.function.Supplier;

// The single, uniform trace mark -- the programmatic twin of a tracing annotation, for the code an
// annotation cannot reach: a class the container never proxies, or a final one.
public final class TraceMark {

    private TraceMark() {}

    private static volatile ObservationRegistry registry = ObservationRegistry.NOOP;

    // Wire the app's ObservationRegistry (the one carrying the tracing handlers). Called once at startup by
    // the application's observability configuration; until then, and while tracing is off, it stays NOOP.
    public static void setRegistry(ObservationRegistry observationRegistry) {
        registry = observationRegistry;
    }

    // Mark a value-returning processing step as a span. name = observation/metric name (low cardinality);
    // label = the span name shown in the trace. WHICH replica acted belongs on the span's service badge, not
    // in the label, so the label is plain.
    // (observe() records the WHOLE Throwable hierarchy on the span, incl. Error -- verified by test; the
    // o11y-review "misses Error" claim, I33, was checked live and is FALSE.)
    public static <T> T around(String name, String label, Supplier<T> action) {
        return Observation.createNotStarted(name, registry).contextualName(label).observe(action);
    }

    // Mark a void processing step as a span.
    public static void around(String name, String label, Runnable action) {
        Observation.createNotStarted(name, registry).contextualName(label).observe(action);
    }

    // Mark a processing step that throws a checked exception (e.g. a bus handler's handle()).
    public static <T, E extends Throwable> T aroundChecked(
            String name, String label, Observation.CheckedCallable<T, E> action) throws E {
        return Observation.createNotStarted(name, registry).contextualName(label).observeChecked(action);
    }
}
