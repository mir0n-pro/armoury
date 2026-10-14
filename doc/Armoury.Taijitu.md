<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="logo/taijitu.svg" alt="taijitu logo" align="right" width="64" height="64"></td>
    <td style="width: 88%;"><h1>Armoury Taijitu</h1></td>
  </tr>
</table>

**A recoverable cache.** A taijitu cache keeps a copy of data whose source of truth lives elsewhere,
shaped for fast reads, and fed by a stream of change events. On a schedule it rebuilds a second copy
from the source, compares the two, and repairs any drift. An event that was missed is recovered
without a restart.

The library holds the control: the two copies, the director, the queues, the night watch. It knows
nothing about trees, entities or SQL. An application supplies the cache work for its own store, and
the reads its callers need.


## The name

The cache is the **Supreme Ultimate Cache** (*Taiji*, "Supreme Ultimate"). Its structure is the
**Taijitu** ("Diagram of the Supreme Ultimate"): two equal monads, yang and yin, behind a single
director that swaps them on drift. Three labels for three audiences:

- **Supreme Ultimate Cache** -- the formal name. Doc headings, lead sentences.
- **Taijitu** -- the short, technical name. Code identifiers (`ATaijituRig`, `AMonad`, the `sweep`
  night watch), file names, log lines.
- **Anti-entropy double-buffer with shadow promotion** -- the dry industry vocabulary. Use it when the
  metaphor does not help: papers, pattern catalogs.

Same shape as **Haubergeon / hauberk / Gatling harness** in the testing layer.


## Structure: two monads behind one director

The cache is a **Taijitu**: two independent **monads** behind one **director**.

Each monad is a **complete copy of the cache**: its own store, its own queue, its own worker. It offers
the same two capabilities: read access for queries, and event processing on its own queue. The two
monads are never connected directly; the director sits between them and the outside world. Which one
is serving and which one is the shadow is a role pointer the director flips.


### The two roles: yang and yin

At any moment one monad holds the **yang** role and the other **yin**:

| Role | Character | Behavior |
|---|---|---|
| **yang** | bright, serving | Answers reads; receives every change event on its queue. |
| **yin** | dark, shadow | Idle until a night-watch sweep loads it fresh from the source of truth; receives change events only for the duration of the sweep. |

The pair is symmetric: either monad can hold either role. The roles swap during a sweep when drift
is found (see *Night-watch sweep*).


### The director

The director, `ATaijituRig` in `pro.mir0n.taijitu`, extended by the application, owns:

1. **Read routing.** Every read goes to the serving monad (`yang()`).
2. **Event fanout.** Change events always go to yang's queue, and also to yin's queue while yin is
   part of an active sweep.
3. **Command issuance.** It drives each monad's state through the command set below.
4. **Result reception.** Each monad calls back a per-monad listener (`gateFor(monad)`) when a command
   completes; the listener drives that monad's gates from the result.

`ATaijituRigY` is the single-monad director: one active monad, no shadow, no swap. `ATaijituRig`
extends it with the second monad, the night watch and the swap.


### Monad state

Each monad carries:

| Field | Values | Meaning |
|---|---|---|
| `queueEnabled` | on / off | Whether new events can land on the monad's queue. |
| `processingEnabled` | on / off | Whether the worker drains the queue or holds it. |
| `status` | `IDLE` / `LOADING` / `LOADED` / `FAILED` | Lifecycle position (`MonadStatus`). |


### Monad commands

The director issues three commands; each rides the monad's queue as a `QueueItem` (`MonadCmd`):

| Command | Effect |
|---|---|
| `CLEAR` | Empty the monad's store; `status -> IDLE`. |
| `LOAD`  | Load the data from the source of truth into the monad's store; `status -> LOADING -> LOADED` (or `FAILED`). |
| `CHECKSUM` | Compute a content hash of the monad's store and report it through the result listener. |

`LOAD` and `CLEAR` run **synchronously** on the monad's single worker thread (`doCommand` blocks until
the worker signals). `CHECKSUM` is the exception -- see *Off-worker CHECKSUM* below.


### The checksum

The application computes the digest. Two rules make it comparable:

- **Order-independent.** The rows are combined in a fixed key order, so the physical order of the
  store does not matter.
- **Content-only.** It detects drift between two hashes of the same data, computed locally. It has no
  security role, so a fast hash such as MD5 is the right choice.

An empty store must still produce a digest (the hash of an empty string), so two empty legs match
instead of both reporting "no value".


## Routines

The director owns three routines: a once-at-startup bootstrap, the periodic night-watch sweep, and
shutdown.


### Bootstrap (process startup)

