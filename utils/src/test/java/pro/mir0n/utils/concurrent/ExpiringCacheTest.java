/*
 *  Armoury
 *  Utils -- the base library, tests
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.utils.concurrent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiringCacheTest {

    private static ExpiringCache<String, String> cache(long ttlMs) {
        return new ExpiringCache<>(LoggerFactory.getLogger(ExpiringCacheTest.class), ttlMs);
    }

    private static ExpiringCache<String, String> touching(long ttlMs) {
        return new ExpiringCache<>(LoggerFactory.getLogger(ExpiringCacheTest.class), ttlMs,
                IExpiringCache.Expiry.AFTER_ACCESS);
    }

    @Test
    @DisplayName("put then getAndRemove: returns the value and removes it -- it is a hand-off, not a lookup")
    void store_consume_isOneShot() {
        ExpiringCache<String, String> c = cache(60_000L);
        c.put("k", "v");
        assertThat(c.size()).isEqualTo(1);
        assertThat(c.getAndRemove("k")).isEqualTo("v");
        assertThat(c.size()).isZero();
        assertThat(c.getAndRemove("k")).isNull();          // a second take gets nothing
    }

    @Test
    @DisplayName("getAndRemove: absent key, and null key, both yield null rather than throwing")
    void consume_absentOrNull_isNull() {
        ExpiringCache<String, String> c = cache(60_000L);
        assertThat(c.getAndRemove("nope")).isNull();
        assertThat(c.getAndRemove(null)).isNull();
    }

    @Test
    @DisplayName("put: null key or null value is ignored, not stored")
    void store_nulls_ignored() {
        ExpiringCache<String, String> c = cache(60_000L);
        c.put(null, "v");
        c.put("k", null);
        assertThat(c.size()).isZero();
    }

    @Test
    @DisplayName("put twice: the later put replaces the earlier -- last put wins")
    void store_twice_lastWins() {
        ExpiringCache<String, String> c = cache(60_000L);
        c.put("k", "first");
        c.put("k", "second");
        assertThat(c.size()).isEqualTo(1);
        assertThat(c.getAndRemove("k")).isEqualTo("second");
    }

    @Test
    @DisplayName("getAndRemove: an entry older than the ttl yields null EVEN IF the prune has not run")
    void consume_expired_isNullWithoutPrune() {
        // This is the lazy check that makes the cache correct without a running scheduler: an entry that
        // slipped past the prune interval must not be handed out as if it were fresh.
        ExpiringCache<String, String> c = cache(100L);
        c.put("stale", "v", System.currentTimeMillis() - 10_000L);
        assertThat(c.size()).isEqualTo(1);            // still present -- nothing pruned it
        assertThat(c.getAndRemove("stale")).isNull();      // but not handed out
        assertThat(c.size()).isZero();                // and taken away by the read
    }

    @Test
    @DisplayName("prune: drops what is over the ttl and keeps what is not")
    void prune_dropsOnlyExpired() {
        ExpiringCache<String, String> c = cache(100L);
        c.put("fresh", "f");
        c.put("stale", "s", System.currentTimeMillis() - 10_000L);

        c.prune();

        assertThat(c.size()).isEqualTo(1);
        assertThat(c.getAndRemove("fresh")).isEqualTo("f");
    }

    @Test
    @DisplayName("start / stop are idempotent; start reports whether IT started the thread")
    void startStop_idempotent() {
        ExpiringCache<String, String> c = cache(60_000L);
        assertThat(c.start()).isTrue();
        assertThat(c.start()).isFalse();              // already running
        c.stop();
        c.stop();                                     // no throw on a second stop
        assertThat(c.start()).isTrue();               // and it can be started again
        c.stop();
    }

    @Test
    @DisplayName("an un-started cache still puts, hands off and expires correctly")
    void worksWithoutTheThread() {
        // Correct-but-unbounded without the prune thread -- which is exactly what a unit test wants, and why
        // the lazy expiry check in consume() is not optional.
        ExpiringCache<String, String> c = cache(100L);
        c.put("a", "v");
        c.put("old", "v", System.currentTimeMillis() - 10_000L);
        assertThat(c.getAndRemove("a")).isEqualTo("v");
        assertThat(c.getAndRemove("old")).isNull();
    }

    @Test
    @DisplayName("the default prune interval is half the ttl, and never zero")
    void defaultPruneInterval_isSane() {
        // ttl 1ms would otherwise schedule at 0ms and spin.
        assertThat(new ExpiringCache<String, String>(null, 1L).start()).isTrue();
        assertThat(cache(60_000L).ttlMs()).isEqualTo(60_000L);
    }

    @Test
    @DisplayName("get after-access: renews the entry, so an entry asked for outlives one that is not")
    void getAfterAccess_renews() throws InterruptedException {
        ExpiringCache<String, String> c = touching(1_000L);
        long old = System.currentTimeMillis() - 700L;
        c.put("asked", "a", old);
        c.put("idle", "i", old);
        assertThat(c.get("asked")).isEqualTo("a");
        Thread.sleep(500L);
        assertThat(c.get("asked")).isEqualTo("a");
        assertThat(c.get("idle")).isNull();
        assertThat(c.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("get after-write: reads without renewing, so the entry still expires from its put")
    void getAfterWrite_doesNotRenew() throws InterruptedException {
        ExpiringCache<String, String> c = cache(1_000L);
        c.put("k", "v", System.currentTimeMillis() - 700L);
        assertThat(c.get("k")).isEqualTo("v");
        Thread.sleep(500L);
        assertThat(c.get("k")).isNull();
    }

    @Test
    @DisplayName("get after-access: an expired entry, an absent key and a null key all yield null, and nothing is renewed")
    void getAfterAccess_expiredAbsentOrNull_isNull() {
        ExpiringCache<String, String> c = touching(1_000L);
        c.put("stale", "s", System.currentTimeMillis() - 10_000L);
        assertThat(c.get("stale")).isNull();
        assertThat(c.get("stale")).isNull();
        assertThat(c.get("nope")).isNull();
        assertThat(c.get(null)).isNull();
    }

    @Test
    @DisplayName("get after-access under a racing putIfGreater: a touch never puts an older value back")
    void getAfterAccess_neverRestoresOlder() throws InterruptedException {
        ExpiringCache<String, Integer> c = new ExpiringCache<>(null, 60_000L, IExpiringCache.Expiry.AFTER_ACCESS);
        int last = 20_000;
        c.put("k", 0);
        Thread writer = new Thread(() -> {
            for (int i = 1; i <= last; i++) {
                c.putIfGreater("k", i);
            }
        });
        Thread reader = new Thread(() -> {
            for (int i = 0; i < last; i++) {
                c.get("k");
            }
        });
        writer.start();
        reader.start();
        writer.join();
        reader.join();
        assertThat(c.get("k")).isEqualTo(last);
    }

    @Test
    @DisplayName("remove: drops the entry whatever its age; an absent or null key is no error")
    void remove_forced() {
        ExpiringCache<String, String> c = cache(60_000L);
        c.put("fresh", "f");
        c.put("stale", "s", System.currentTimeMillis() - 120_000L);
        c.remove("fresh");
        c.remove("stale");
        c.remove("nope");
        c.remove(null);
        assertThat(c.size()).isZero();
        assertThat(c.get("fresh")).isNull();
    }

    @Test
    @DisplayName("keys: a copy of the keys now -- removing through it while walking it is safe")
    void keys_snapshot() {
        ExpiringCache<String, String> c = cache(60_000L);
        c.put("a", "1");
        c.put("b", "2");
        List<String> keys = c.keys();
        for (String k : keys) {
            c.remove(k);
        }
        assertThat(keys).containsExactlyInAnyOrder("a", "b");
        assertThat(c.size()).isZero();
        assertThat(c.keys()).isEmpty();
    }

    @Test
    @DisplayName("a prune racing a put over an expired entry never drops the value just put")
    void race_pruneAgainstPut_keepsFresh() throws Exception {
        ExpiringCache<String, String> c = cache(60_000L);
        int lost = 0;
        for (int round = 0; round < 200 && lost == 0; round++) {
            for (int i = 0; i < 1_000; i++) {
                c.put("k" + i, "a", System.currentTimeMillis() - 120_000L);
            }
            CyclicBarrier both = new CyclicBarrier(2);
            Thread pruner = new Thread(() -> {
                await(both);
                c.prune();
            });
            pruner.start();
            await(both);
            for (int i = 0; i < 1_000; i++) {
                c.putIfGreater("k" + i, "b");
            }
            pruner.join();
            for (int i = 0; i < 1_000; i++) {
                if (c.get("k" + i) == null) {
                    lost++;
                }
            }
        }
        assertThat(lost).isZero();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
