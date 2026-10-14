/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: abstract base for every hauberk Simulation -- pulls up lazy RefreshableToken, instrumented httpProtocol with PerformanceMatrix, and after() perf-matrix flush
 * 05/17/2026 mir0n  Authorization header branches on HauberkConfig.AUTH_MODE -- Basic for Vanilla Token Relay, Bearer (KC JWT) otherwise
 * 07/02/2026 mir0n  every outbound request carries a fresh X-Request-ID (UUID) so hauberk writes satisfy the services' write-time X-Request-ID guard
 * 10/13/2026 mir0n  v2.0.0 -- moved into Armoury, renamed with the A prefix, and the target base URL
 *                   became a constructor argument instead of a read of the Esquire gw.base setting.
 *                   A consumer declares its own one-line subclass that passes its base URL, and its
 *                   Simulations extend that; nothing else about them changes.
 */
package pro.mir0n.hauberk.simulations;

import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import pro.mir0n.hauberk.auth.RefreshableToken;
import pro.mir0n.hauberk.config.AuthConfig;
import pro.mir0n.hauberk.perf.PerformanceMatrix;

import java.util.UUID;

import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Abstract base for every hauberk {@code Simulation}. Centralises the
 * boilerplate that would otherwise be duplicated in every subclass:
 *
 * <ul>
 *   <li>The {@code RefreshableToken} (lazy, so the first handshake with the
 *       identity provider fires on first request, not at construction).</li>
 *   <li>The {@code httpProtocol} builder with the edge credential header +
 *       {@code PerformanceMatrix.instrument(...)} so {@code --metrics}
 *       works uniformly.</li>
 *   <li>The {@code after()} hook that flushes the per-scenario perf
 *       matrix on sim end.</li>
 * </ul>
 *
 * The base URL arrives as a constructor argument, which is what keeps this
 * class free of any one target's settings. A consumer writes its own
 * one-liner over it:
 *
 * <pre>
 * public abstract class AEsqSimulation extends AHauberkSimulation {
 *     protected AEsqSimulation() { super(EsqHauberkConfig.GW_BASE); }
 * }
 * </pre>
 *
 * The catalog description is carried as a class-level annotation:
 * {@link SimulationInfo @SimulationInfo("...")}. {@code ListCommand}
 * reads it reflectively from the {@code Class} object -- not from an
 * instance -- because Gatling's {@code Simulation} constructor refuses
 * direct {@code new} calls outside the Gatling runner. Presence on every
 * concrete sim is enforced by a unit test rather than by the compiler.
 *
 * Each subclass shrinks to: the {@code @SimulationInfo} annotation, a
 * scenario, and the instance-initialiser {@code setUp(...).protocols(httpProtocol)}
 * block.
 */
public abstract class AHauberkSimulation extends Simulation {

    /** Lazy access token; the first handshake fires on first request, not on construction. */
    protected final RefreshableToken TOKEN = RefreshableToken.lazy();

    /**
     * Gatling HTTP protocol pre-wired with: the target base URL, JSON Accept,
     * the edge credential header (re-evaluated per request so token
     * auto-refresh works mid-run), and Performance Matrix capture. Subclasses
     * just pass this to {@code setUp(...).protocols(httpProtocol)}.
     */
    protected final HttpProtocolBuilder httpProtocol;

    /**
     * @param baseUrl root of the target under test -- every request in the
     *                Simulation is relative to it.
     */
    protected AHauberkSimulation(String baseUrl) {
        this.httpProtocol = PerformanceMatrix.instrument(
                getClass(),
                baseUrl,
                http.baseUrl(baseUrl)
                    .acceptHeader("application/json")
                    .header("Authorization", (Session __sess) -> edgeCredential())
                    // One client-generated X-Request-ID per outbound request. Services that
                    // guard a write on its presence reject the write without it; harmless on
                    // reads. Re-evaluated per request so every call carries a distinct id.
                    .header("X-Request-ID", (Session __sess) -> UUID.randomUUID().toString()));
    }

    /** Basic for a Vanilla Token Relay run, Bearer otherwise. */
    private String edgeCredential() {
        String ret;
        if (AuthConfig.isBasicAuth()) {
            ret = AuthConfig.basicAuthHeader();
        } else {
            ret = "Bearer " + TOKEN.value();
        }
        return ret;
    }

    @Override
    public void after() {
        PerformanceMatrix.printSummary(getClass().getSimpleName());
    }
}
