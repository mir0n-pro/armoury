/*
 *  Armoury
 *  Utils -- the base library
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 11/21/2026 mir0n  created (v2.0.1): the expiring-cache contract -- get, put, putIfGreater, getAndRemove,
 *                   remove, keys, size, start, stop, and the Expiry enum AFTER_WRITE / AFTER_ACCESS
 */
package pro.mir0n.utils.concurrent;

import java.util.List;

/**
 * A cache whose entries expire by age. An entry older than the time to live is never returned, whether
 * or not the prune thread has run.
 *
 * @param <K> the key
 * @param <V> the value; its natural order is what {@link #putIfGreater} compares
 */
public interface IExpiringCache<K, V extends Comparable<? super V>> {

    /** How an entry's age is counted, chosen when the cache is built. */
    enum Expiry {
        /** From the put: an entry lives a fixed time, however often it is read. */
        AFTER_WRITE,
        /** From the last {@link #get}: an entry lives as long as it is asked for. */
        AFTER_ACCESS
    }

    /** @return the value, or null when there is none or it is older than the time to live */
    V get(K key);

    /** Puts the value; a later put for the same key replaces it. */
    void put(K key, V value);

    /**
     * Puts the value only when it is greater than the one there, in one atomic step. An expired entry
     * counts as absent.
     *
     * @return true when the value was stored
     */
    boolean putIfGreater(K key, V value);

    /**
     * Takes the value away.
     *
     * @return the value, or null when there is none or it is older than the time to live
     */
    V getAndRemove(K key);

    /** Removes the entry. */
    void remove(K key);

    /** @return a copy of the keys at this moment */
    List<K> keys();

    /** @return the number of entries, expired ones not yet pruned included */
    int size();

    /**
     * Starts the prune thread.
     *
     * @return true when this call started it, false when it was already running
     */
    boolean start();

    /** Stops the prune thread; a second call does nothing. */
    void stop();
}
