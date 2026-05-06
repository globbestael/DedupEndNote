# Plan: create four plan files corresponding to Steps 1, 3, 2, 4 from the .thinking file

**Status: executed** — all four plan files written to `.plans/` on 2026-05-06.

## Context

The user has accepted the layered approach proposed in `.thinking/2026-05-04-test-taxonomy-and-validation.md` and wants to break it into four executable plan files. The recommended sequencing is **1 → 3 → 2 → 4** (taxonomy clarification, then shared postprocessing extraction, then alignment with the production flow, then refactoring of the alternatives test). Step 5 (pluggable services and thresholds) is being deferred — the user will rethink it later.

This plan describes the contents of the four plan files created in `.plans/`. Each will be triggered individually by the user.

## Filename convention

`.plans/` follows `YYYY-MM-DD-HHMM-<slug>.md`, where the date/time normally reflects the *commit* of execution. The four plan files below use today's date (2026-05-06) with sequential placeholder times that preserve the intended execution order. The user can rename via `git mv` at execution time per the existing convention.

Created filenames (in execution order):

1. `2026-05-06-1000-test-taxonomy-clarification.md`
2. `2026-05-06-1010-extract-validation-service.md`
3. `2026-05-06-1020-align-validation-with-production-flow.md`
4. `2026-05-06-1030-refactor-author-experiments-tests.md`

## Plan-file 1 — Test taxonomy clarification

**Slug:** `test-taxonomy-clarification`

**Context section:** explain the three-way split (unit / integration / validation) and why `ValidationTests` is misclassified as integration. Reference `.thinking/2026-05-04-test-taxonomy-and-validation.md`.

**Steps:**
1. Move `ValidationTests.java` from `src/test/java/edu/dedupendnote/integration/services/` to a new `src/test/java/edu/dedupendnote/validation/` package.
2. Inside the validation package, place supporting classes that are only consumed by validation: at present that is just `ValidationTests` itself. Decide where shared dependencies stay:
   - `RecordDBService` (Spring `@Service` consumed by `ValidationTests`) — keep in `integration/services/` for now; revisit in plan 2 if needed.
   - `ValidationResult` (POJO) — keep in `integration/domain/` for now; revisit in plan 2 if needed.
   - `MemoryAppender` (used by `MissedDuplicatesTests` and `ValidationTests`) — keep in `integration/utils/`.
3. Update package declarations and imports in the moved file.
4. Rename test integration files that end in `Test.java` (not `Tests.java`) so the integration profile filter can use only `**/integration/**/*Tests.java`:
   - `integration/TwoFilesTest.java` → `integration/TwoFilesTests.java`
   - Verify no other files in `integration/` end in `Test.java` (only test runner classes; `AbstractIntegrationTest.java` is a base class with no `@Test` methods so it is irrelevant to the include filter, but rename it consistently as well? — propose **leaving the abstract base named `AbstractIntegrationTest`** since it is conventional for abstract bases not to follow `*Tests` naming, and Surefire does not run abstract classes).
5. Update `pom.xml`:
   - Existing `unit-tests` profile: extend the excludes to also exclude `**/validation/**`.
   - Existing `integration-tests` profile: drop `<include>**/integration/**/*Test.java</include>` (no longer needed after the rename), keep `<include>**/integration/**/*Tests.java</include>`.
   - Add a new `validation-tests` profile with `<include>**/validation/**/*Tests.java</include>`.
6. Update `CLAUDE.md`:
   - Add the validation category to the test class hierarchy section.
   - Document the three Maven profiles.
   - Note that validation tests are slow, depend on local truth files, and are intended to be run on demand (not every commit).

**Files modified:** the moved/renamed test files, `pom.xml`, `CLAUDE.md`.

**Verification:**
- `./mvnw clean test-compile` — no unresolved imports.
- `./mvnw -Punit-tests test` — same pass/fail count as before.
- `./mvnw -Pintegration-tests test` — runs only `*Tests.java` under `integration/` (does **not** include `ValidationTests` anymore).
- `./mvnw -Pvalidation-tests test` — runs only `ValidationTests`.

---

## Plan-file 2 — Extract `ValidationService` (shared postprocessing)

**Slug:** `extract-validation-service`

**Context section:** the `checkResults()` method in `ValidationTests` mixes three concerns: (a) running the deduplication, (b) reading the truth file, (c) computing TP/FP/FN/TN and writing reports. Concerns (b) and (c) are reusable by `ValidationOfAlternatives`; extracting them into a dedicated service class makes plan 4 straightforward.

