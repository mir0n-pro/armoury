/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/13/2026 mir0n  v2.0.0 -- created: the OIDC half of the old HauberkConfig, lifted out whole so a
 *                   target with no identity provider never triggers the kc.* requirements. Keys are
 *                   unchanged (kc.base, kc.realm, kc.client.id, kc.client.secret, kc.admin.*,
 *                   tokenRelay.type), so an existing properties file reads as it did; the one change
 *                   is kc.admin.client.id, which no longer defaults to an Esquire client name.
 */
package pro.mir0n.hauberk.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * How the harness authenticates: the OIDC provider, the client it presents,
 * and which Token Relay pattern the run targets at the edge.
 *
 * Loaded on first touch. A target that serves the harness without an identity
 * provider never touches this class, and so is never asked for a kc.* setting.
 */
public final class AuthConfig {

    private AuthConfig() {}

    public static final String BASE;
    public static final String REALM;
    public static final String CLIENT_ID;
    public static final String CLIENT_SECRET;

    /** Master-realm bootstrap admin credentials. Used by simulations that manage users
     *  through the admin REST API. Optional -- only those simulations read them. */
    public static final String ADMIN_USER;
    public static final String ADMIN_PASSWORD;
    public static final String ADMIN_CLIENT_ID;
    public static final String ADMIN_SECRET;

    /**
     * Token Relay pattern this run targets at the edge. Names the end-to-end auth
     * flow semantically, not the byte-level transport:
     *   plain    (default) -- Plain JWT: the harness sends Bearer (the IdP's JWT);
     *                         the edge validates the JWS locally; no token relay
     *                         involved.
     *   vanilla            -- Vanilla Token Relay: the harness sends HTTP Basic
     *                         with client_id:client_secret; the edge runs the
     *                         client_credentials grant on the harness's behalf and
     *                         caches the result per client_id.
     *   phantom            -- Phantom Token Relay: the harness sends Bearer (the
     *                         IdP's JWT); the edge runs RFC 8693 token-exchange via
     *                         its dedicated exchange client and caches per
     *                         source-token jti.
     * Sourced from `tokenRelay.type`. Maps to a transport choice (Basic for vanilla,
     * Bearer otherwise) consumed through isBasicAuth().
     */
    public static final String TOKEN_RELAY_TYPE;

    /** Pre-computed Authorization header value for vanilla (Basic) mode
     *  (static because client_id + secret are config-time constants).
     *  null when TOKEN_RELAY_TYPE != "vanilla". */
    private static final String BASIC_AUTH_HEADER;

    static {
        BASE          = HauberkConfig.require("kc.base");
        REALM         = HauberkConfig.require("kc.realm");
        CLIENT_ID     = HauberkConfig.require("kc.client.id");
        CLIENT_SECRET = HauberkConfig.require("kc.client.secret");

        ADMIN_USER      = HauberkConfig.optional("kc.admin.user", "admin");
        ADMIN_PASSWORD  = HauberkConfig.optional("kc.admin.password", "q");
        ADMIN_CLIENT_ID = HauberkConfig.optional("kc.admin.client.id", "");
        ADMIN_SECRET    = adminSecretFromEnv();

        TOKEN_RELAY_TYPE = HauberkConfig.optionalLowercase("tokenRelay.type", "plain");
        if (!"plain".equals(TOKEN_RELAY_TYPE)
                && !"vanilla".equals(TOKEN_RELAY_TYPE)
                && !"phantom".equals(TOKEN_RELAY_TYPE)) {
            throw new IllegalStateException(
                "Setting 'tokenRelay.type' must be 'plain', 'vanilla', or 'phantom' (got: "
                + TOKEN_RELAY_TYPE + ")");
        }
        if ("vanilla".equals(TOKEN_RELAY_TYPE)) {
            String creds = CLIENT_ID + ":" + CLIENT_SECRET;
            BASIC_AUTH_HEADER = "Basic " + Base64.getEncoder().encodeToString(
                creds.getBytes(StandardCharsets.UTF_8));
        } else {
            BASIC_AUTH_HEADER = null;
        }
    }

    /** The client_credentials token endpoint of the configured realm. */
    public static String tokenEndpoint() {
        return BASE + "/realms/" + REALM + "/protocol/openid-connect/token";
    }

    /** True when tokenRelay.type = "vanilla" -- the harness presents
     *  HTTP Basic at the edge (the edge exchanges it for a JWT). */
    public static boolean isBasicAuth() {
        return "vanilla".equals(TOKEN_RELAY_TYPE);
    }

    /** Pre-computed "Basic base64(id:secret)" header value. Throws when
     *  the run is not a Vanilla Token Relay run -- callers should branch
     *  on {@link #isBasicAuth()} before reading this. */
    public static String basicAuthHeader() {
        if (BASIC_AUTH_HEADER == null) {
            throw new IllegalStateException(
                "basicAuthHeader() called but tokenRelay.type=" + TOKEN_RELAY_TYPE);
        }
        return BASIC_AUTH_HEADER;
    }

    private static String adminSecretFromEnv() {
        String ret = System.getenv("KC_ADMIN_SECRET");
        if (ret == null || ret.isBlank()) {
            ret = System.getenv("KCMASTER_ADMIN_SECRET");
        }
        if (ret == null) {
            ret = "";
        }
        return ret;
    }
}
