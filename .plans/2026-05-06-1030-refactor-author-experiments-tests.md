# Refactor AuthorExperimentsTests to use shared validation infrastructure

**Status: executed (used SRA2_Haematology — has TN=1186, FP=1, making specificity improvement visible)**

**Note:** The `validation/alternatives/` package referenced in this plan was renamed to `validation/experiments/` by `2026-05-06-1100-rename-alternatives-to-experiments.md`.

## Context

`AuthorExperimentsTests.higherAuthorSimilarityFindsLessDuplicates` currently deduplicates the same file **twice** — once with the production engine, once with an experimental engine — and asserts only on raw duplicate counts. This has two weaknesses:

1. The production baseline is recomputed rather than read from the authoritative `validationResultsMap`, making the test slower and redundant.
2. The assertion (`resultString != expResultString`) confirms that the counts differ but does not measure *how* the performance changes. Sensitivity/specificity are the meaningful metrics.

This plan refactors the test to: read the production baseline from `validationResultsMap`, run only the experimental engine through `ValidationService.checkResults`, and assert on relative sensitivity/specificity. It also renames the experimental inner class and extracts it to a top-level file, and moves the test into the `validation/` package.

This plan must be executed **after** `2026-05-06-1010-extract-validation-service.md` (ValidationService must exist).

## Steps

### 1. Choose a dataset with truth files

The current test uses `experiments/t1.txt`. This file is small (4 records) and has no truth file in `validationResultsMap`. Two options:

- **(a) Create a truth file for `experiments/t1.txt`**: deduplicate it, validate all 4 records manually, create a `t1_TRUTH.txt` file, add a `ValidationResult` entry with the correct TP/FP/FN/TN counts (1 TP, 0 FN, 3 TN, 0 FP given `4 deduplicated → 1 kept`).
- **(b) Switch to an existing validated dataset**: use `Clinical_trials` (219 records — the smallest validated dataset in `validationResultsMap`).

The executor should prefer **(a)** if a truth file can be created easily from the existing `t1.txt` (4 records = trivial to validate). Use **(b)** if not.

### 2. Extract ExperimentalAuthorsComparisonService to a top-level class

Create `src/test/java/edu/dedupendnote/validation/alternatives/ExperimentalAuthorsComparisonService.java`:

```java
package edu.dedupendnote.validation.alternatives;

public class ExperimentalAuthorsComparisonService implements AuthorsComparisonService {

    public static final Double AUTHOR_SIMILARITY_NO_REPLY = 0.67 + 0.5;
    public static final Double AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS = 0.75 + 0.2;
    public static final Double AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS = 0.80 + 0.2;

    // ... same body as the current inner class ExperimentalAuthorsComparator
}
```

Remove the inner class `ExperimentalAuthorsComparator` from `AuthorExperimentsTests`.

### 3. Move AuthorExperimentsTests to the validation package

Move from `src/test/java/edu/dedupendnote/integration/services/AuthorExperimentsTests.java` to `src/test/java/edu/dedupendnote/validation/alternatives/AuthorExperimentsTests.java`.

Update package declaration:
```java
package edu.dedupendnote.validation.alternatives;
```

The class still extends `AbstractIntegrationTest` (which lives in `edu.dedupendnote.integration`) — update the import. The `@SpringBootTest` context is still needed because the experimental `DeduplicationService` is instantiated manually, but `ioService` and the truth-reading infrastructure are injected.

### 4. Replace the test body

```java
@Autowired
ValidationService validationService;

@Autowired
IOService ioService;

@Test
void higherAuthorThresholdsReduceSensitivityAndIncreaseSpecificity() throws IOException {
    String setName     = "...";         // name matching an entry in validationResultsMap
    String subdir      = testDir + "/...";
    String inputFile   = subdir + "...";
    String outputFile  = subdir + "..._to_validate.txt";
    String truthFile   = subdir + "..._TRUTH.txt";

    // Production baseline — read from the authoritative map, do NOT recompute
    ValidationResult baseline = validationResultsMap.get(setName);
    assertThat(baseline).isNotNull();

    // Experimental engine — higher thresholds, same normalization
    DeduplicationService expService = new DeduplicationService(new ComparisonService());
    expService.setAuthorsComparisonService(new ExperimentalAuthorsComparisonService());

    long start = System.currentTimeMillis();
    List<Publication> publications = ... // run experimental dedup in mark mode, re-read with LB
                                        // or call expService.deduplicateOneFile in mark mode
                                        // then ioService.readPublications(..., true)
    long duration = System.currentTimeMillis() - start;

    ValidationResult expResult = validationService.checkResults(
        setName + "_experimental", inputFile, outputFile, truthFile,
        publications, duration, /* withTracing= */ false, expService);

    // The hypothesis: higher author thresholds miss more duplicates (lower sensitivity)
    // but produce fewer false positives (higher specificity).
    assertThat(expResult.getSensitivity()).isLessThan(baseline.getSensitivity());
    assertThat(expResult.getSpecificity()).isGreaterThanOrEqualTo(baseline.getSpecificity());

    // Print both results for comparison
    System.err.println("Baseline:    " + baseline);
    System.err.println("Experiment:  " + expResult);
}
```

Remove the old field `AuthorsComparisonService authorsComparisonService = new ExperimentalAuthorsComparator()` and the `@BeforeEach expService` setup.

### 5. Update CLAUDE.md

- Add `validation/alternatives/` to the test class hierarchy section.
- Note that `ExperimentalAuthorsComparisonService` is the first non-production implementation of `AuthorsComparisonService`.

## Files modified

- `src/test/java/edu/dedupendnote/integration/services/AuthorExperimentsTests.java` → moved to `validation/alternatives/AuthorExperimentsTests.java`; class body rewritten
- New `src/test/java/edu/dedupendnote/validation/alternatives/ExperimentalAuthorsComparisonService.java`
- Possibly a new truth file for `experiments/t1.txt` (if option *a* is chosen in step 1), and a corresponding `ValidationResult` entry in `validationResultsMap`
- `CLAUDE.md`

## Verification

1. `./mvnw clean test-compile` — no errors.
2. `./mvnw -Pvalidation-tests test` — `AuthorExperimentsTests` runs once (no double deduplication), the sensitivity/specificity assertions pass, and both result rows are printed to stderr for comparison.
3. `./mvnw -Pintegration-tests test` — unaffected; `AuthorExperimentsTests` is no longer in the integration package.
4. The printed output clearly shows the expected trade-off: experimental sensitivity ≤ baseline sensitivity; experimental specificity ≥ baseline specificity.