**Steps:**
1. Create a new test-helper class `ValidationService` in `src/test/java/edu/dedupendnote/validation/services/` (Spring `@Service`, picked up by the test component scan because it stays under `edu.dedupendnote.*`).
2. Move into it:
   - `readTruthFile(String fileName)` (currently in `ValidationTests`)
   - `readASySDGoldFile(String asysdInputfileName)` (only used by `createInitialTruthFile`; if we keep that in `ValidationTests`, this can stay there too — flag for the executor to decide)
   - The post-deduplication scoring loop from `checkResults` (the body that fills `tps/fns/tns/fps`, builds `fnPairs`/`fpPairs`, builds `errors`, and constructs the `ValidationResult`).
   - `writeFNandFPresults(...)` (the trace-capture writer)
3. The new method signature on `ValidationService`:
   ```java
   ValidationResult checkResults(
       String setName,
       String inputFileName,
       String outputFileName,
       String truthFileName,
       List<Publication> publications,        // already deduplicated
       long durationMs,
       boolean withTracing
   );
   ```
   Rationale: this method does *not* run the deduplication itself. The caller (whether `ValidationOfProductionTests` or `ValidationOfAlternativesTests`) runs the deduplication with whatever engine it wants and passes the result in. This is the key seam that enables plan 4.
4. `RecordDBService` is still required (the `convertToRecordDB` and `saveRecordDBs` calls). Either:
   - Inject `RecordDBService` into `ValidationService` (preferred), or
   - Pass it as a parameter (rejected — clutters every call).
5. Decide where `RecordDBService` and `ValidationResult` live: since both are now consumed by `ValidationService`, propose moving them under `validation/`:
   - `integration/services/RecordDBService.java` → `validation/services/RecordDBService.java`
   - `integration/domain/ValidationResult.java` → `validation/domain/ValidationResult.java`
   This keeps the validation package self-contained.
6. Refactor `ValidationTests.checkResults(...)` to:
   - Run `deduplicate(inputFileName)` (the existing private helper, unchanged in this plan — it is altered in plan 3).
   - Time the call.
   - Delegate to `validationService.checkResults(...)`.
7. Update CLAUDE.md services table to mention the new test-helper service (or note in the Testing section that it is a test-only Spring `@Service`).

**Files modified:** `ValidationTests.java`, new `ValidationService.java`, possibly the moves of `RecordDBService` and `ValidationResult`, `CLAUDE.md`.

**Verification:**
- `./mvnw -Pvalidation-tests test` produces the same output table as before this plan (TP/FP/FN/TN counts identical, scores identical, FN/FP analysis files written when `withTracing` is true).
- The existing `validationResultsMap` baseline still matches (no false changes).

---

## Plan-file 3 — Align ValidationOfProduction with the production flow

**Slug:** `align-validation-with-production-flow`

**Context section:** the current private `deduplicate(String)` helper in `ValidationTests` calls `readPublications` + `doSanityChecks` + `searchYearOneFile`. The production path through `deduplicateOneFile` adds enrichment and writes the output. Per the user's preference (option *b* in `.thinking/2026-05-04-test-taxonomy-and-validation.md`): run `deduplicateOneFile` in mark mode, then re-read the marked output file. This exercises the exact production code path. The validation file then needs to read the `LB` field from the marked RIS — which production `readPublications` deliberately skips so that mark-mode output never carries a stale label. So a parameter must be added to `readPublications` to opt into reading `LB`, used **only** by validation.

**Steps:**
1. Add an opt-in `LB` reader to `IOService`. Choose one of:
   - **(preferred) Overload**: keep `readPublications(String, Consumer<String>)` unchanged (defaults to `includeLabelField=false`); add `readPublications(String, Consumer<String>, boolean includeLabelField)` and have the existing method delegate. Add a comment block above both methods explaining:
     - why `LB` is normally skipped (the documented overwrite contract for mark mode);
     - that the three-arg variant is intended only for validation tests reading mark-mode output;
     - that no production caller should pass `true`.
   - **(rejected) Default-parameter pattern with a wrapper method on a different class**: more indirection for no benefit.
2. In the body of the three-arg variant, add a new `case "LB":` branch in the field switch that calls `publication.setLabel(fieldContent)`. Continuation lines for `LB` are not expected — keep the implementation minimal and document the assumption.
3. In `ValidationTests.deduplicate(...)` (or its successor after plan 2), replace the body with:
   ```
   String markFileName = inputFileName + "_mark.txt";
   deduplicationService.deduplicateOneFile(inputFileName, markFileName, /*markMode=*/ true, message -> {});
   List<Publication> publications = ioService.readPublications(markFileName, message -> {}, /*includeLabelField=*/ true);
   return publications;
   ```
   Add a comment exactly as suggested by the user:
   > Run `deduplicateOneFile` in mark mode and read the marked output. This closes the gap between validation and production: validation now exercises the exact code path the production deployment runs, instead of mimicking it.
