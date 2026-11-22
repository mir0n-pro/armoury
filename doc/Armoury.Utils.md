<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="logo/utils.svg" alt="utils logo" align="right" width="64" height="64"></td>
    <td style="width: 88%;"><h1>Armoury Utils</h1></td>
  </tr>
</table>

**The base library, at the bottom of the stack.** `armoury-utils` holds the small pieces that every
layer above it needs: an expiring cache, a queue with one worker, a bounded worker pool, and the
host identity. Nothing in it knows Spring, JPA, or any product.

It depends on SLF4J, at `provided` scope: the application supplies it at run time.

| Package | Classes |
|---|---|
| `pro.mir0n.utils.concurrent` | `IExpiringCache`, `ExpiringCache`, `IKeyedExpiringCache`, `KeyedExpiringCache`, `IQueueRig`, `BoundedQueueRig`, `WorkerPool` |
| `pro.mir0n.utils` | `HostId` |
| `pro.mir0n.o11y` | `BizMeters`, `Gauges`, `TraceMark` |

The jar also carries `pro.mir0n.o11y`: three Micrometer helpers for business meters, gauges, and
trace spans. Micrometer is declared at `provided` scope and is used by this package alone. The
package has its own root, so a jar of its own later is a pom and nothing else.


## The expiring cache: one interface, three ways to use it

A cache whose entries expire by age. `IExpiringCache` is the interface, with the usual cache names.
`ExpiringCache` implements it. `KeyedExpiringCache` extends both with a read-through load and an
eviction by a second key.

| Method | What it does |
|---|---|
| `get(key)` | Returns the value, or null when there is none or it is older than the time to live. |
| `put(key, value)` | Puts the value. A later put for the same key replaces it. |
| `putIfGreater(key, value)` | Puts the value only when it is greater than the one there, in one atomic step. An expired entry counts as absent. |
| `getAndRemove(key)` | Takes the value away and returns it. |
| `remove(key)` | Removes the entry. |
| `keys()` / `size()` | A copy of the keys at this moment, and their number. |
| `start()` / `stop()` | Starts and stops the background prune thread. Both can be called twice. |

The methods are the same for every use. What changes is how the age is counted, and that is chosen
when the cache is built:

| Expiry | The age counts from | So an entry lives |
|---|---|---|
| `AFTER_WRITE` (the default) | the put | a fixed time, however often it is read |
| `AFTER_ACCESS` | the last `get` | as long as it is asked for |


### A hand-off

A value is parked now and picked up once, shortly after. The cache is built `AFTER_WRITE`. The
producer calls `put`, or `putIfGreater` when arrivals can come out of order and the greatest must win.
The consumer calls `getAndRemove`. A value nobody picks up within the time to live is dropped.

For example, an update can arrive for a record that does not exist yet. The update is parked under the
record's key. The create that follows takes it with `getAndRemove` and applies it.


### A fixed-age lookup

A value is read many times until it is too old. The cache is built `AFTER_WRITE`. A reader calls
`get`. On a miss it loads the value from its source and calls `putIfGreater`. `remove` drops an entry
before its time, for example when the source revokes it. A token cache fits here: the token is good
for a known time, and reading it does not make it last longer.


### A read-through cache whose source can change

A value is read many times, kept while it is asked for, and removed when its source changes. This is
`KeyedExpiringCache`, built `AFTER_ACCESS`. It adds two methods:

| Method | What it does |
|---|---|
| `get(key, loader)` | Returns the cached value. On a miss it calls the loader and stores the answer. |
| `evict(extraKey)` | Removes every entry whose value carries this extra key. Returns how many it removed. |

The extra key is a field of the value, named once in the constructor. It lets the source name the
record in its own terms. For example, the cache is keyed by a login id, and a change event names the
user by an internal id. One user can sit under more than one cache key, so `evict` scans the cache.

**A load and an eviction can race.** A load starts. Its source changes. The eviction arrives and finds
nothing to remove, because the load has not returned yet. Then the load returns with the old value and
stores it. In an `AFTER_ACCESS` cache that value does not age while it is read. A user who keeps
working keeps the old value.

`get(key, loader)` closes that gap:

1. On a miss, it takes a ticket: the number of evictions so far, and the time.
2. It calls the loader. The loader returns a `CompletableFuture`, so the library needs no reactive
   framework. A caller that works with another type wraps it.
3. When the answer comes back, it checks and stores under one lock. If an eviction of the answer's extra
   key came after the ticket, the answer is not stored and the loader is called again.
