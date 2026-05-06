# Align ValidationOfProduction with the production flow

**Status: executed (duration baselines in validationResultsMap not yet updated — requires a validation test run)**

## Context

The private `deduplicate()` helper in `ValidationTests` currently calls `ioService.readPublications()` + `deduplicationService.doSanityChecks()` + `deduplicationService.searchYearOneFile()`. The production path through `deduplicateOneFile` adds two more steps: enrichment (copying missing fields from duplicate records to kept records) and writing the output file. If either of these steps were ever to have a side effect on which records are marked as duplicates, the validation test would not catch it.

The user prefers closing this gap by running `deduplicateOneFile` in mark mode and re-reading the marked output (option *b* from `.thinking/2026-05-04-test-taxonomy-and-validation.md`). This exercises the exact production code path without requiring `DeduplicationService` to expose internals.

The challenge: `readPublications` deliberately skips the `LB` (Label) field so that stale mark-mode labels in a user's input file are never carried forward into the output (this is documented user-facing behaviour). Re-reading a mark-mode output file for validation purposes requires reading `LB`. A new overload of `readPublications` that opts into reading `LB` solves this cleanly — the two-arg signature remains unchanged for all production callers.

This plan must be executed **after** `2026-05-06-1010-extract-validation-service.md`.

## Steps

### 1. Add a readPublications overload that includes the Label field

In `src/main/java/edu/dedupendnote/services/IOService.java`:

Keep the existing public method unchanged:
```java
public List<Publication> readPublications(String inputFileName, Consumer<String> progressReporter) {
    return readPublications(inputFileName, progressReporter, false);
}
```

Add the new overload:
```java
/**
 * Reads a RIS file into Publication objects, optionally reading the LB (Label) field.
 *
 * <p>The Label field is normally skipped during reading. Mark mode writes the ID of the
 * kept record into the LB field of every duplicate; this deliberately overwrites any LB
 * content from the user's original file, which is documented behaviour. To avoid carrying
 * a stale label from a previously marked file into a new deduplication run, the two-arg
 * {@link #readPublications(String, Consumer)} always passes {@code includeLabelField=false}.
 *
 * <p>Pass {@code includeLabelField=true} ONLY when reading a mark-mode output file for
 * validation purposes (ValidationTests / ValidationService). No production caller should
 * pass {@code true}.
 */
public List<Publication> readPublications(String inputFileName, Consumer<String> progressReporter,
        boolean includeLabelField) {
    // ... same body as the current readPublications, with the following added inside the switch:
    //
    // case "LB":
    //     if (includeLabelField) {
    //         publication.setLabel(fieldContent);
    //     }
    //     break;
}
```

The `case "LB"` branch is minimal. Continuation lines for `LB` are not expected (the label is a single short ID string) — document this assumption in a comment.

Implementation note: the simplest approach is to copy the full `readPublications` body into the new overload and add the `LB` case. Alternatively, extract the switch body into a private method that takes `includeLabelField` as a parameter — choose whichever the executor finds cleaner; both are correct.

### 2. Replace the deduplicate() helper in ValidationTests (or ValidationService)

After plan 2 (extract-validation-service), the deduplication call lives in `ValidationTests.checkResults`. Replace the private `deduplicate(String inputFileName)` helper with the mark-mode flow:

```java
private List<Publication> deduplicate(String inputFileName) {
    /*
     * Run deduplicateOneFile in mark mode and read the marked output.
     * This closes the gap between validation and production: validation now exercises
     * the exact code path the production deployment runs, instead of mimicking it.
     */
    String markFileName = inputFileName + "_mark.txt";
    deduplicationService.deduplicateOneFile(inputFileName, markFileName, /* markMode= */ true, message -> {});
    return ioService.readPublications(markFileName, message -> {}, /* includeLabelField= */ true);
}
```

`IOService` must be injected into `ValidationTests` (it is already `@Autowired` there).

### 3. Verify that label-to-dedupid mapping still works

`RecordDBService.convertToRecordDB` reads `publication.getLabel()` and converts it to `PublicationDB.dedupid`. With the new flow, `label` is set from the `LB` field of the mark-mode output — exactly as it was set by `searchYearOneFile`. The downstream scoring in `ValidationService.checkResults` is unchanged.

### 4. Update timing measurements and validationResultsMap baselines

The new `deduplicate()` helper measures time across: `readPublications` + `doSanityChecks` + `searchYearOneFile` + `enrich` + `writeMarkedPublications` + `readPublications` (with LB). This will increase reported durations compared to the previous helper which measured only `readPublications` + `doSanityChecks` + `searchYearOneFile`.

After running `checkAllTruthFiles` once with the new code, update the `duration` parameter in every `ValidationResult` entry in `validationResultsMap`. TP/FP/FN/TN counts and all derived scores (sensitivity, specificity, etc.) must remain identical.

### 5. Update CLAUDE.md

Add a note in the Testing → Validation section:
> Validation runs `deduplicateOneFile` in mark mode to exercise the full production code path, then re-reads the mark-mode output with `includeLabelField=true` to extract deduplication groups.

## Files modified

- `src/main/java/edu/dedupendnote/services/IOService.java` — new three-arg `readPublications` overload; existing two-arg method delegates to it; `LB` case added in the new overload only
- `src/test/java/edu/dedupendnote/validation/ValidationTests.java` — `deduplicate()` helper replaced with mark-mode flow
- `src/test/java/edu/dedupendnote/validation/ValidationTests.java` — `validationResultsMap` duration values updated
- `CLAUDE.md` — Testing section updated

## Verification

1. `./mvnw clean test-compile` — no errors; NullAway is satisfied (the new `@Nullable`-annotated overload follows the same patterns as the existing method).
2. `./mvnw -Pvalidation-tests test` — TP/FP/FN/TN identical to baseline for all 14 datasets; only duration values differ (updated in the same commit).
3. Spot-check: confirm that `_mark.txt` files are written next to each input file, that they contain an `LB` field for duplicate records, and that `readPublications(..., true)` correctly sets `Publication.label` on those records (enable debug logging on one small dataset).
4. `./mvnw -Pintegration-tests test` — unchanged; the two-arg `readPublications` delegation call is transparent to all production paths.
5. `./mvnw -Punit-tests test` — unchanged; `IOService` unit tests (if any) still compile and pass.