Brings the serving monad from `IDLE` to `LOADED` and opens the read gate. It runs once,
synchronously; a failed load retries (about 5 s apart) until it succeeds.

```
0. yang.status = IDLE; gates off; the director rejects reads.
1. CLEAR yang (clean slate), then LOAD yang with queueEnabled = ON,
   processingEnabled = OFF -- events arriving mid-load BUFFER, are not applied.
2. yang.status -> LOADING -> LOADED (or FAILED).
3. LOADED  -> processingEnabled = ON; yang drains the buffered events in
              arrival order onto the now-consistent cache; reads open.
   FAILED  -> gates off, CLEAR, retry LOAD after the retry delay.
```


### Night-watch sweep (periodic; also forceable)

The anti-entropy mechanism. The shadow is rebuilt from the source of truth, both monads are
checksummed, and the director reacts to drift. The sweep is **serialized** (one at a time, guarded)
and **re-armed after it ends**: the next sweep starts `sweepIntervalMs` after the previous one
finishes, not on a fixed clock. `sweepAsync()` forces a sweep on the night-watch thread and returns
at once.

```
1. LOAD the shadow (yin) fresh from the source of truth into its own store.
   Shadow LOAD FAILED -> abandon this sweep; retry next round.
2. Submit CHECKSUM to BOTH legs; collect each digest within sweepTimeoutMs.
3. A leg with no digest leaves nothing to compare. TIMEDOUT (too slow to measure)
   -> promote the shadow loaded in step 1, whatever the configured mismatch mode.
   FAILED (the query threw) or INTERRUPTED (going down) -> keep serving, retry next round.
4. Compare the two digests:
     MATCH    -> serving monad is healthy; nothing to do.
     MISMATCH -> react per the configured mode (below).
5. Clear the shadow back to idle (always).
```

The shadow is loaded fresh on every sweep, whether that sweep ends in a promotion or not, so a
promotion costs nothing and loses nothing. That is what the digests are compared for: not to decide
the swap, but to raise the alarm when the two legs disagree.

On mismatch the reaction is the director's `onMismatch` (`MismatchAction`):

| Mode | Reaction |
|---|---|
| `LOG` | Record the drift, keep serving the current monad (the conservative default). |
| `SWAP` | Promote the freshly-loaded shadow to serving (the double-buffer pointer flip); the old serving monad becomes the next shadow. |
| `TERMINATE` | Exit the process, so an orchestrator relaunches it from a clean load. |


### Shutdown (process exit)

The director's `shutdown()` stops the night watch and both monads' workers; the application calls it
when the process stops. The source of truth holds the data, and the cache is rebuilt from it on the
next bootstrap.


## Readiness

The bootstrap `LOAD` can block after the application already accepts requests, so a plain health
check would report UP before the cache can serve. `isReady()` on the director is true once the
serving monad is `LOADED`. An application wires it into its readiness check, so traffic waits until
the cache is serving.


## Invariants

Architectural rules, not settings:

- **Two monads, always.** "Taijitu" *is* "two equal units behind one director."
- **Sequential message processing.** Each monad drains its queue with exactly one worker thread;
  event order (`CREATE` before `UPDATE` before `DELETE`) is kept end to end. There is no concurrency
  on the write path. Under a backlog the worker batches a run of queued events into one store
  transaction (`setBulkThreshold`): still strictly in order, still one thread, fewer commits.
- **Event fanout during a sweep is ALL.** From the moment yin's `queueEnabled` turns on, it receives
  every change event, unfiltered, so it converges with yang on the same stream. Otherwise the
  CHECKSUM comparison cannot be trusted.
- **The source of truth is elsewhere.** A restart always rebuilds the cache from it.


## Configuration

The library's settings are setters on the director and the monad; the application binds them to
its own configuration.

