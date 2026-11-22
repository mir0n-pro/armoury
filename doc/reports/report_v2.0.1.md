# Release Report: v2.0.0 → HEAD

**Repo:** `armoury/develop`  
**Top commit:** `ff24613`

---

## Release Notes

### doc/release_notes.txt


**v2.0.1-2611.2212**  v2.0.1 -- one cache interface, and a read-through cache an eviction cannot overtake  
&nbsp;: Feature:     IExpiringCache, the one interface of the expiring cache, with the usual cache names;  
&nbsp;                 an entry's age counts from the put or from the last read  
&nbsp;: Feature:     KeyedExpiringCache: a read-through cache evicted by a second key; a value loaded  
&nbsp;                 before an eviction is never kept  
&nbsp;: Refactoring: ExpiringCache methods renamed: store -> put, storeIfGreater -> putIfGreater,  
&nbsp;                 consume -> getAndRemove, peek -> get  
&nbsp;: Fix:         the cache clean-up no longer removes a value put while it runs  
&nbsp;: Doc:         README.md  
&nbsp;                 doc\Armoury.Utils.md  
&nbsp;                 doc\Armoury.ContinuousDev.md (new)  
&nbsp;                 doc\logo\utils.svg  
&nbsp;                 doc\logo\taijitu.svg  
&nbsp;   Components:   utils  

---

## Code Changes

### utils/src/main/java/pro/mir0n/utils/changes.txt


**11/22/2026** mir0n  v2.0.1 -- one cache interface, and a read-through cache an eviction cannot overtake  
concurrent.IExpiringCache  (new)  
&nbsp;- created (v2.0.1): the expiring-cache contract -- get, put, putIfGreater, getAndRemove, remove, keys,  
&nbsp;   size, start, stop, and the Expiry enum AFTER_WRITE / AFTER_ACCESS  
concurrent.IKeyedExpiringCache  (new)  
&nbsp;- created (v2.0.1): IExpiringCache plus a read-through get(K, loader) returning a CompletableFuture, and  
&nbsp;   evict(E) by an extra key the value carries  
concurrent.KeyedExpiringCache  (new)  
&nbsp;- created (v2.0.1): IKeyedExpiringCache on ExpiringCache. A load takes a ticket (the eviction count and  
&nbsp;   the time); its answer is stored only when no eviction of its extra key came after the ticket, the check  
&nbsp;   and the put under the lock evict(E) takes; eviction records kept one TTL and pruned only while still  
&nbsp;   the one read; a ticket older than the TTL never stores, and a refused answer is loaded again  
**concurrent.ExpiringCache**  
&nbsp;- v2.0.1 -- implements IExpiringCache: store/storeIfGreater/consume/peek renamed  
&nbsp;   put/putIfGreater/getAndRemove/get; remove(K) and keys(); Expiry AFTER_WRITE (the default) /  
&nbsp;   AFTER_ACCESS chosen in two new constructors, get() renews the entry under AFTER_ACCESS; prune() removes  
&nbsp;   an expired entry only while it is still the one it read; devLog and entries protected; method javadoc  
&nbsp;   removed, a short class description  

---

## Commits

```

-- 2026-11-22 | commit: ff24613 | mir0n.the.programmer | poms CI friendly --
M	hauberk/pom.xml
M	pom.xml
M	taijitu/pom.xml
M	utils/pom.xml
 4 files changed, 39 insertions(+), 12 deletions(-)


-- 2026-11-22 | commit: 37c2d79 | mir0n.the.programmer | v2.0.1 ExpiringCache KeyedExpiringCache refactoring --
M	README.md
A	doc/Armoury.ContinuousDev.md
M	doc/Armoury.Utils.md
M	doc/logo/taijitu.svg
M	doc/logo/utils.svg
M	doc/release_notes.txt
M	pom.xml
M	utils/src/main/java/pro/mir0n/utils/changes.txt
M	utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
A	utils/src/main/java/pro/mir0n/utils/concurrent/IExpiringCache.java
A	utils/src/main/java/pro/mir0n/utils/concurrent/IKeyedExpiringCache.java
A	utils/src/main/java/pro/mir0n/utils/concurrent/KeyedExpiringCache.java
M	utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheStoreIfGreaterTest.java
M	utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheTest.java
A	utils/src/test/java/pro/mir0n/utils/concurrent/KeyedExpiringCacheTest.java
 15 files changed, 966 insertions(+), 136 deletions(-)
```

---

## Files Modified

```
M	README.md
A	doc/Armoury.ContinuousDev.md
M	doc/Armoury.Utils.md
M	doc/logo/taijitu.svg
M	doc/logo/utils.svg
M	doc/release_notes.txt
M	hauberk/pom.xml
M	pom.xml
M	taijitu/pom.xml
M	utils/pom.xml
M	utils/src/main/java/pro/mir0n/utils/changes.txt
M	utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
A	utils/src/main/java/pro/mir0n/utils/concurrent/IExpiringCache.java
A	utils/src/main/java/pro/mir0n/utils/concurrent/IKeyedExpiringCache.java
A	utils/src/main/java/pro/mir0n/utils/concurrent/KeyedExpiringCache.java
M	utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheStoreIfGreaterTest.java
M	utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheTest.java
A	utils/src/test/java/pro/mir0n/utils/concurrent/KeyedExpiringCacheTest.java
 18 files changed, 1005 insertions(+), 148 deletions(-)
```

---

*From `v2.0.0` till `HEAD`*
