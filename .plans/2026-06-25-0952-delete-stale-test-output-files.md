# Delete stale test output files before each writing test

## Context

Several integration and validation tests write output files **next to their input files** in the
persistent directory `~/dedupendnote_input_files/...` (not a temp dir). Output paths are derived from
the input via `UtilitiesService.createPath(inputPath, suffix, "txt")`, e.g. `Haematology.txt` →
`Haematology_mark.txt`, `Haematology_to_validate.txt`, `Haematology_deduplicated.txt`.

The writers use `Files.newBufferedWriter` (truncate-on-open), so a **successful** run overwrites cleanly.
The problem is a **failed** run: if a test errors while reading the input or normalizing a field (before
the write happens), the output file from a *previous* successful run remains on disk. A developer
inspecting that file later can mistake stale content for the current run's result.

There was almost no cleanup: only `ValidationService.checkResults` deleted its two analysis files
(`_FN_Analysis`, `_FP_Analysis`), and only inside the `withTracing` branch. No `initTestDir()`
created or cleaned directories — they only re-pointed the `testDir` field.

**Goal:** guarantee that each writing test's output files reflect *this* run or do not exist at all.
Scope is **test-side only**; production `DeduplicationService` is left untouched.

## Changes made

### `src/test/java/edu/dedupendnote/integration/AbstractIntegrationTest.java`

Added `deleteDerivedOutputs(Path inputPath)` — iterates 7 generated-output suffixes
(`_deduplicated`, `_mark`, `_markDB`, `_to_validate`, `_experimental_to_validate`,
`_FN_Analysis`, `_FP_Analysis`) and calls `Files.deleteIfExists` for each derived path.
Input/truth file suffixes (`_TRUTH`, `_for_truth`, `_asysd_gold`, `_with_TRUTH`) are
deliberately excluded from the list. Uses `UtilitiesService.createPath` — the same helper
the tests use to locate outputs — and follows the `Files.deleteIfExists` precedent already
in `ValidationService.java:146-147`.

### Call sites — `deleteDerivedOutputs(inputPath)` added as first action after the input path is known

| File | Method(s) |
|---|---|
| `integration/DeduplicationServiceTests.java` | `deduplicate_OK`, `deduplicateSmallFiles`, `deduplicate_withDuplicateIDs` |
| `integration/DeduplicationServiceTests.java` | `deduplicateTwoFiles_OK` — passes `newInputPath` (output is derived from the new-file input, per `DeduplicationService` line 292) |
| `integration/MissedDuplicatesTests.java` | `deduplicateMissedDuplicates` |
| `validation/ValidationTests.java` | `deduplicate(Path inputPath)` private helper — single edit covers all 14 datasets and all downstream files |
| `validation/experiments/AuthorExperimentsTests.java` | `higherAuthorThresholdsReduceSensitivityAndIncreaseSpecificity` |

Including the throwing test `deduplicate_withDuplicateIDs` is intentional: it throws before writing, so
the pre-delete is exactly what prevents a stale `_deduplicated.txt` from surviving a failed run.

### `CLAUDE.md`

Updated `AbstractIntegrationTest` description to document `deleteDerivedOutputs` and the convention
that writing tests must call it.

### No ADR

This is a test-infrastructure correctness fix with no meaningful alternative — not an architectural
decision warranting an ADR.

## Verification

Integration tests: `./mvnw test -Pintegration-tests` — 23 tests, 0 failures ✓

Manual stale-file check:
1. Create `~/dedupendnote_input_files/integration/other/File_with_duplicate_IDs_deduplicated.txt` with content `STALE`.
2. Run `DeduplicationServiceTests#deduplicate_withDuplicateIDs` (throws `DuplicateIdsException` before writing).
3. Confirm the file is gone — helper deleted it; the failing run never recreated it.