| Setter | Code default | Role |
|---|---|---|
| `ATaijituRig.setOnMismatch` | `LOG` | Drift reaction: `LOG` / `SWAP` / `TERMINATE`. |
| `ATaijituRig.setSweepIntervalMs` | `10000` | Delay between the end of one sweep and the start of the next. |
| `ATaijituRig.setSweepTimeoutMs` | `10000` | Per-leg CHECKSUM deadline within a sweep. |
| `AMonadY.setBulkThreshold` | `10` | Backlog depth above which the worker batches queued events into one store transaction (the queue rig's default). |
| `AMonadY.setEventBatchMax` | `1024` | The largest batch the worker applies in one transaction. |

The queue capacity is a constructor argument of the monad; the queue is a `BoundedQueueRig` from
`armoury-utils`.


## How it is built

### Off-worker CHECKSUM with cancellation

`LOAD` and `CLEAR` depend on order and run on the monad's single worker thread. `CHECKSUM` is
different: a full content-hash scan can take seconds to minutes, so it runs on a **separate one-run
thread** (`checksumExec`), detached from the queue, and the worker stays free to process events.

A checksum that times out must abort the running query, not merely ignore a late result. Otherwise a
slow scan ties up a connection and a thread. The seam:

- When the checksum starts, before it issues the query, it registers an `ICancelable` with the result
  listener through `onStarted(command, cancelable)`.
- The application's `ICancelable` holds what is running -- for example a JDBC `PreparedStatement` --
  and its `cancel()` stops it, so the in-flight query throws and the leg reports `FAILED`.

The sweep's per-leg `sweepTimeoutMs` arms this: if a digest does not arrive in time, the director
cancels the query and the leg comes back `FAILED`. When even the cancel produces no answer, the
waiter writes `TIMEDOUT` itself, and `INTERRUPTED` if the waiting thread was interrupted. All three
are kept out of the comparison the same way -- two legs carrying the same non-digest must never
compare EQUAL and be reported as a match nobody measured -- but what follows depends on which one it
is:

- `TIMEDOUT` -- the data is there; only the measurement was too slow. The shadow carries what the
  source of truth held at load time plus every event since, so the sweep promotes it rather than go
  on serving a leg it could not check. This promotion does not follow `onMismatch`: it answers a
  missing measurement, not drift.
- `FAILED` -- the leg's own query threw, so there is no store to trust on either side. The sweep
  reports it and leaves both legs where they are.
- `INTERRUPTED`, and any result once shutdown has begun -- the process is going down, and service
  does not move on the way out.


### The two-flag load sequence

Each monad carries two independent gates, `queueEnabled` and `processingEnabled`, not one. That
solves loading while the event stream is live:

```
queueEnabled   processingEnabled   behavior
------------   -----------------   ----------------------------------------
off            off                 idle; events dropped
on             off                 events BUFFER in the queue; none applied   <- mid-LOAD
on             on                  steady state: worker drains + applies
```

While `LOAD` reads the snapshot out of the source of truth, events keep arriving. Dropping them would
let the cache drift; applying them to a half-loaded store would corrupt it. Buffering (queue on,
processing off), then draining after `LOAD` completes, loses nothing and corrupts nothing. The same
two-step flip runs on yang at bootstrap and on yin at each sweep.


## Using it

`armoury-taijitu` holds the control core, in `pro.mir0n.taijitu`; the queue machinery is in
`armoury-utils` (`pro.mir0n.utils.concurrent`). Neither knows the domain. Any cache that needs
reconciliation against a source of truth can adopt it with two small, ordinary classes:

- **Extend `AMonad`** (the monad mechanics: queue, gates, status, off-worker CHECKSUM) and fill the
  cache-work hook: `LOAD` / `CLEAR` / `CHECKSUM`, and event apply, against the store the domain uses.
- **Extend `ATaijituRig`** (the two-monad director, swap, gates, night watch) and add the domain read
  methods, routed to the serving monad.
- **Wire the two monads and the director** as ordinary objects -- for example beans in a Spring
  `@Configuration`.

The library supplies the control contracts (`IMonad`, `ITaijituRig`), the command vocabulary
(`MonadCmd`), the status machine (`MonadStatus`), the drift policy (`MismatchAction`), the listener
and cancel seams (`ICmdResponseListener`, `ICancelable`), and the night watch itself. The application
contributes only its own cache work and reads.

Esquire's entity tree (bizTree) is the first application built on it.


## Pattern identification

The Taijitu combines three patterns that are well known one by one; the combination is an original
synthesis:

- **Anti-entropy repair** (the Dynamo / Cassandra family). A periodic routine computes content
  hashes on two replicas (yang and a freshly loaded yin), compares them, and reconciles on divergence.
  The night-watch sweep is exactly an anti-entropy run -- except the comparator is built fresh per
  sweep from the source of truth and discarded afterward, not a long-lived second replica.
- **Double-buffering** (graphics / OS rendering). Two equal buffers, one serving (front / yang) while
  the other is prepared (back / yin); an atomic pointer flip promotes back to front. The
  swap-on-mismatch is the pointer flip -- except it swaps only on drift, not every frame.
- **Shadow replica** (payments, search-index rebuilds). A parallel copy is built from the source of
  truth, exists only long enough to validate the live system, and is discarded (no drift) or promoted
  (drift). The yin lifecycle is the shadow lifecycle -- except yin receives only the change stream,
  never read traffic, and only during the sweep.

In one line, when the metaphor is not wanted: **"anti-entropy double-buffer with shadow promotion."**
