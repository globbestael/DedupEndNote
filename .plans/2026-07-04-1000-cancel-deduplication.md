# User-initiated cancellation of a running deduplication

## Context

The FIXME comment in `DedupEndNoteController` noted that reloading the page does not stop a
running deduplication, and asked whether that is possible. The same gap applied to deliberate
user cancellation: there was no "Cancel" button.

`future.cancel(true)` in `runDedup` fired only on timeout (`TimeoutException`). The HTTP
request thread was blocked on `future.get()` for the entire run, so the browser had no way to
signal "stop" while waiting.

Virtual threads (Project Loom) honour `Thread.interrupt()` on blocking *socket* I/O
automatically, but file I/O is not reliably interruptible this way — so an explicit
`isInterrupted()` check is required in the reading and comparison loops.

## Changes

### 1. New exception: `CancelledException`

File: `src/main/java/edu/dedupendnote/services/CancelledException.java`

New class, extends `DeduplicationException`. Keeps user-initiated cancellation distinguishable
from other failure modes (invalid RIS, duplicate IDs, record cap) so the controller can apply
cancel-specific cleanup (output file deletion) without affecting those paths.

### 2. `BibliographicItemReader.readBibliographicItems` — per-record interrupt check

File: `src/main/java/edu/dedupendnote/services/BibliographicItemReader.java`

After `bibliographicItems.add(bibliographicItem)` in the `"ER"` case (once per complete
record):

```java
if (Thread.currentThread().isInterrupted()) {
    throw new CancelledException("ERROR: Deduplication was cancelled by the user.");
}
```

File I/O on virtual threads is not interruptible by `Thread.interrupt()` the way socket I/O
is, so reading would otherwise run to completion even after `future.cancel(true)`. The
per-record explicit check stops the reading promptly (at worst one record after the interrupt
arrives).

### 3. `DeduplicationService.compareSet` — per-pivot interrupt check

File: `src/main/java/edu/dedupendnote/services/DeduplicationService.java`

At the top of the `while (bibliographicItems.size() > 1)` loop:

```java
if (Thread.currentThread().isInterrupted()) {
    throw new CancelledException("ERROR: Deduplication was cancelled by the user.");
}
```

One check per pivot iteration; the inner `for` loop does not need its own check.

### 4. `DedupEndNoteController` — futures map, `CancellationException` catch, `wssessionId` in `runDedup`, cancel endpoint

File: `src/main/java/edu/dedupendnote/controllers/DedupEndNoteController.java`

**New field:**
```java
private final ConcurrentHashMap<UUID, Future<?>> runningFutures = new ConcurrentHashMap<>();
```

**`runDedup` signature** — add `UUID wssessionId`. Both callers (`startOneFile`,
`startTwoFiles`) already have it in scope.

**Register/deregister the future** around the `get()` call:
```java
runningFutures.put(wssessionId, future);
try { ... } finally {
    runningFutures.remove(wssessionId);
    concurrentRunsSemaphore.release();
}
```

**`progressReporter` lambda in `startOneFile` and `startTwoFiles`** — guard with interrupt
check so the task thread cannot send WebSocket messages after being interrupted (eliminates
the race where late task messages overwrite the terminal ERROR message in the browser):
```java
Consumer<String> progressReporter = message -> {
    if (!Thread.currentThread().isInterrupted()) {
        simpMessagingTemplate.convertAndSend("/topic/messages-" + wssessionId, new StompMessage(message));
    }
};
```
The controller's own `progressReporter.accept(msg)` calls in catch blocks run on the *main
HTTP thread* (which is never interrupted), so terminal messages still go through.

**`CancellationException` catch block** — when `future.cancel(true)` is called from the
cancel endpoint, `future.get()` throws `java.util.concurrent.CancellationException` (not
`ExecutionException`). A dedicated catch block is required:
```java
} catch (CancellationException e) {
    Files.deleteIfExists(outputPath);
    String msg = "ERROR: Deduplication was cancelled by the user.";
    progressReporter.accept(msg);
    return ResponseEntity.ok(new ApiResponse(msg));
}
```

**`CancelledException` inside `ExecutionException`** — safety-net path (fires only if the
task thread throws before `future.get()` sees the cancellation). Checked before the general
`DeduplicationException` branch; also deletes the partial output file.

**Cancel endpoint:**
```java
@PostMapping(value = "/cancelDedup", produces = "application/json")
public ResponseEntity<ApiResponse> cancelDedup(@RequestParam UUID wssessionId) {
    Future<?> future = runningFutures.get(wssessionId);
    if (future == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse("No running deduplication found."));
    }
    future.cancel(true);
    return ResponseEntity.ok(new ApiResponse("Cancellation requested."));
}
```

**FIXME comment** — replaced with a note that stopping via `POST /cancelDedup` is now
implemented.

### 5. Terminal-message latch in the browser — `dedupFinished` flag

File: `src/main/resources/static/dedup-utils.js`

Module-level flag `var dedupFinished = false;`. In the STOMP subscriber:

```js
if (dedupFinished) return;
// ... existing handlers ...
if (message.match("^DONE")) { dedupFinished = true; ... }
else if (message.match("^ERROR")) { dedupFinished = true; ... }
```

Once a terminal message arrives, all subsequent WebSocket messages for the session are
silently dropped — covers cancellation, timeout, and all other error paths.

All cancel/error/timeout messages use the `"ERROR: ..."` prefix so they hit the `^ERROR`
branch. The cancel message is `"ERROR: Deduplication was cancelled by the user."`.

`dedupFinished` is reset to `false` in the `buttonStartDeduplication` click handler in both
`index.html` and `twofiles.html` so a fresh run receives all its progress messages.

### 6. Frontend — Cancel button

Both `src/main/resources/templates/index.html` and `src/main/resources/templates/twofiles.html`.

Cancel button added to `step2-progress` div (hidden initially with `d-none`). Shown when
Start is clicked, hidden again in the AJAX `complete` callback. Click handler POSTs to
`/cancelDedup` and disables the button to prevent double-clicks.

## What is NOT changed

- The `TimeoutException` path is unchanged: it still calls `future.cancel(true)` and deletes
  the output file. The timeout mechanism is orthogonal to user cancellation.
- `BibliographicItemWriter` write loops are I/O-bound; they are short relative to reading and
  comparison, and file I/O interruption is handled by the OS at the syscall level.
- The `compareSet` inner `for` loop does not get an interrupt check — the outer `while`
  granularity is sufficient.

## Verification

1. `./mvnw test -Punit-tests` — compile + unit regression.
2. `./mvnw test -Pintegration-tests` — `DeduplicationServiceTests` (one-file + two-file,
   MARK + REMOVE) are the regression guard.
3. Manual test — cancel while reading a large file:
   - "ERROR: Deduplication was cancelled by the user." appears and is not overwritten.
   - Progress bar stops updating immediately.
   - No partial output file on disk.
   - Cancel button disappears; Start button re-enables.
4. Manual test — cancel while comparing:
   - Same outcome as above.
5. Manual test — cancel with no running job (e.g., double-click):
   - `/cancelDedup` returns 404; UI is already in the done state.