4. `evict` takes the same lock. It records the eviction, then removes the entries.

So an eviction lands either before the check, and the check sees it, or after the store, and the scan
removes the value. Nothing lands in between.

Each eviction record is kept for one time to live. A ticket older than that never stores; its load is
made again. This means no answer can arrive after the record it needs is gone.

The price: one lock on every stored load and every eviction; a scan of the cache on every eviction; a
second load when an eviction lands during the first, or when a load takes longer than the time to live.
The plain `put` and `putIfGreater` are still there, and they skip the check. A read-through user stores
through `get(key, loader)` only.


### What every use shares

**Expiry happens in two places.** The scheduled prune keeps the map from growing. A reader does not
depend on it: every read checks the age of the entry it found and returns null when it is too old.
Without that check, an entry could slip past the prune interval and reach a caller as if it were
fresh. A cache that was never started is still correct, only unbounded, which is what a unit test
wants. The prune removes an entry only while it is still the one the prune read, so a value put in
the meantime stays. The eviction records of `KeyedExpiringCache` are pruned the same way.

**`putIfGreater` is one operation on purpose.** "Read it, compare it, put it back" is a race when two
threads do it for the same key: the older value can win. The value type is `Comparable`, so the cache
needs no comparator and never learns what its callers order by.

The logger and the timings come in through the constructor, so the cache carries no dependency on
any configuration mechanism. The owner supplies both.


## The queue rig: IQueueRig and BoundedQueueRig

`IQueueRig` is the contract; `BoundedQueueRig` is the implementation: a fixed-size FIFO queue drained
by one daemon worker thread. The worker is given at construction; `init(name, logger, capacity)`
sizes the queue and names the thread.

**The processing gate.** While processing is off, the worker waits before it takes an item. Queued
items stay on the queue, in order, and `size()` still counts them. Producers can keep putting items
in, and wait only when the queue is full. When processing is turned on, the worker drains the queue.
`clear()` is the only other way to remove items: a bulk drop for buffered work that must be thrown
away.

| Method | What it does |
|---|---|
| `put(item)` | Adds an item; waits while the queue is full. |
| `tryPut(item)` | Adds an item without waiting; returns false when the rig is stopped or the queue is full. |
| `setProcessing(on)` | Opens or closes the processing gate. |
| `clear()` | Drops every queued item. |
| `setBulkThreshold(n)` | Above this backlog (default 10), a list worker receives a run of items in one call. |
| `setErrorListener`, `setSuccessListener` | The outcome of each item, reported to the owner. |

**One bad item does not stop the worker.** Each item is processed outside the lock, and a throw is
passed to the error listener, or logged, and the worker goes on. An interrupt while waiting is the
shutdown signal.

**Bulk.** A worker that implements the list form receives a run of queued items in one call once the
backlog passes the threshold. Order is kept, and there is still one thread. The cost is that the
worker has to handle a list. When a run fails, its error listener receives the whole run and
returns the items to go on with, or null to drop the rest of the run.


## WorkerPool

A bounded pool of workers, with three thread models. `WorkerPool.create(name, size, mode)` builds it;
`submit(work)` hands work in and applies the bound: take a permit, run the work on the pool, release
the permit when it ends.

| Mode | Threads |
|---|---|
| `PLATFORM` | A fixed pool of `size` reused platform threads. |
| `VIRTUAL` | A fixed pool of `size` reused virtual threads: the same shape as `PLATFORM`, on cheaper threads. |
| `VIRTUAL_PER_TASK` | A new virtual thread per task. `size` is the optional cap: above 0 it bounds the workers that run at once; 0 means no cap. |

`PLATFORM` and `VIRTUAL` need `size >= 1`; `VIRTUAL_PER_TASK` accepts 0. `Mode.of(token)` reads a mode
from a setting. `submit` returns false when it was interrupted while waiting for a permit, or when the
pool is shutting down; the caller decides whether to log or drop. The work handles its own errors; a
throw from it never loses a permit. `shutdown(awaitSeconds)` stops the pool.


## HostId

The host identity of this container or pod, and the instance number taken from it. One source for
both: the host name.

- `instanceHost()` returns the host name.
- `instanceNo()` returns the number after the last dash of the host name, when that part is all
  digits: `esquire-gateway-1` gives `1`. Any other name gives `0`. The result is cached after the
  first call.

In Kubernetes a StatefulSet names its pods with a trailing ordinal, so each replica gets its own
number with no configuration. Two test hooks pin or reset the cached number.

