/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: thread-safe access-token holder; lazy first fetch via KcTokenClient; auto-refresh at TTL-30s
 */
package pro.mir0n.hauberk.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe holder for a KC access token that auto-refreshes when the
 * cached token's expiry is approaching. Stops long Simulations from falling
 * off the 5-minute TTL cliff that {@link KcTokenClient}'s no-cache design
 * has historically been vulnerable to.
 *
 * Usage from a Simulation:
 *
 *   private static final RefreshableToken TOKEN = RefreshableToken.start();
 *
 *   HttpProtocolBuilder httpProtocol = http
 *       .baseUrl(...)
 *       .header("Authorization", session -&gt; "Bearer " + TOKEN.value());
 *
 * Gatling re-evaluates the header lambda per request, so {@link #value()}
 * returns either the cached token or a freshly-refreshed one transparently.
 */
public final class RefreshableToken {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Refresh when less than this many seconds remain on the token. */
    private static final long REFRESH_LEAD_SECONDS = 30L;

    private final AtomicReference<Cached> cached = new AtomicReference<>();

    private RefreshableToken() {}

    /**
     * Factory: build a holder with no eager fetch. The first call to
     * {@link #value()} -- which happens from Gatling's per-request header
     * lambda at run-time -- performs the actual KC token request. This keeps
     * {@code new SimulationXxx()} side-effect-free, so {@code hauberk list}
     * can instantiate Simulations to read their {@code description()}
     * without forcing 16 KC handshakes (and without failing if KC is down).
     */
    public static RefreshableToken lazy() {
        return new RefreshableToken();
    }

    /**
     * Factory: build + warm. Same as {@link #lazy()} followed by an
     * immediate {@link #value()} call to populate the cache. Use when the
     * caller wants the first KC handshake to happen up front rather than
     * inside the first request.
     */
    public static RefreshableToken start() {
        RefreshableToken ret = new RefreshableToken();
        ret.value(); // prime the cache
        return ret;
    }

    /** Returns a valid access token, refreshing transparently as needed. */
    public String value() {
        Cached now = cached.get();
        long nowSec = System.currentTimeMillis() / 1000L;
        if (now == null || now.expSec - nowSec < REFRESH_LEAD_SECONDS) {
            String fresh = KcTokenClient.fetchAccessToken();
            cached.set(new Cached(fresh, decodeExp(fresh)));
            now = cached.get();
        }
        return now.token;
    }

    /** Decode the JWT payload's `exp` claim (epoch seconds). Returns -1 on failure. */
    private static long decodeExp(String jws) {
        long ret = -1;
        try {
            int firstDot = jws.indexOf('.');
            int secondDot = jws.indexOf('.', firstDot + 1);
            String payloadB64 = jws.substring(firstDot + 1, secondDot);
            byte[] payload = Base64.getUrlDecoder().decode(padBase64(payloadB64));
            JsonNode body = MAPPER.readTree(new String(payload, StandardCharsets.UTF_8));
            JsonNode exp = body.path("exp");
            if (exp.isNumber()) {
                ret = exp.asLong();
            }
        } catch (Throwable ignore) {
            // fall back to "no expiry known" -- caller will refresh more eagerly
        }
        return ret;
    }

    private static String padBase64(String s) {
        String ret = s;
        int pad = 4 - (ret.length() % 4);
        if (pad != 4) {
            ret = ret + "====".substring(0, pad);
        }
        return ret;
    }

    private record Cached(String token, long expSec) {}
}
