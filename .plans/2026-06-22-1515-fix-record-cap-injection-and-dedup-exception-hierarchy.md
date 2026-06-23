# Fix record-cap injection + make hard dedup errors un-ignorable

## Status

**Complete. All tests green: unit 584, integration 23, validation 20 (AuthorExperimentsTests now passes).**
Not committed.

## Context

Three related problems surfaced around `DeduplicationService`'s record-count cap:

1. **`maxRecords` is `0` when the service is hand-built.** `@Value` only runs for Spring-managed
   beans; `AuthorExperimentsTests` builds with `new DeduplicationService(...)`, so the `int` stays
   at `0`. With `maxRecords == 0`, `checkRecordCap` flagged every file as over-cap, bailed early,
   and the test silently read a stale mark file — explaining the "experiment sensitivity == baseline"
   anomaly from the previous task (custom thresholds never ran).

2. **Hard-error returns can be silently ignored.** `deduplicateOneFile`/`deduplicateTwoFiles`
   signalled failures (cap, invalid RIS, duplicate IDs) by returning `"ERROR:"` strings. Two callers
   (`ValidationTests:633`, `AuthorExperimentsTests:73`) ignored the return and read the output
   blindly. Decision: unify all hard failures into a thrown exception hierarchy (option 2b), caught
   once at the controller.

3. **Test-side `dedup.*` properties.** `src/test/resources/application.properties` fully shadows
   the production one on the classpath; added a comment explaining this.

## Changes made

### Part 1 — `maxRecords` field initializer
`DeduplicationService.java`: added `= 100000` initializer so hand-built instances get the correct cap. `@Value` still overrides for Spring beans.

### Part 2 — Exception hierarchy (new files)
- `DeduplicationException extends RuntimeException` — base with `getErrorMessage()`
- `RecordCapExceededException extends DeduplicationException` — cap exceeded
- `DuplicateIdsException extends DeduplicationException` — duplicate ID sanity check
- `InvalidRisFileException extends DeduplicationException` — reparented (was `extends RuntimeException`; dropped its own redundant field/method)

### Part 3 — Service throws instead of returning `"ERROR:"`
`DeduplicationService.java`:
- `checkRecordCap` — changed to `void`, throws `RecordCapExceededException`
- Two-file combined cap block — throws `RecordCapExceededException`
- Three `catch (InvalidRisFileException)` blocks removed — exception propagates directly
- `doSanityChecks` — changed to `void`, throws `DuplicateIdsException`
- All `String s = doSanityChecks(...); if (s != null) { ... }` guards removed

### Part 4 — Controller catches at boundary
`DedupEndNoteController.java`: added `catch (ExecutionException e)` in `runDedup` — when the cause is a `DeduplicationException`, sends the message to the WebSocket progress reporter and returns `200 OK ApiResponse` (matching prior behaviour). Other `ExecutionException`s rethrow.

### Part 5 — Tests updated
- `RecordCountCapTests`: two cap-exceeded tests now use `assertThatThrownBy(...).isInstanceOf(RecordCapExceededException.class)`. Two within-cap tests unchanged.
- `DeduplicationServiceTests.deduplicate_withDuplicateIDs`: now uses `assertThatThrownBy(...).isInstanceOf(DuplicateIdsException.class)`.

### Part 6 — Test properties
`src/test/resources/application.properties`: removed the `dedup.max-records = 100000` active line (never helped `new`-constructed instances; redundant with the field initializer for beans). Added comment block explaining the classpath-shadowing situation and the per-test `@SpringBootTest(properties=…)` pattern.

## Verification results

| Step | Command | Result |
|---|---|---|
| Compile | `./mvnw -q -DskipTests test-compile` | ✅ clean (NullAway/Error Prone pass) |
| Unit | `./mvnw test -Punit-tests` | ✅ 584 pass, 9 skipped |
| Integration | `./mvnw test -Pintegration-tests` | ✅ 23 pass, 1 skipped |
| Validation | `./mvnw test -Pvalidation-tests` | ✅ 20 pass (2 ran), 18 skipped — `AuthorExperimentsTests` now green |

`AuthorExperimentsTests.higherAuthorThresholdsReduceSensitivityAndIncreaseSpecificity` now passes, confirming the root cause: the `maxRecords=0` bug was preventing the experiment from running, which caused the "sensitivity == baseline" anomaly reported from the previous task.

## Files modified
- `src/main/java/edu/dedupendnote/services/DeduplicationException.java` (new)
- `src/main/java/edu/dedupendnote/services/RecordCapExceededException.java` (new)
- `src/main/java/edu/dedupendnote/services/DuplicateIdsException.java` (new)
- `src/main/java/edu/dedupendnote/services/InvalidRisFileException.java` (reparented)
- `src/main/java/edu/dedupendnote/services/DeduplicationService.java`
- `src/main/java/edu/dedupendnote/controllers/DedupEndNoteController.java`
- `src/test/java/edu/dedupendnote/integration/RecordCountCapTests.java`
- `src/test/java/edu/dedupendnote/integration/DeduplicationServiceTests.java`
- `src/test/resources/application.properties`

## How to verify
Run the four commands in the table above. All should be fully green.
