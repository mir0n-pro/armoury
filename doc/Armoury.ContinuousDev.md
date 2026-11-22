# Armoury -- continuous development

Notes on what to build or write next. Each note says what it is and why it is worth doing.


## Taijitu: a tutorial on a ConcurrentHashMap cache

The taijitu library does not pick the storage. The director and the monads hold the control: the
queues, the gates, the night watch, and the swap. The store behind each monad is the application's
choice, and the way the cache is read decides it:

| How the cache is read | The store that fits |
|---|---|
| By key only: `get(id)` | `ConcurrentHashMap` |
| In order: a range, the first key, the nearest key | `ConcurrentSkipListMap` |
| Complex reads: filter, join, pages | An embedded database such as H2 |

Esquire's entity tree reads a subtree, limited to the caller, in pages. That is a complex read, so its
monads use H2. It is the only application today, and it shows the hardest case first.

The note: write a tutorial for the simplest case, a cache read by key on a `ConcurrentHashMap`. It
belongs in [Armoury.Taijitu.md](Armoury.Taijitu.md), next to *Using it*. A reader then sees the whole
pattern without SQL in the way.

What the tutorial covers:

1. **The row.** An immutable record. An update puts a new row and never changes the old one. A
   reader that holds the old row keeps it until it lets go; the garbage collector frees it.
2. **The monad.** Extend `AMonad` and fill the cache-work hook:
   - **Event apply.** Apply an event only when its change number is newer than the row held.
     `replace(key, oldRow, newRow)` is the per-key compare-and-set that keeps an older change from
     overwriting a newer one.
   - **`LOAD`.** Read the source of truth into an empty map.
   - **`CLEAR`.** Empty the map.
   - **`CHECKSUM`.** Hash the rows in key order, so the digest does not depend on the map's
     internal order. An empty map hashes the empty string.
   - **Cancellation.** The `ICancelable` sets a flag that the checksum loop reads, so a timed-out
     checksum stops.
3. **The director.** Extend `ATaijituRig` and add `get(id)`, routed to the serving monad.
4. **The wiring.** Two monads and one director, built as plain objects.
5. **When to change the store.** Keys read in order: `ConcurrentSkipListMap`. Reads that are
   queries: an embedded database.

What it costs, so the tutorial says it:

- The map holds every row twice while the night watch runs, as every taijitu cache does.
- `ConcurrentHashMap` keeps one key consistent at a time. A reader that reads two keys can see one
  before an update and one after it.
- A hash map has no order. A read that needs a range turns into a full scan.

The tutorial is prose and short code listings in the doc. The library itself does not change.
