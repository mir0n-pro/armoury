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
| `pro.mir0n.utils.concurrent` | `ExpiringCache`, `IQueueRig`, `BoundedQueueRig`, `WorkerPool` |
| `pro.mir0n.utils` | `HostId` |
| `pro.mir0n.o11y` | `BizMeters`, `Gauges`, `TraceMark` |

The jar also carries `pro.mir0n.o11y`: three Micrometer helpers for business meters, gauges, and
trace spans. Micrometer is declared at `provided` scope and is used by this package alone. The
package has its own root, so a jar of its own later is a pom and nothing else.


## ExpiringCache

A cache whose entries expire by age. It hands a value over: `store` puts a value under a key,
`consume` takes it away and returns it, and anything not consumed within the time to live is dropped.

| Method | What it does |
|---|---|
| `store(key, value)` | Puts the value, stamped with the time. |
| `storeIfGreater(key, value)` | Puts the value only when it is greater than the one there, in one atomic step. An expired entry counts as absent. |
| `consume(key)` | Takes the value away. Returns null when there is none, or when it is older than the time to live. |
| `peek(key)` | Reads the value without taking it. |
| `start()` / `stop()` | Starts and stops the background prune thread. Both can be called twice. |

**Expiry happens in two places.** The scheduled prune keeps the map from growing. A reader does not
depend on it: `consume` checks the age of the entry it removed and returns null when it is too old.
Without that check, an entry could slip past the prune interval and reach a caller as if it were
fresh. A cache that was never started is still correct, only unbounded, which is what a unit test
wants.

**`storeIfGreater` is one operation on purpose.** "Read it, compare it, put it back" is a race when two
threads do it for the same key: the older value can win. The value type is `Comparable`, so the cache
needs no comparator and never learns what its callers order by.

**`consume` removes.** This is a hand-off, not a lookup table. A cache that needs to be read many times
is a different thing.

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

