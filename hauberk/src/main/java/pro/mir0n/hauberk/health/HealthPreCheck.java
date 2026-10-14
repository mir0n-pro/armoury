/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: KC well-known + gateway health probes run before sim start; aborts on failure
 * 05/17/2026 mir0n  auth-path probe branches on HauberkConfig.AUTH_MODE -- presents Basic for Vanilla Token Relay, Bearer otherwise;
 * 10/13/2026 mir0n  v2.0.0 -- moved into Armoury and the probe LIST moved out of the code into the
 *                   properties file: health.probe.<name>=<url> for a plain probe, health.auth.probe=<url>
 *                   for the one that presents the edge credential. The two Esquire probes (KC
 *                   well-known, gateway /actuator/health) and the /esq-kinds auth probe are now three
 *                   lines of configuration, written with ${kc.base} / ${gw.base} so no URL is duplicated.
 */
package pro.mir0n.hauberk.health;

import pro.mir0n.hauberk.auth.KcTokenClient;
import pro.mir0n.hauberk.config.AuthConfig;
import pro.mir0n.hauberk.config.HauberkConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Probes the target before a simulation starts. Fails fast (and loud) if
 * anything is down -- saves the operator from chasing thousands of KO
 * requests that all trace back to "service X was offline".
 *
 * What gets probed is configuration, not code:
 * <pre>
 *   health.probe.kc=${kc.base}/realms/${kc.realm}/.well-known/openid-configuration
 *   health.probe.gw=${gw.base}/actuator/health
 *   health.auth.probe=${gw.base}/esq-kinds
 * </pre>
 * A {@code health.probe.*} entry passes on any status below 500 -- it asks
 * whether something is answering there at all. {@code health.auth.probe} is
 * the one probe that must return 200: it presents whichever edge credential
 * the run is configured for (Bearer for Plain JWT and Phantom Token Relay,
 * Basic for Vanilla Token Relay) against a protected URL, and so smoke-tests
 * the whole auth path at once.
 *
 * With nothing configured the pre-check passes and says so. Output is
 * summarised; a single line per probe with OK / FAIL.
 */
public final class HealthPreCheck {

    private static final String PROBE_PREFIX = "health.probe.";
    private static final String AUTH_PROBE_KEY = "health.auth.probe";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)   // ingress-nginx + HTTP/2 negotiation
            .connectTimeout(Duration.ofSeconds(5))  // mis-parses some KC responses as chunked
            .build();                               // when in fact they are Content-Length

    private HealthPreCheck() {}

    /**
     * Runs all configured probes and returns true if everything passed. On
     * failure, prints what is broken and returns false (caller decides to abort).
     */
    public static boolean run() {
        List<Probe> probes = new ArrayList<>();
        for (String key : HauberkConfig.keysUnder(PROBE_PREFIX)) {
            probes.add(new Probe(key.substring(PROBE_PREFIX.length()),
                                 HauberkConfig.require(key),
                                 null));
        }
        String authUrl = HauberkConfig.optional(AUTH_PROBE_KEY, "");

        boolean ret = true;
        System.out.println("[health] pre-check");
        if (probes.isEmpty() && authUrl.isEmpty()) {
            System.out.println("  no probes configured (" + PROBE_PREFIX + "* / "
                    + AUTH_PROBE_KEY + ") -- nothing checked");
        }
        for (Probe p : probes) {
            boolean ok = p.check();
            System.out.printf("  %-30s %s  (%s)%n", p.name, ok ? "OK" : "FAIL", p.url);
            if (!ok) {
                ret = false;
                if (p.lastError != null) {
                    System.out.println("      " + p.lastError);
                }
            }
        }
        if (!authUrl.isEmpty() && !authPath(authUrl)) {
            ret = false;
        }
        if (!ret) {
            System.err.println("[health] pre-check FAILED -- aborting before sim starts.");
        }
        return ret;
    }

    /**
     * Auth path smoke: present the edge credential this run is configured for and
     * hit a protected endpoint. Bearer (Plain JWT and Phantom Token Relay) fetches
     * a token from the IdP first; Basic (Vanilla Token Relay) presents
     * client_id:secret directly -- the edge does the handshake on the harness's
     * behalf.
     */
    private static boolean authPath(String url) {
        boolean ret;
        String label;
        if (AuthConfig.isBasicAuth()) {
            label = "auth path w/ basic";
        } else {
            label = "auth path w/ bearer";
        }
        try {
            String authHeader;
            if (AuthConfig.isBasicAuth()) {
                authHeader = AuthConfig.basicAuthHeader();
            } else {
                authHeader = "Bearer " + KcTokenClient.fetchAccessToken();
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", authHeader)
                    .GET().build();
            HttpResponse<Void> resp = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            ret = resp.statusCode() == 200;
            System.out.printf("  %-30s %s  (%s, HTTP %d)%n",
                    label, ret ? "OK" : "FAIL", url, resp.statusCode());
        } catch (Throwable t) {
            System.out.printf("  %-30s FAIL  (%s)%n", label, t.getMessage());
            ret = false;
        }
        return ret;
    }

    private static final class Probe {
        final String name;
        final String url;
        final String body;
        String lastError;

        Probe(String name, String url, String body) {
            this.name = name;
            this.url  = url;
            this.body = body;
        }

        boolean check() {
            boolean ret = false;
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5));
                if (body == null) b.GET();
                else              b.POST(HttpRequest.BodyPublishers.ofString(body));
                HttpResponse<Void> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.discarding());
                ret = resp.statusCode() < 500;
                if (!ret) {
                    lastError = "HTTP " + resp.statusCode();
                }
            } catch (Throwable t) {
                lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            return ret;
        }
    }
}
