# ADR-0009 User-initiated cancellation of a running deduplication

**Status:** Decided — implemented 2026-07-04  
**Context:** There was no way for a user to stop a deduplication once started. The only
termination path other than normal completion was a server-side timeout. A Cancel button was
requested.

## Decision

Cancellation is implemented via five cooperating mechanisms. Each addresses a different facet
of the problem; omitting any one leaves an observable gap.

### 1. `POST /cancelDedup` endpoint + `runningFutures` map

`DedupEndNoteController` maintains a `ConcurrentHashMap<UUID, Future<?>> runningFutures`
keyed by `wssessionId`. `runDedup` registers the `Future` after `executor.submit()` and
removes it in the `finally` block. The cancel endpoint looks up the future and calls
`future.cancel(true)`.

`future.cancel(true)` does two things atomically: marks the future as cancelled (so
`future.get()` returns immediately) and sends an interrupt to the virtual task thread.

### 2. Dedicated `CancellationException` catch block in `runDedup`

When `future.cancel(true)` is called from the cancel endpoint, `future.get()` on the main
HTTP thread throws `java.util.concurrent.CancellationException` — **not** `ExecutionException`.
A dedicated `catch (CancellationException e)` block is required; without it, the exception
propagates as an unhandled 500.

This block deletes the partial output file and sends the "ERROR: cancelled" message to the
browser via `progressReporter`. The `progressReporter` call runs on the *main HTTP thread*
(not interrupted), so the guard described in point 4 does not suppress it.

### 3. Explicit interrupt checks in the pipeline — `compareSet` and `readBibliographicItems`

`future.cancel(true)` sends an interrupt to the virtual task thread, but the interrupt is
only acted on when the thread is at a blocking point the JVM can intercept.

- **Blocking socket I/O** (e.g. network reads): Project Loom parks virtual threads during
  blocking I/O and delivers the interrupt promptly via park/unpark.
- **Blocking file I/O**: on most platforms, file reads are submitted to a kernel thread pool
  and are *not* reliably interruptible this way. Without an explicit check, file reading runs
  to completion regardless of the interrupt.
- **CPU-bound loops**: never block, so the interrupt is never delivered by the runtime.

Therefore, explicit `Thread.currentThread().isInterrupted()` checks are added:

- `BibliographicItemReader.readBibliographicItems` — after each complete record (`ER`
  boundary). Granularity: at most one extra record parsed after cancel.
- `DeduplicationService.compareSet` — at the top of the `while` loop. Granularity: at most
  one pivot iteration after cancel.

Both throw `CancelledException` (a `DeduplicationException` subtype) on detection. Since
`future.get()` has already thrown `CancellationException` at this point, these exceptions
are discarded by the future's internal state machine — they serve only to terminate the task
thread cleanly and release its resources.

### 4. `progressReporter` interrupt guard in `startOneFile` / `startTwoFiles`

There is a window between `future.cancel(true)` and the task thread noticing the interrupt
during which the task thread may send additional WebSocket messages (PROGRESS updates,
"Working on…" messages). These can arrive at the browser *before* the "ERROR: cancelled"
message sent by the main thread, so the `dedupFinished` latch (point 5) has not yet been
set when they land — making them visible to the user.

The `progressReporter` lambda is guarded:

```java
Consumer<String> progressReporter = message -> {
    if (!Thread.currentThread().isInterrupted()) {
        simpMessagingTemplate.convertAndSend(...);
    }
};
```

When the task thread is interrupted, it cannot send any WebSocket messages. The main HTTP
thread (which sends the terminal ERROR/DONE messages) is never interrupted, so those
messages are unaffected.

### 5. `dedupFinished` terminal-message latch in the browser (`dedup-utils.js`)

Even with the server-side guard (point 4), a small number of in-flight messages sent *before*
the interrupt was set may still arrive after the terminal message. The browser-side latch
provides a second line of defence.

`var dedupFinished = false` is set to `true` in the STOMP subscriber when a message
matching `^DONE` or `^ERROR` arrives. All subsequent messages for the session are silently
dropped. The flag is reset to `false` at the start of each new Start click.

All server-side terminal messages (DONE, all error conditions, timeout, cancellation) use
either the `"DONE: …"` or `"ERROR: …"` prefix, so the latch recognises them uniformly
without special-casing.

## Alternatives considered

### A. AtomicBoolean `cancelled` flag passed through the pipeline

A shared `AtomicBoolean` could be checked by the reader and comparison loops without relying
on thread interrupt semantics.

**Rejected** because: it requires threading a new parameter through `deduplicateOneFile`,
`deduplicateTwoFiles`, `compareSet`, and `readBibliographicItems`. Thread interruption is
already the natural Java mechanism for this, and `future.cancel(true)` sets it without any
extra wiring. The `isInterrupted()` check pattern is identical to what the `AtomicBoolean`
approach would produce.

### B. Rely solely on the `dedupFinished` browser latch

Suppress stale messages in the browser only; let the task thread run to completion.

**Rejected** because: (a) reading a large file (up to 150 MB) could take tens of seconds
after cancel — wasteful server resources; (b) the "reading still going on" visible in the
progress bar was reported as confusing by users even with the correct terminal message
displayed.

### C. Typed WebSocket messages (PROGRESS / TERMINAL) instead of prefix matching

Add a `type` field to `StompMessage`. The browser ignores PROGRESS after receiving TERMINAL.

**Rejected** because: the existing message format already uses `"DONE: …"` and `"ERROR: …"`
prefixes consistently for terminal outcomes. Prefix matching in the browser is one regex
check; a typed protocol would require changing `StompMessage`, all `progressReporter.accept`
call sites, and the JavaScript deserialisation — more change for no practical gain.

## What to watch for

- A new blocking I/O operation is added to the pipeline (e.g. a network fetch). If it uses
  socket I/O on a virtual thread, the interrupt is delivered automatically. If it uses file
  I/O or a non-interruptible blocking call, add an explicit `isInterrupted()` check.
- A new terminal outcome is added (e.g. a new error condition in the service). Its message
  must start with `"ERROR: "` to be recognised by the browser's `dedupFinished` latch.