4. Re-think the `dedupid` extraction in the existing scoring code (in `ValidationService.checkResults` after plan 2; or still in `ValidationTests` if plan 3 is executed before plan 2). Currently `dedupid` is taken from `Publication.label` via `RecordDBService.convertToRecordDB` (`recordIdMap.get(...).getLabel()`). With the new flow the `Publication` object has `label` set directly (from the LB field), so `convertToRecordDB` continues to work unchanged.
5. Verify the timing measurement: previously `endTime - startTime` measured only the comparison phase. With the new flow it now measures readPublications + sanityChecks + searchYearOneFile + enrichment + writeMarkedPublications + readPublications-with-LB. This will increase reported durations; bump the `validationResultsMap` baseline durations in the same commit (sensitivity/specificity stay identical, only `duration` shifts).
6. Add a small note to CLAUDE.md's Testing section explaining the validation flow: "validation runs the production `deduplicateOneFile` in mark mode and re-reads the output (with `includeLabelField=true`)".

**Files modified:** `IOService.java` (new overload), `ValidationTests.java` (or `ValidationService.java` after plan 2) — the deduplicate helper, `validationResultsMap` baseline durations, `CLAUDE.md`.

**Verification:**
- `./mvnw -Pvalidation-tests test` — TP/FP/FN/TN identical to baseline; durations updated to new (higher) values; no new `_mark.txt` files left in unexpected locations.
- The new `_mark.txt` files appear next to each input and contain the expected number of records and a non-empty `LB` field for duplicates.
- Spot-check: open a `_mark.txt` and confirm the `LB` field is correctly read back into `Publication.label` by enabling debug logging on a single dataset.
- The single-arg `readPublications` is unchanged and `MissedDuplicatesTests`/`DedupEndNoteApplicationTests`/`TwoFilesTests` still pass.

---

## Plan-file 4 — Refactor `AuthorExperimentsTests` to use shared validation infrastructure

**Slug:** `refactor-author-experiments-tests`

**Context section:** `AuthorExperimentsTests.higherAuthorSimilarityFindsLessDuplicates` currently deduplicates the same input file twice (once with the production engine, once with the experimental one) and only asserts on raw duplicate counts. The refactor replaces this with: (a) take the production baseline from `validationResultsMap` (already authoritative), (b) run only the experimental engine, (c) report sensitivity/specificity via `ValidationService` so the comparison is meaningful.

**Steps:**
1. Move `AuthorExperimentsTests` from `integration/services/` to `validation/` (or `validation/alternatives/`). Discuss the placement with the user during execution; default to `validation/alternatives/AuthorExperimentsTests.java`.
2. Rename the inner class `ExperimentalAuthorsComparator` to `ExperimentalAuthorsComparisonService` and extract it to a separate top-level class `validation/alternatives/ExperimentalAuthorsComparisonService.java`. (The previous `.thinking` file already proposed this.)
3. Replace the test body:
   - Remove the call to `service.deduplicateOneFile(...)` for the production engine.
   - Look up the production baseline from `validationResultsMap` for the dataset under test (start with the `experiments/t1.txt` dataset; if it is not in `validationResultsMap`, add a `ValidationResult` entry with the known counts — `4 deduplicated, 1 kept` translates to a known TP/FP/FN/TN once the truth file exists).
   - Run only the experimental engine through `validationService.checkResults(...)` with an `expService` configured with `ExperimentalAuthorsComparisonService`.
   - Assert that the experimental engine has **higher specificity** (fewer false positives) and **lower sensitivity** (more false negatives) than the baseline — making the experiment's hypothesis explicit instead of just counting duplicates.
4. If the `experiments/t1.txt` dataset does not yet have a truth file, the test must either (a) create one as part of this plan, or (b) switch to a dataset that does. Default: switch to a dataset already in `validationResultsMap` (e.g. `Clinical_trials` is small and has truth data).
5. Update `CLAUDE.md` to mention the alternatives sub-package under validation.

**Files modified:** `AuthorExperimentsTests.java` (moved + body rewritten), new `ExperimentalAuthorsComparisonService.java`, possibly `validationResultsMap`, `CLAUDE.md`.

**Verification:**
- `./mvnw -Pvalidation-tests test` — `AuthorExperimentsTests` runs once (no double-dedup), reports sensitivity/specificity, and the assertions on relative scores hold.
- Production baseline from `validationResultsMap` is read, not recomputed.

---

## Out of scope for all four plans

- Pluggable thresholds in `Default*ComparisonService` (Step 5 — deferred at the user's request).
- Extracting `Title`/`Journal`/`Pages` comparison-service interfaces (also Step 5).
- Web admin endpoint for triggering validation runs (Step 6 in the `.thinking` file — explicitly punted there).
- Multi-field combination experiments.
