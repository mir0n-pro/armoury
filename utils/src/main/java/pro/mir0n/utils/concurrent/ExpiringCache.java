/*
 *  Armoury
 *  Utils -- the base library
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/10/2026 mir0n  created (v1.2.12): generic hand-off cache whose entries expire by age -- a
 *                   ConcurrentHashMap with a timestamp per entry, one daemon prune thread and a lazy age
 *                   check on read, so an un-started cache is still correct. store() parks a value,
 *                   consume() takes it away, storeIfGreater() parks only when the arrival compares greater
 *                   than what is there, in one atomic merge. The value type is Comparable, so the cache
 *                   needs no comparator and never learns what its callers order by.
 * 10/11/2026 mir0n  v1.2.16 -- peek(K): reads the value WITHOUT taking it away
 * 11/21/2026 mir0n  v2.0.1 -- implements IExpiringCache: store/storeIfGreater/consume/peek renamed
 *                   put/putIfGreater/getAndRemove/get; remove(K) and keys(); Expiry AFTER_WRITE (the
 *                   default) / AFTER_ACCESS chosen in two new constructors, get() renews the entry under
 *                   AFTER_ACCESS; prune() removes an expired entry only while it is still the one it read;
 *                   devLog and entries protected; method javadoc removed, a short class description
 */
package pro.mir0n.utils.concurrent;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An {@link IExpiringCache} on a concurrent map, with a timestamp per entry and one daemon prune thread.
 * Every read checks the age itself, so the prune only keeps the map small: a cache that was never started
 * is still correct, only unbounded.
 *
 * <p>The logger and the timings come in through the constructor. The prune runs at half the time to live
 * when no interval is given, and the expiry is {@link Expiry#AFTER_WRITE} when none is given.
 */
public class ExpiringCache<K, V extends Comparable<? super V>> implements IExpiringCache<K, V> {

    public record Timestamped<V>(V value, long ts) {}

    protected final Logger devLog;
    private final long   ttlMs;
    private final long   pruneIntervalMs;
    private final String threadName;
    private final Expiry expiry;

    protected final ConcurrentHashMap<K, Timestamped<V>> entries = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    public ExpiringCache(Logger devLog, long ttlMs) {
        this(devLog, ttlMs, Math.max(1L, ttlMs / 2L), Expiry.AFTER_WRITE);
    }

    public ExpiringCache(Logger devLog, long ttlMs, long pruneIntervalMs) {
        this(devLog, ttlMs, pruneIntervalMs, Expiry.AFTER_WRITE);
    }

    public ExpiringCache(Logger devLog, long ttlMs, Expiry expiry) {
        this(devLog, ttlMs, Math.max(1L, ttlMs / 2L), expiry);
    }

    public ExpiringCache(Logger devLog, long ttlMs, long pruneIntervalMs, Expiry expiry) {
        this.devLog          = devLog;
        this.ttlMs           = ttlMs;
        this.pruneIntervalMs = pruneIntervalMs;
        this.threadName      = threadNameFrom(devLog);
        this.expiry          = expiry;
    }

    @Override
    public synchronized boolean start() {
        boolean ret = false;
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, threadName);
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleWithFixedDelay(this::prune, pruneIntervalMs, pruneIntervalMs, TimeUnit.MILLISECONDS);
            ret = true;
        }
        return ret;
    }

    @Override
    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    @Override
    public void put(K key, V value) {
        put(key, value, System.currentTimeMillis());
    }

    //xxx: the test seam -- a test ages an entry exactly instead of sleeping
    void put(K key, V value, long ts) {
        if (key != null && value != null) {
            entries.put(key, new Timestamped<>(value, ts));
        }
    }

    @Override
    public boolean putIfGreater(K key, V value) {
        boolean ret = false;
        if (key != null && value != null) {
            long now = System.currentTimeMillis();
            Timestamped<V> incoming = new Timestamped<>(value, now);
            //xxx: one atomic merge -- read, compare and put back would let the smaller value win a race; an
            // expired entry counts as absent
            Timestamped<V> winner = entries.merge(key, incoming, (parked, fresh) ->
                    (now - parked.ts() > ttlMs) || fresh.value().compareTo(parked.value()) > 0
                            ? fresh
                            : parked);
            ret = winner == incoming;
        }
        return ret;
    }

    @Override
    public V getAndRemove(K key) {
        V ret = null;
        if (key != null) {
            Timestamped<V> e = entries.remove(key);
            if (e != null && System.currentTimeMillis() - e.ts() <= ttlMs) {
                ret = e.value();
            }
        }
        return ret;
    }

    @Override
    public V get(K key) {
        V ret;
        if (expiry == Expiry.AFTER_ACCESS) {
            ret = getAndTouch(key);
        } else {
            ret = getOnly(key);
        }
        return ret;
    }

    private V getOnly(K key) {
        V ret = null;
        if (key != null) {
            Timestamped<V> e = entries.get(key);
            if (e != null && System.currentTimeMillis() - e.ts() <= ttlMs) {
                ret = e.value();
            }
        }
        return ret;
    }

    private V getAndTouch(K key) {
        V ret = null;
        if (key != null) {
            boolean done = false;
            while (!done) {
                Timestamped<V> e = entries.get(key);
                long now = System.currentTimeMillis();
                //xxx: replace() renews only the entry read here; a store in between wins, and the loop reads again
                if (e == null || now - e.ts() > ttlMs) {
                    done = true;
                } else if (entries.replace(key, e, new Timestamped<>(e.value(), now))) {
                    ret = e.value();
                    done = true;
                }
            }
        }
        return ret;
    }

    @Override
    public void remove(K key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    //xxx: a copy taken now -- an entry stored or removed after it is not in the list
    @Override
    public List<K> keys() {
        return new ArrayList<>(entries.keySet());
    }

    @Override
    public int size() {
        return entries.size();
    }

    public long ttlMs() {
        return ttlMs;
    }

    public void prune() {
        long now = System.currentTimeMillis();
        int before = entries.size();
        Iterator<Map.Entry<K, Timestamped<V>>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, Timestamped<V>> e = it.next();
            //xxx: remove the entry read here only -- a put in between must survive; it.remove() drops the key
            // whatever it holds by then
            if (now - e.getValue().ts() > ttlMs) {
                entries.remove(e.getKey(), e.getValue());
            }
        }
        if (devLog != null && before != entries.size()) {
            devLog.debug("expiring-cache[{}]: pruned {} -> {}", threadName, before, entries.size());
        }
    }

    private static String threadNameFrom(Logger devLog) {
        String ret = "expiring-cache";
        if (devLog != null && devLog.getName() != null) {
            String n = devLog.getName();
            int dot = n.lastIndexOf('.');
            ret = (dot >= 0 && dot < n.length() - 1 ? n.substring(dot + 1) : n) + ".expiring-cache";
        }
        return ret;
    }
}
