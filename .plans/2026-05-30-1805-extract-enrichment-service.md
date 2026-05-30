# Extract EnrichmentService from DeduplicationService

## Context

`DeduplicationService.enrich()` was a private ~107-line method that could not be tested without a full `@SpringBootTest`. It handled four concerns: Reply title replacement, ClinicalTrials.gov title replacement, DOI gathering, missing year/pages fill-in, and Cochrane page capitalisation. Extracting it to a dedicated `@Service` gives it a testable seam.

Design decisions made during grilling:
- The two inline mutations in `compareSet()` (propagate `isReply` flag, copy reply title to pivot) were confirmed load-bearing for comparison — all three non-pages comparison services read `isReply()` — so they stay in `compareSet()`.
- Wide interface (`enrich(List<BibliographicItem>)`) chosen over narrow (`enrich(kept, duplicates)`) for a straight lift-and-shift with minimal risk.
- `setKeptBibliographicItem(false)` stays in `enrich()`, with a TODO comment: setting it there is a no-op in MARK mode (enrich is only called in REMOVE mode), so it may belong in `compareSet()` — to be tested later.
- `EnrichmentService` is a plain `@Service` (singleton); no `@RequestScope` needed since the method is stateless.

## Files created

- `src/main/java/edu/dedupendnote/services/EnrichmentService.java`

## Files modified

| File | Change |
|---|---|
| `services/DeduplicationService.java` | Inject `EnrichmentService`; update constructor; remove private `enrich()` method; update 2 call sites; remove `Comparator` and `Set` imports |
| `validation/experiments/AuthorExperimentsTests.java` | Import + manual `new DeduplicationService(cs, ...)` updated |
| `CLAUDE.md` | Services table updated |

## Verification

```
./mvnw test -Punit-tests        # 544 tests, 0 failures
./mvnw test -Pintegration-tests # 18 tests, 0 failures
```
