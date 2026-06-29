# Clean up the "from old file" / duplicate-set registration on BibliographicItem

## Context

When two files are compared, three fields on `BibliographicItem` encoded overlapping
"provenance / duplicate" information:

- `boolean isPresentInOldFile` — set `true` for every item read from the first (old) file.
- `int id` — negated (made negative) for every old-file item.
- `String label` — the ID of the duplicate set's representative; `startsWith("-")` meant
  the representative is an old-file item.

Two problems:

1. **`isPresentInOldFile` was redundant.** It was set only at `DeduplicationService:321`,
   immediately after `r.setId(-r.getId())` at line 320, and read only at lines 338 and 349.
   IDs are always assigned positive (`missingId = 1` at `BibliographicItemReader:179`;
   EndNote IDs ≥ 1), so after negation old-file items are `id <= -1` and all others
   `id >= 1`. Thus `isPresentInOldFile == true` was exactly equivalent to `getId() < 0`.

2. **`label` was a `String` holding an integer.** Every value stored was an integer ID
   (`String.valueOf(pivot.getId())` in `compareSet`; numeric LB field on mark-mode re-read).
   The `startsWith("-")` tests were really "is the representative ID negative". A nullable
   `Integer` models this correctly — null = "no duplicates", negative value = "representative
   is from the old file". `int` would be wrong (cannot represent the absent case).

Note: `id < 0` ("this item is from the old file") and `label < 0` ("this item's
duplicate-set representative is an old-file item") remain genuinely different concepts.
Only `isPresentInOldFile` was removed.

## Part A — deleted `isPresentInOldFile`, use `getId() < 0`

- `src/main/java/edu/dedupendnote/domain/BibliographicItem.java` — removed the field and its comment.
- `src/main/java/edu/dedupendnote/services/DeduplicationService.java` — dropped `r.setPresentInOldFile(true)` from the forEach; replaced the two read sites (`r.isPresentInOldFile()`) with `r.getId() > 0`.

## Part B — changed `label` from `String` to `Integer`

- `src/main/java/edu/dedupendnote/domain/BibliographicItem.java` — changed field type; updated doc comment.
- `src/main/java/edu/dedupendnote/services/DeduplicationService.java` — `compareSet`: `String.valueOf(pivot.getId())` → `pivot.getId()`; filter: `startsWith("-")` → `>= 0`.
- `src/main/java/edu/dedupendnote/services/EnrichmentService.java` — `startsWith("-")` → `>= 0`; `Map<String, List<...>>` → `Map<Integer, List<...>>`.
- `src/main/java/edu/dedupendnote/services/BibliographicItemReader.java` — LB field parsed to `Integer` with `InvalidRisFileException` on non-numeric input.
- `src/main/java/edu/dedupendnote/services/BibliographicItemWriter.java` — `map.put("LB", ...)` wraps the Integer back to String via `String.valueOf()`.
- `src/test/java/edu/dedupendnote/validation/services/RecordDBService.java` — `Integer.valueOf(bibliographicItem.getLabel())` → `bibliographicItem.getLabel()`.

## Verification

- `./mvnw test -Punit-tests` — 584 tests, 0 failures, 0 errors.
- `./mvnw test -Pintegration-tests` — 23 tests, 0 failures, 0 errors (1 pre-existing skip).

## Note for the enrichment-move plan

The filter in `.plans/2026-06-29-1603-colocate-enrichment-in-writer.md` should now use:
```java
.filter(r -> r.getId() > 0 && (r.getLabel() == null || r.getLabel() >= 0))
```
