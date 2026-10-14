/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: plain-Java KC client_credentials grant against /token; returns raw access_token
 * 10/13/2026 mir0n  v2.0.0 -- moved into Armoury; reads the client and the token endpoint from AuthConfig
 */
package pro.mir0n.hauberk.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pro.mir0n.hauberk.config.AuthConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches an OAuth2 access token from Keycloak via the client_credentials grant.
 *
 * The returned token is a JWS signed by the configured realm and carries whatever
 * claims that realm puts on the configured client -- the harness does not read
 * them, it only presents the token.
 *
 * Used by Simulation `before()` hooks to obtain a Bearer for subsequent calls,
 * and by ad-hoc Java tooling that needs a token for inspection.
 *
 * No caching here -- callers fetch once per Simulation and stash in a Session
 * attribute / system property. Tokens TTL is 5 min by default; long Simulations
 * will need a refresh helper layered on top.
 */
public final class KcTokenClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)    // see HealthPreCheck note
            .connectTimeout(Duration.ofSeconds(10))  // -- ingress-nginx HTTP/2 negotiation
            .build();                                // -- mangles KC responses

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KcTokenClient() {}

    /** Fetch a fresh access token. Throws on any failure. */
    public static String fetchAccessToken() {
        String ret;
        try {
            String form = "grant_type=client_credentials"
                    + "&client_id="     + AuthConfig.CLIENT_ID
                    + "&client_secret=" + AuthConfig.CLIENT_SECRET;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(AuthConfig.tokenEndpoint()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException(
                        "KC " + AuthConfig.tokenEndpoint() + " returned "
                                + resp.statusCode() + ": " + resp.body());
            }
            JsonNode body = MAPPER.readTree(resp.body());
            ret = body.path("access_token").asText(null);
            if (ret == null || ret.isBlank()) {
                throw new IllegalStateException(
                        "KC token response missing access_token: " + resp.body());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("KC token fetch failed: " + e.getMessage(), e);
        }
        return ret;
    }
}
