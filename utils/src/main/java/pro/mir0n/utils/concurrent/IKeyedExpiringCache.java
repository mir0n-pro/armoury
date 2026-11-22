/*
 *  Armoury
 *  Utils -- the base library
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 11/21/2026 mir0n  created (v2.0.1): IExpiringCache plus a read-through get(K, loader) returning a
 *                   CompletableFuture, and evict(E) by an extra key the value carries
 */
package pro.mir0n.utils.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A read-through expiring cache whose entries can be evicted by an extra key, a field the value carries.
 * A load and an eviction can race; a value loaded before an eviction of its extra key is never stored.
 *
 * @param <K> the key
 * @param <E> the extra key a value carries
 * @param <V> the value
 */
public interface IKeyedExpiringCache<K, E, V extends Comparable<? super V>> extends IExpiringCache<K, V> {

    /**
     * Returns the cached value; on a miss, calls the loader and stores its answer. When an eviction of the
     * answer's extra key comes while it loads, the answer is not stored and the loader is called again.
     * The plain {@link #put} and {@link #putIfGreater} skip that check: a read-through user stores
     * through this method only.
     *
     * @return the value; null when the loader answers null, which is not stored
     */
    CompletableFuture<V> get(K key, Function<? super K, CompletableFuture<V>> loader);

    /**
     * Removes every entry whose value carries this extra key.
     *
     * @return how many entries were removed
     */
    int evict(E extraKey);
}
