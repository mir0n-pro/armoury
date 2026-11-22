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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyedExpiringCacheTest {

    record Claims(String uid, long changeNo) implements Comparable<Claims> {
        @Override
        public int compareTo(Claims other) {
            return Long.compare(changeNo, other.changeNo);
        }
    }

    private static KeyedExpiringCache<String, String, Claims> byUid(long ttlMs) {
        return new KeyedExpiringCache<>(LoggerFactory.getLogger(KeyedExpiringCacheTest.class), ttlMs,
                IExpiringCache.Expiry.AFTER_ACCESS, Claims::uid);
    }

    private static Function<String, CompletableFuture<Claims>> answering(AtomicInteger calls, Claims answer) {
        return key -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(answer);
        };
    }

    @Test
    @DisplayName("get with a loader: a miss loads once and stores, the next get is a hit")
    void loader_missThenHit() {
        KeyedExpiringCache<String, String, Claims> c = byUid(60_000L);
        AtomicInteger calls = new AtomicInteger();
        assertThat(c.get("login", answering(calls, new Claims("5", 1))).join()).isEqualTo(new Claims("5", 1));
        assertThat(c.get("login", answering(calls, new Claims("5", 2))).join()).isEqualTo(new Claims("5", 1));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("get with a loader: an eviction of the value's extra key during the load refuses it -- loaded again")
    void loader_evictedWhileLoading_loadsAgain() {
        KeyedExpiringCache<String, String, Claims> c = byUid(60_000L);
        AtomicInteger calls = new AtomicInteger();
        Function<String, CompletableFuture<Claims>> loader = key -> {
            Claims ret = new Claims("5", 2);
            if (calls.incrementAndGet() == 1) {
                c.evict("5");
                ret = new Claims("5", 1);
            }
            return CompletableFuture.completedFuture(ret);
        };
        assertThat(c.get("login", loader).join()).isEqualTo(new Claims("5", 2));
        assertThat(calls.get()).isEqualTo(2);
        assertThat(c.get("login")).isEqualTo(new Claims("5", 2));
    }

    @Test
    @DisplayName("get with a loader: an eviction of another extra key during the load does not refuse it")
    void loader_otherEviction_stores() {
        KeyedExpiringCache<String, String, Claims> c = byUid(60_000L);
        AtomicInteger calls = new AtomicInteger();
        Function<String, CompletableFuture<Claims>> loader = key -> {
            calls.incrementAndGet();
            c.evict("7");
            return CompletableFuture.completedFuture(new Claims("5", 1));
        };
        assertThat(c.get("login", loader).join()).isEqualTo(new Claims("5", 1));
        assertThat(calls.get()).isEqualTo(1);
        assertThat(c.get("login")).isEqualTo(new Claims("5", 1));
    }

    @Test
    @DisplayName("get with a loader: a load slower than the ttl is not stored -- loaded again")
    void loader_slowerThanTtl_loadsAgain() {
        KeyedExpiringCache<String, String, Claims> c = byUid(50L);
        AtomicInteger calls = new AtomicInteger();
        Function<String, CompletableFuture<Claims>> loader = key -> {
            CompletableFuture<Claims> ret = CompletableFuture.completedFuture(new Claims("5", 2));
            if (calls.incrementAndGet() == 1) {
                ret = CompletableFuture.supplyAsync(() -> {
                    sleep(150L);
                    return new Claims("5", 1);
                });
            }
            return ret;
        };
        assertThat(c.get("login", loader).join()).isEqualTo(new Claims("5", 2));
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("get with a loader: a null answer is passed on and not stored; a failed load fails the get")
    void loader_nullOrFailed() {
        KeyedExpiringCache<String, String, Claims> c = byUid(60_000L);
        assertThat(c.get("login", key -> CompletableFuture.completedFuture(null)).join()).isNull();
        assertThat(c.size()).isZero();
        CompletableFuture<Claims> failed = c.get("login",
                key -> CompletableFuture.failedFuture(new IllegalStateException("down")));
        assertThatThrownBy(failed::join).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(c.size()).isZero();
    }

    @Test
    @DisplayName("evict: removes every entry with the extra key, under any cache key, and no other")
    void evict_byExtraKey_scans() {
        KeyedExpiringCache<String, String, Claims> c = byUid(60_000L);
        c.put("login-a", new Claims("5", 1));
        c.put("login-b", new Claims("5", 1));
        c.put("login-c", new Claims("7", 1));
        assertThat(c.evict("5")).isEqualTo(2);
        assertThat(c.keys()).containsExactly("login-c");
        assertThat(c.evict(null)).isZero();
    }

    @Test
    @DisplayName("a load racing an eviction: the value loaded before it is never left in the cache")
    void race_loadAgainstEviction_neverStale() throws Exception {
        KeyedExpiringCache<String, String, Claims> c = byUid(60_000L);
        Claims old = new Claims("5", 1);
        Claims fresh = new Claims("5", 2);
        AtomicBoolean stale = new AtomicBoolean(false);
        for (int i = 0; i < 20_000 && !stale.get(); i++) {
            CompletableFuture<Claims> pending = new CompletableFuture<>();
            AtomicInteger calls = new AtomicInteger();
            CompletableFuture<Claims> result = c.get("login", key -> {
                CompletableFuture<Claims> ret = CompletableFuture.completedFuture(fresh);
                if (calls.incrementAndGet() == 1) {
                    ret = pending;
                }
                return ret;
            });
            CyclicBarrier both = new CyclicBarrier(2);
            Thread evictor = new Thread(() -> {
                await(both);
                c.evict("5");
            });
            evictor.start();
            await(both);
            pending.complete(old);
            evictor.join();
            result.join();
            if (old.equals(c.get("login"))) {
                stale.set(true);
            }
            c.remove("login");
        }
        assertThat(stale.get()).isFalse();
    }

    @Test
    @DisplayName("a prune racing an eviction never drops its fresh record: the load before it is not stored")
    void race_pruneAgainstEviction_neverStale() throws Exception {
        KeyedExpiringCache<String, String, Claims> c = byUid(200L);
        int stale = 0;
        for (int round = 0; round < 15 && stale == 0; round++) {
            for (int i = 0; i < 500; i++) {
                c.evict(String.valueOf(i));
            }
            sleep(250L);
            List<CompletableFuture<Claims>> pending = new ArrayList<>();
            List<CompletableFuture<Claims>> results = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                CompletableFuture<Claims> answer = new CompletableFuture<>();
                pending.add(answer);
                AtomicInteger calls = new AtomicInteger();
                Claims fresh = new Claims(String.valueOf(i), 2);
                results.add(c.get("login" + i, key -> {
                    CompletableFuture<Claims> ret = CompletableFuture.completedFuture(fresh);
                    if (calls.incrementAndGet() == 1) {
                        ret = answer;
                    }
                    return ret;
                }));
            }
            CyclicBarrier both = new CyclicBarrier(2);
            Thread pruner = new Thread(() -> {
                await(both);
                c.prune();
            });
            pruner.start();
            await(both);
            for (int i = 0; i < 500; i++) {
                c.evict(String.valueOf(i));
            }
            pruner.join();
            for (int i = 0; i < 500; i++) {
                pending.get(i).complete(new Claims(String.valueOf(i), 1));
                results.get(i).join();
                Claims held = c.get("login" + i);
                if (held != null && held.changeNo() == 1) {
                    stale++;
                }
                c.remove("login" + i);
            }
        }
        assertThat(stale).isZero();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
