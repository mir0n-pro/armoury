/*
 *  Armoury
 *  Utils -- the base library
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 11/21/2026 mir0n  created (v2.0.1): IKeyedExpiringCache on ExpiringCache. A load takes a ticket (the
 *                   eviction count and the time); its answer is stored only when no eviction of its extra
 *                   key came after the ticket, the check and the put under the lock evict(E) takes;
 *                   eviction records kept one TTL and pruned only while still the one read; a ticket
 *                   older than the TTL never stores, and a refused answer is loaded again
 */
package pro.mir0n.utils.concurrent;

import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * An {@link IKeyedExpiringCache} on top of {@link ExpiringCache}. The extra key is read from the value by
 * the function given to the constructor.
 *
 * <p>A load takes a ticket first: the number of evictions so far, and the time. The answer is stored only
 * when no eviction of its extra key came after the ticket, and the check and the store run under the same
 * lock as {@link #evict}. Each eviction is recorded for one time to live, and a ticket older than that never
 * stores. The price is one lock on every stored load and every eviction, and a scan of the cache on every
 * eviction.
 */
public class KeyedExpiringCache<K, E, V extends Comparable<? super V>> extends ExpiringCache<K, V>
        implements IKeyedExpiringCache<K, E, V> {

    private record Ticket(long evictions, long ts) {}

    private final Function<? super V, ? extends E> extraKeyOf;
    //xxx: per extra key, the number of its last eviction; kept for one TTL -- a load older than that is never
    // stored, so no record it could need is gone
    private final ConcurrentHashMap<E, Timestamped<Long>> evictionRecords = new ConcurrentHashMap<>();
    private final Object evictionLock = new Object();
    private long evictions;

    public KeyedExpiringCache(Logger devLog, long ttlMs, Expiry expiry, Function<? super V, ? extends E> extraKeyOf) {
        this(devLog, ttlMs, Math.max(1L, ttlMs / 2L), expiry, extraKeyOf);
    }

    public KeyedExpiringCache(Logger devLog, long ttlMs, long pruneIntervalMs, Expiry expiry,
                              Function<? super V, ? extends E> extraKeyOf) {
        super(devLog, ttlMs, pruneIntervalMs, expiry);
        this.extraKeyOf = extraKeyOf;
    }

    @Override
    public CompletableFuture<V> get(K key, Function<? super K, CompletableFuture<V>> loader) {
        CompletableFuture<V> ret;
        V hit = get(key);
        if (hit != null) {
            ret = CompletableFuture.completedFuture(hit);
        } else {
            ret = load(key, loader);
        }
        return ret;
    }

    private CompletableFuture<V> load(K key, Function<? super K, CompletableFuture<V>> loader) {
        Ticket ticket = ticket();
        return loader.apply(key).thenCompose(loaded -> settle(key, loader, ticket, loaded));
    }

    //xxx: a value loaded before an eviction of its extra key may be the one the eviction removed: it is not
    // stored, and the loader is asked once more
    private CompletableFuture<V> settle(K key, Function<? super K, CompletableFuture<V>> loader, Ticket ticket,
                                        V loaded) {
        CompletableFuture<V> ret;
        if (loaded == null || putIfNotEvicted(key, loaded, ticket)) {
            ret = CompletableFuture.completedFuture(loaded);
        } else {
            if (devLog != null) {
                devLog.debug("keyed-expiring-cache: [{}] evicted while loading -- loading again", key);
            }
            ret = load(key, loader);
        }
        return ret;
    }

    private Ticket ticket() {
        synchronized (evictionLock) {
            return new Ticket(evictions, System.currentTimeMillis());
        }
    }

    //xxx: the checks and the put run under the lock evict() takes, so an eviction lands before the check or
    // after the put, never in between; the age is read under the lock too, or a pause after it could outlast
    // the record the check needs
    private boolean putIfNotEvicted(K key, V value, Ticket ticket) {
        boolean ret = false;
        synchronized (evictionLock) {
            if (System.currentTimeMillis() - ticket.ts() <= ttlMs()) {
                Timestamped<Long> record = evictionRecords.get(extraKeyOf.apply(value));
                if (record == null || record.value() <= ticket.evictions()) {
                    putIfGreater(key, value);
                    ret = true;
                }
            }
        }
        return ret;
    }

    @Override
    public int evict(E extraKey) {
        int ret = 0;
        if (extraKey != null) {
            synchronized (evictionLock) {
                evictions++;
                evictionRecords.put(extraKey, new Timestamped<>(evictions, System.currentTimeMillis()));
                Iterator<Map.Entry<K, Timestamped<V>>> it = entries.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<K, Timestamped<V>> e = it.next();
                    if (extraKey.equals(extraKeyOf.apply(e.getValue().value()))) {
                        it.remove();
                        ret++;
                    }
                }
            }
        }
        return ret;
    }

    @Override
    public void prune() {
        super.prune();
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<E, Timestamped<Long>>> records = evictionRecords.entrySet().iterator();
        while (records.hasNext()) {
            Map.Entry<E, Timestamped<Long>> r = records.next();
            //xxx: remove the record read here only -- a fresh eviction in between must keep its record
            if (now - r.getValue().ts() > ttlMs()) {
                evictionRecords.remove(r.getKey(), r.getValue());
            }
        }
    }
}
