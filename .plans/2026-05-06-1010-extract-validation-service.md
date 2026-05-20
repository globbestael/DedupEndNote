# Extract ValidationService — shared postprocessing for production and alternative validation

**Status: executed**

## Context

`ValidationTests.checkResults()` currently mixes three concerns:
1. Running the deduplication (calling the private `deduplicate()` helper).
2. Reading the truth file and scoring the results (TP/FP/FN/TN computation).
3. Writing the analysis files and reporting.

Concerns 2 and 3 are identical for both `ValidationOfProduction` (current `ValidationTests`) and the planned `ValidationOfAlternatives` (future tests that inject alternative comparison services or normalizations). Extracting them into a shared `ValidationService` class removes this duplication and creates the seam that makes alternative-engine tests straightforward to write.

This plan must be executed **after** `2026-05-06-1000-test-taxonomy-clarification.md`, which creates the `validation/` package.

## Steps

### 1. Move RecordDBService and ValidationResult under validation/

These classes are consumed only by validation concerns. Move them now so the `validation/` package is self-contained:

- `src/test/java/edu/dedupendnote/integration/services/RecordDBService.java` → `src/test/java/edu/dedupendnote/validation/services/RecordDBService.java`; update package to `edu.dedupendnote.validation.services`.
- `src/test/java/edu/dedupendnote/integration/domain/ValidationResult.java` → `src/test/java/edu/dedupendnote/validation/domain/ValidationResult.java`; update package to `edu.dedupendnote.validation.domain`.

Update the import in `ValidationTests.java` for both.

### 2. Create ValidationService

Create `src/test/java/edu/dedupendnote/validation/services/ValidationService.java`.

```java
package edu.dedupendnote.validation.services;

@Slf4j
@Service
public class ValidationService {

    @Autowired
    private RecordDBService recordDBService;

    // ... methods moved from ValidationTests
}
```

The class is annotated `@Service` so it is picked up by the Spring `@SpringBootTest` component scan (all test classes under `edu.dedupendnote.*` are scanned because `DedupEndNoteApplication` is in the same root).

### 3. Move scoring and reporting logic into ValidationService

Move the following from `ValidationTests` into `ValidationService`:

- `readTruthFile(String fileName)` — reads the tab-delimited truth file via Jackson CSV. Keep imports for `CsvMapper`, `CsvSchema`, `PublicationDB`.
- The scoring loop (the body of `checkResults` that computes `tps/fns/tns/fps`, builds `fnPairs`/`fpPairs`, populates `errors`/`fpErrors`, and constructs the `ValidationResult`).
- `writeFNandFPresults(Map<Integer, List<List<Publication>>> pairsList, String outputFileName)` — the trace-capture writer.

The new public method signature on `ValidationService`:

```java
public ValidationResult checkResults(
    String setName,
    String inputFileName,
    String outputFileName,
    String truthFileName,
    List<Publication> publications,   // already deduplicated, label field set
    long durationMs,
    boolean withTracing,
    DeduplicationService deduplicationService   // needed by writeFNandFPresults for compareSet
);
```

Rationale for `List<Publication> publications` as a parameter: the caller runs the deduplication with whatever engine it chooses and passes the result here. This is the seam that allows alternative engines in plan 4.

### 4. Keep readASySDGoldFile in ValidationTests

`readASySDGoldFile` is only used by `createInitialTruthFile` (a `@Disabled` utility test). Keep it in `ValidationTests` to avoid moving code that is not relevant to the shared concern.

### 5. Refactor ValidationTests.checkResults

Replace the current body with:

```java
ValidationResult checkResults(String setName, String inputFileName, String outputFileName, String truthFileName)
        throws IOException {
    log.error("- Validating {}", setName);
    long startTime = System.currentTimeMillis();
    List<Publication> publications = deduplicate(inputFileName);    // unchanged in this plan
    long durationMs = System.currentTimeMillis() - startTime;

    return validationService.checkResults(
        setName, inputFileName, outputFileName, truthFileName,
        publications, durationMs, withTracing, deduplicationService);
}
```

Inject `ValidationService` into `ValidationTests`:

```java
@Autowired
ValidationService validationService;
```

### 6. Update CLAUDE.md

Add a note in the Testing section that `ValidationService` is a test-only Spring `@Service` in `validation/services/`, shared between `ValidationTests` and future alternative-validation tests.

## Files modified

- `src/test/java/edu/dedupendnote/integration/services/RecordDBService.java` → moved to `validation/services/`
- `src/test/java/edu/dedupendnote/integration/domain/ValidationResult.java` → moved to `validation/domain/`
- New `src/test/java/edu/dedupendnote/validation/services/ValidationService.java`
- `src/test/java/edu/dedupendnote/validation/ValidationTests.java` — `checkResults` refactored; `@Autowired ValidationService` added; moved methods removed
- `CLAUDE.md` — Testing section updated

## Verification

1. `./mvnw clean test-compile` — no unresolved imports.
2. `./mvnw -Pvalidation-tests test` — `checkAllTruthFiles` produces an identical output table (TP/FP/FN/TN counts and scores unchanged). The `validationResultsMap` baseline still matches (`assertThat(changed).isFalse()` passes — or stays at `.isTrue()` if it was temporarily set that way for the Roo Code refactoring).
3. FN/FP analysis files are written when `withTracing = true` (spot-check one dataset).
4. `./mvnw -Pintegration-tests test` — unaffected; `RecordDBService` is no longer in the integration package and is not needed there (it was only used by `ValidationTests`).
