# Rename Similarity*Test and JWSimilarity*Test files

## Context

The unit test naming convention used generic `Similarity*Test` and `JWSimilarity*Test` prefixes
that did not identify which production service was under test. The new scheme aligns test-class
names with the service they exercise:

- Tests that assert on the **boolean return value** of `Default*ComparisonService.compare()` or
  equivalent static helpers (e.g. `compareIssns`) → renamed to `Default*ComparisonServiceTest`.
- Tests that assert on a **raw JWS `Double`** score, or that do not call `compare()` at all →
  kept with a `JWSimilarity*Test` or `Similarity*Test` name.

`JournalsBaseTest` was the only class that extended `BaseTest` but had both a test method
(`fnCompareJournalsTest`) and utilities that belong logically with the journal `compare()` tests.
Nothing extended `JournalsBaseTest`, so it was folded into `DefaultJournalComparisonServiceTest`
and deleted.

## Files created

| New file | Derived from |
|---|---|
| `DefaultJournalComparisonServiceIssnTest` | `SimilarityIssnTest` |
| `DefaultJournalComparisonServiceTest` | `SimilarityJournalTest` + `JournalsBaseTest` |
| `DefaultTitleComparisonServiceTest` | `SimilarityTitleTest` |

## Files deleted

- `SimilarityIssnTest.java`
- `SimilarityJournalTest.java`
- `SimilarityTitleTest.java`
- `JournalsBaseTest.java`

## Files modified

| File | Change |
|---|---|
| `JWSimilarityJournalTest.java` | Added `// TODO:` companion comment pointing to `DefaultJournalComparisonServiceTest` |
| `JWSimilarityTitleTest.java` | Added `// TODO:` companion comment pointing to `DefaultTitleComparisonServiceTest` |
| `CLAUDE.md` | Updated test class hierarchy (removed `JournalsBaseTest`, added `DefaultJournalComparisonServiceTest`; updated `JWSimilarityTitleTest` description: `IOService` → `BibliographicItemReader`); updated standalone class list; updated taxonomy sentence (Similarity → Comparison) |

## Comments added

- `DefaultJournalComparisonServiceIssnTest`: `// TODO: compareIssns() is a static helper on DefaultJournalComparisonService; see DefaultJournalComparisonServiceTest for the companion compare() tests.`
- `DefaultJournalComparisonServiceTest`: `// TODO: compareIssns() tests on DefaultJournalComparisonService are in DefaultJournalComparisonServiceIssnTest.`; separator comment before `fnCompareJournalsTest` explaining it was originally in `JournalsBaseTest` and tests against real validated data from a file.
- `JWSimilarityJournalTest`: `// TODO: For boolean compare() tests on DefaultJournalComparisonService, see DefaultJournalComparisonServiceTest.`
- `JWSimilarityTitleTest`: `// TODO: For boolean compare() tests on DefaultTitleComparisonService, see DefaultTitleComparisonServiceTest.`

## Verification

```
./mvnw test -Punit-tests
```
