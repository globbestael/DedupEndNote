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
       └─ DuplicateIdsException      (new; thrown by doSanityChecks)
```

**Service contract:** `deduplicateOneFile` and `deduplicateTwoFiles` now throw a `DeduplicationException` subclass on any hard failure and return only a `"DONE: …"` string on success. `doSanityChecks` is `void`; `checkRecordCap` is `void`.

**Controller boundary:** `runDedup` catches `ExecutionException` from `future.get(…)`. If the cause is a `DeduplicationException`, it sends `getErrorMessage()` to the WebSocket progress reporter and returns `ResponseEntity.ok(new ApiResponse(message))` — preserving the existing HTTP contract (200 with an error string in the body, displayed by the client JS). Any other `ExecutionException` is rethrown.

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
- The HTTP contract is revisited (e.g. returning a non-200 status for deduplication errors). At that point the `ResponseEntity.ok(…)` in the `catch (ExecutionException)` block should be updated; the exception hierarchy itself is unaffected.
