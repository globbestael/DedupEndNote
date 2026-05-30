# Delete the ComparisonService pass-through (candidate #4)

## Context

`ComparisonService` was a shallow wrapper: four instance methods each delegating to an injected service in one line, plus two static helpers (`compareIssns`, `compareSameDois`) that did not use the injected services. The deletion test confirmed it was a pass-through — deleting it moved no complexity, just removed indirection.

Design decisions made during grilling:
- Replace with `record FieldComparators(authors, titles, journals, pages)` — plain record, no Spring annotations. A `@Bean` factory in `DedupEndNoteApplication` wires in the four `Default*` implementations, mirroring what `ComparisonService`'s no-arg constructor did.
- `compareSameDois` moves to `DefaultPagesComparisonService` (reads the DOI flag set by pages comparison).
- `compareIssns` moves to `DefaultJournalComparisonService` (ISSN matching is part of the journal step).
- `DeduplicationService` now holds `FieldComparators fieldComparators` and calls `fieldComparators.pages().compare(...)` etc.
- `AuthorExperimentsTests` constructs `new FieldComparators(...)` to substitute a custom author service — same pattern as before, just using the record.

`ComparisonServiceTest` was also deleted in this change: its three test methods had already drifted to call the `Default*` services directly rather than `ComparisonService`. Cases were redistributed:
- `compareJournals` cases → absorbed into `SimilarityJournalTest` (positive/negative providers)
- `compareTitles` cases → new `SimilarityTitleTest`
- `compareIssns` cases → new `SimilarityIssnTest` (tests `DefaultJournalComparisonService.compareIssns`)

A TODO comment in `SimilarityJournalTest` (referenced by the other two new files) records a possible future rename of all `Similarity*Test` and `JWSimilarity*Test` files to a `Default*ComparisonService*Test` scheme.

## Files created

- `src/main/java/edu/dedupendnote/services/FieldComparators.java`
- `src/test/java/edu/dedupendnote/unit/services/SimilarityTitleTest.java`
- `src/test/java/edu/dedupendnote/unit/services/SimilarityIssnTest.java`

## Files deleted

- `src/main/java/edu/dedupendnote/services/ComparisonService.java`
- `src/test/java/edu/dedupendnote/unit/services/ComparisonServiceTest.java`

## Files modified

| File | Change |
|---|---|
| `DedupEndNoteApplication.java` | Add `@Bean FieldComparators fieldComparators()` |
| `services/DefaultJournalComparisonService.java` | Add static `compareIssns` |
| `services/DefaultPagesComparisonService.java` | Add static `compareSameDois` |
| `services/DeduplicationService.java` | Replace `ComparisonService` with `FieldComparators`; update constructor and 6 call sites in `compareSet()` |
| `validation/experiments/AuthorExperimentsTests.java` | `new ComparisonService(...)` → `new FieldComparators(...)` |
| `integration/MissedDuplicatesTests.java` | Remove stale `ComparisonService` logger name |
| `validation/services/ValidationService.java` | Remove stale `ComparisonService` logger name |
| `unit/services/SimilarityJournalTest.java` | Add TODO renaming comment; absorb 6 journal cases |
| `CLAUDE.md` | Services table updated; test class list updated |

## Verification

```
./mvnw test -Punit-tests        # 544 tests, 0 failures
./mvnw test -Pintegration-tests # 18 tests, 0 failures
```
