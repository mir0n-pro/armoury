/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/13/2026 mir0n  v2.0.0 -- created: the ${key} expansion (plain, nested, unknown reference, cycle),
 *                   the system-property override, the integer bounds, and the shipped generic defaults
 */
package pro.mir0n.hauberk.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The config layer carries one piece of real logic -- the ${key} expansion that
 * lets a target write a URL once -- plus the override order and the integer
 * bounds. Those are what this covers.
 *
 * Touching HauberkConfig loads hauberk.properties from the working directory,
 * which during the build is this project's root, so the last test also says
 * whether the file shipped beside this jar still parses.
 */
class HauberkConfigTest {

    @Test
    @DisplayName("expand: a value refers to another setting")
    void expandsOneReference() {
        Properties p = new Properties();
        p.setProperty("edge.base", "http://localhost:7070");
        p.setProperty("health.probe.edge", "${edge.base}/actuator/health");

        HauberkConfig.expand(p);

        assertThat(p.getProperty("health.probe.edge"))
                .isEqualTo("http://localhost:7070/actuator/health");
        assertThat(p.getProperty("edge.base")).isEqualTo("http://localhost:7070");
    }

    @Test
    @DisplayName("expand: two references in one value, and a reference to a reference")
    void expandsNested() {
        Properties p = new Properties();
        p.setProperty("host", "localhost");
        p.setProperty("kc.base", "http://${host}:8081/kc-auth");
        p.setProperty("kc.realm", "esquire");
        p.setProperty("probe", "${kc.base}/realms/${kc.realm}/.well-known/openid-configuration");

        HauberkConfig.expand(p);

        assertThat(p.getProperty("kc.base")).isEqualTo("http://localhost:8081/kc-auth");
        assertThat(p.getProperty("probe")).isEqualTo(
                "http://localhost:8081/kc-auth/realms/esquire/.well-known/openid-configuration");
    }

    @Test
    @DisplayName("expand: an unknown reference names the setting that holds it")
    void expandRejectsUnknownReference() {
        Properties p = new Properties();
        p.setProperty("health.probe.edge", "${edge.base}/actuator/health");

        assertThatThrownBy(() -> HauberkConfig.expand(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("health.probe.edge")
                .hasMessageContaining("edge.base");
    }

    @Test
    @DisplayName("expand: a cycle stops instead of running out of memory")
    void expandRejectsCycle() {
        Properties p = new Properties();
        p.setProperty("a", "${b}");
        p.setProperty("b", "${a}");

        assertThatThrownBy(() -> HauberkConfig.expand(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expanding");
    }

    @Test
    @DisplayName("a system property stands in for a setting, and require says which one is missing")
    void systemPropertyOverrides() {
        String key = "hauberk.test.only.setting";
        System.setProperty(key, "7070");
        try {
            assertThat(HauberkConfig.require(key)).isEqualTo("7070");
            assertThat(HauberkConfig.requireInt(key)).isEqualTo(7070);
            assertThat(HauberkConfig.optional(key, "fallback")).isEqualTo("7070");
        } finally {
            System.clearProperty(key);
        }
        assertThat(HauberkConfig.optional(key, "fallback")).isEqualTo("fallback");
        assertThatThrownBy(() -> HauberkConfig.require(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(key);
    }

    @Test
    @DisplayName("the integer bounds: not a number, and below the minimum")
    void integerBounds() {
        String key = "hauberk.test.only.number";
        System.setProperty(key, "not-a-number");
        try {
            assertThatThrownBy(() -> HauberkConfig.requireInt(key))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be an integer");

            System.setProperty(key, "0");
            assertThat(HauberkConfig.requireIntMin0(key)).isZero();
            assertThatThrownBy(() -> HauberkConfig.requireInt(key))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be >= 1");

            System.setProperty(key, "2");
            assertThatThrownBy(() -> HauberkConfig.requireIntMin(key, 3))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be >= 3");
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    @DisplayName("the shipped hauberk.properties is generic: no target, no capture headers")
    void shippedFileIsGeneric() {
        assertThat(HauberkConfig.SIMULATIONS_PACKAGE).isEqualTo("pro.mir0n.hauberk.simulations");
        assertThat(HauberkConfig.PERF_H_CAPTURE).isEqualTo("X-Capture-Metrics");
        assertThat(HauberkConfig.PERF_H_GW_OUTER).isEmpty();
        assertThat(HauberkConfig.PERF_H_SRV_INNER).isEmpty();
        assertThat(HauberkConfig.PERF_RUN_LABEL).isEmpty();

        List<String> probes = HauberkConfig.keysUnder("health.probe.");
        assertThat(probes).isEmpty();
    }
}
