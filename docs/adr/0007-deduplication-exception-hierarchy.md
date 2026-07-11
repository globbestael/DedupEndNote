# ADR-0007 DeduplicationException hierarchy with single controller catch boundary

**Status:** Decided — implemented in commit 5f97fe9  
**Date:** 2026-06-22  
**Context:** `DeduplicationService.deduplicateOneFile` and `deduplicateTwoFiles` signalled hard failures (record-count cap exceeded, invalid RIS file, duplicate EndNote IDs) by returning `"ERROR: …"` strings. The caller was not required to inspect the return value, making it possible for a test to silently read a stale output file while the deduplication had not actually run. Two such callers existed: `ValidationTests` (safe by luck — the cap was never hit in production) and `AuthorExperimentsTests` (actively broken: `maxRecords` was `0` for hand-constructed `new DeduplicationService(…)` instances because `@Value` injection only runs for Spring-managed beans, so every file exceeded the cap, the service returned early, and the test read a stale mark file from a previous run — causing a phantom "experiment sensitivity equals baseline" result).

## Decision

Replace all hard-failure `"ERROR:"` returns in `DeduplicationService` with a thrown exception hierarchy, caught at a single boundary in `DedupEndNoteController.runDedup`.

**Exception hierarchy** (all in `edu.dedupendnote.services`):

```
RuntimeException
  └─ DeduplicationException          (base; carries getErrorMessage())
       ├─ InvalidRisFileException    (reparented; was extends RuntimeException)
       ├─ RecordCapExceededException (new; thrown by checkRecordCap / two-file cap block)
       ├─ DuplicateIdsException      (new; thrown by doSanityChecks)
       └─ CancelledException         (new; thrown by compareSet / readBibliographicItems
                                      when Thread.currentThread().isInterrupted())
```

**Service contract:** `deduplicateOneFile` and `deduplicateTwoFiles` now throw a `DeduplicationException` subclass on any hard failure and return only a `"DONE: …"` string on success. `doSanityChecks` is `void`; `checkRecordCap` is `void`.

**Controller boundary:** `runDedup` catches exceptions from `future.get(…)` at three levels:

1. `java.util.concurrent.CancellationException` — thrown by `future.get()` when `future.cancel(true)` has been called (the primary user-cancel path). The partial output file is deleted before responding.
2. `ExecutionException` where cause is `CancelledException` — safety-net path if the task thread throws before `future.get()` detects the cancellation. Also deletes the partial output file.
3. `ExecutionException` where cause is any other `DeduplicationException` — sends `getErrorMessage()` to the WebSocket progress reporter and returns `ResponseEntity.ok(new ApiResponse(message))`.

Any other `ExecutionException` is rethrown. All error messages use the `"ERROR: …"` prefix so the browser's terminal-message latch (`dedupFinished` flag) recognises them uniformly alongside `"DONE: …"`. See ADR-0009 for the full cancel design.

**Field initializer:** `maxRecords = 100000` is set as a Java field initializer in addition to the `@Value` annotation, so hand-built instances get the correct default and `@Value` can still override it for Spring-managed beans.

Tests that previously asserted `resultString.startsWith("ERROR:")` on the cap/duplicate-ID paths are rewritten to `assertThatThrownBy(…).isInstanceOf(…)`.

## Alternatives considered

### 1. Assert the return value in each test

Add `assertThat(result).doesNotStartWith("ERROR:")` before trusting the output file. `ValidationTests` and `AuthorExperimentsTests` would be fixed; a guard would be added to `AbstractIntegrationTest` as a reminder pattern for future tests.

**Rejected** because: it relies on every future test author remembering to add the guard. Two existing tests had already forgotten. A contract enforced by the type system (thrown exception = test fails automatically) is unconditionally safer than one enforced by convention.

### 2. Throw only for the record-count cap (narrow exception)

A single `RecordCapExceededException`, caught at the controller. The other hard failures (invalid RIS, duplicate IDs) continue to return `"ERROR:"` strings.

**Rejected** because: it leaves the root problem — silently ignorable return values — in place for two of the three failure modes. The fix would be incomplete and the two remaining `"ERROR:"` paths would continue to be latent traps for future tests.

### 3. Keep returning strings; remove the stale-read risk by deleting output files on error

`checkRecordCap` and `doSanityChecks` could delete any existing output file before returning the error string, so a subsequent read would fail with a missing-file error rather than silently reading stale data.

**Rejected** because: it treats a symptom rather than the cause. The deeper problem is that a `String` return type does not force the caller to handle the failure case; an exception does.

## What to watch for (conditions that would reopen this)

- A future hard failure is added to the service. It must throw a `DeduplicationException` subclass — not return a string. This is the only pattern now.
- The HTTP contract is revisited (e.g. returning a non-200 status for deduplication errors). At that point the `ResponseEntity.ok(…)` in the catch blocks should be updated; the exception hierarchy itself is unaffected.
- A new cancellable blocking operation is added to the pipeline. Add an `isInterrupted()` check inside its loop (see ADR-0009 for why file I/O requires an explicit check rather than relying on virtual-thread interrupt delivery).
