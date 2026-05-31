# Internalize NormPatterns into per-domain normalization services (candidate #5)

## Context

`NormPatterns` was a 374-line grab-bag of 58 static fields (Pattern and List<String>) used by five
normalization services. Each pattern was owned by exactly one service except
`MULTIPLE_WHITE_SPACE_PATTERN` (shared by Titles and Journals). Keeping them centralized created
shallow indirection: callers had to know `NormPatterns` to understand any normalization service, and
there was no locality — a bug in a journal pattern required looking in two unrelated files.

Design decisions made during grilling:
- Move each pattern into the single service that owns it, ordered alphabetically within the service.
- `MULTIPLE_WHITE_SPACE_PATTERN` (shared) is duplicated as a `private static final` in both
  `TitlesNormalizationService` and `JournalsNormalizationService` (trivial 4-char regex `\\s{2,}`).
- Patterns used only internally → `private static final`.
- Two patterns stay `public static final` because test code references them by class name:
  - `UNUSUAL_WHITESPACE_PATTERN` on `NormalizationService` (referenced by `NormalizationServiceTextTest`)
  - `BALANCED_BRACES_PATTERN` on `TitlesNormalizationService` (referenced by `JWSimilarityTitleTest`)
- `NormPatterns.java` is deleted.

## Files deleted

- `src/main/java/edu/dedupendnote/domain/NormPatterns.java`

## Files modified

| File | Change |
|---|---|
| `services/NormalizationService.java` | Add 5 patterns (DOI, ISSN_ISBN, NON_BASIC_LATIN, PUBLICATION_YEAR private; UNUSUAL_WHITESPACE public); remove `NormPatterns` import; add `Pattern` import |
| `services/AuthorsNormalizationService.java` | Add 3 patterns (ANONYMOUS_OR_GROUPNAME, EXCEPT_CAPITALS, LAST_NAME_ADDITIONS all private); remove `NormPatterns` import; add `Pattern` import |
| `services/TitlesNormalizationService.java` | Add 23 patterns alphabetically (BALANCED_BRACES public, rest private); remove `NormPatterns` import; add `Pattern` import |
| `services/JournalsNormalizationService.java` | Add 24 patterns alphabetically (all private); remove `NormPatterns` import; add `Pattern` import |
| `services/PagesNormalizationService.java` | Add 4 patterns (PAGES_ADDITIONS, PAGES_HYPHEN_MERGE_1, PAGES_HYPHEN_MERGE_2, PAGES_MONTH all private); remove `NormPatterns` import; add `Pattern` import |
| `unit/services/JWSimilarityTitleTest.java` | `NormPatterns.BALANCED_BRACES_PATTERN` → `TitlesNormalizationService.BALANCED_BRACES_PATTERN`; import updated |
| `unit/services/NormalizationServiceTextTest.java` | `NormPatterns.UNUSUAL_WHITESPACE_PATTERN` → `NormalizationService.UNUSUAL_WHITESPACE_PATTERN`; `NormPatterns` import removed |
| `CLAUDE.md` | Domain package: remove `NormPatterns`; services table: update `NormalizationService` line count and description; add rows for 4 per-domain normalization services |

## Verification

```
./mvnw test -Punit-tests        # 544 tests, 0 failures
./mvnw test -Pintegration-tests # 18 tests, 0 failures
```
