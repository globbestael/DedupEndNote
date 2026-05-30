# Split IOService into BibliographicItemReader and BibliographicItemWriter

## Context

IOService (~980 lines) conflated three unrelated responsibilities behind one shallow seam: RIS parsing, Normalization coordination, and RIS output. This was identified during an architectural review (`docs/architecture-review-20260530.html`) as the strongest deepening opportunity.

Design decisions made during grilling:
- `BibliographicItemReader` absorbs the read side (state machine + `addNormalized*` static helpers). The static helpers stay public for test-fixture use.
- `BibliographicItemWriter` absorbs Remove Mode and Mark Mode output.
- The two validation-only write methods (`writeRisWithTRUTH`, `writeRisWithTRUTH_forDS`) move to `ValidationIOService` in the test source tree, following the precedent set by `ValidationService` and `RecordDBService`.
- `BibliographicItemWriter` and `ValidationIOService` both need to serialize a `Map<String,String>` as RIS. The ~12-line serialization is duplicated with a comment rather than extracted to a shared utility.
- `DeduplicationService` previously instantiated `new IOService()` directly. Changed to proper constructor injection of both new services.
- `RIS_LINE_PATTERN` lives on `BibliographicItemReader`; `BibliographicItemWriter` and `ValidationIOService` reference it there.

## Files created

- `src/main/java/edu/dedupendnote/services/BibliographicItemReader.java`
- `src/main/java/edu/dedupendnote/services/BibliographicItemWriter.java`
- `src/test/java/edu/dedupendnote/validation/services/ValidationIOService.java`

## Files deleted

- `src/main/java/edu/dedupendnote/services/IOService.java`

## Files modified

| File | Change |
|---|---|
| `services/DeduplicationService.java` | Inject `BibliographicItemReader` + `BibliographicItemWriter`; update constructor; update 5 call sites |
| `validation/ValidationTests.java` | Inject `BibliographicItemReader` + `ValidationIOService`; update 3 call sites |
| `validation/experiments/AuthorExperimentsTests.java` | Inject `BibliographicItemReader`; update manual `new DeduplicationService(cs, ...)` |
| `integration/DedupEndNoteApplicationTests.java` | `RIS_LINE_PATTERN` reference |
| `validation/services/RecordDBService.java` | `RIS_LINE_PATTERN` reference |
| `unit/services/JWSimilarityTitleTest.java` | Imports + `addNormalizedTitle`, `SOURCE_PATTERN`, `COMMENT_PATTERN` |
| 8 other unit test files | Import + `IOService.addNormalized*` → `BibliographicItemReader.*` |
| `domain/BibliographicItem.java` | Stale comments |
| `services/NormalizationService.java` | Stale comment |
| `CLAUDE.md` | Services table |

## Verification

```
./mvnw test -Punit-tests        # 544 tests, 0 failures
./mvnw test -Pintegration-tests # 18 tests, 0 failures
```
