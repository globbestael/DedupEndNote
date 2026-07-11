# Move both EnrichmentService calls into BibliographicItemWriter

Supersedes the earlier draft `.plans/2026-06-29-1603-colocate-enrichment-in-writer.md`.

## Context and architectural framing

The deduplication pipeline has three phases:

1. **Read** — `BibliographicItemReader` parses RIS input into `List<BibliographicItem>`.
2. **Compare** — `DeduplicationService.searchYear*` / `compareSet` groups items into
   duplicate sets by assigning labels. Once labels are assigned the comparison work is done.
3. **Output** — In `REMOVE` mode: produce a *representation* of each duplicate set and write
   it. In `MARK` mode: write all items with duplicate-set membership annotated in LB.

Under this framing, everything in `EnrichmentService` is output-phase logic:

- `enrich()` — synthesises the best representation of a duplicate set from all its members
  (merges DOIs, fills missing year/pages, selects best title for Reply/ClinicalTrials). Also
  marks which items are NOT the representative by setting `isKeptBibliographicItem = false`.
- `enrichCochrane()` (new, extracted from `enrich()`) — uppercases pages for kept Cochrane
  items; independent of duplicate-set membership.
- `enrichMap()` — projects the in-memory representation onto the raw RIS field map at write
  time.

All three belong in the writer. `DeduplicationService` becomes a pure orchestrator:
read → compare → write. `isKeptBibliographicItem` is output-phase bookkeeping (which item
is the representative) set during `enrich()`, read by the writer loop; it is not domain
state from the comparison phase.

## Why MARK mode is unaffected

In MARK mode `enrich()` and `enrichCochrane()` are never called (guarded by
`mode == REMOVE`). All items therefore keep their initial `isKeptBibliographicItem = true`.
The writer re-reads from the appropriate input path; its `recordIdMap` is built with
`getId() > 0`, which already excludes old-file items (id < 0) in two-file mode. The writer
loop's existing `isKeptBibliographicItem` check is always true in MARK mode, so all records
from the input path are written — exactly the current behaviour.

## Changes

### 1. `EnrichmentService` — extract `enrichCochrane()`, clean up `enrich()`

File: `src/main/java/edu/dedupendnote/services/EnrichmentService.java`

**Remove from `enrich()`:**
- The Cochrane pages-uppercase block inside the duplicate-set loop (current lines 110–113):
  ```java
  if (bibliographicItemToKeep.isCochrane() && bibliographicItemToKeep.getPagesOutput() != null) {
      bibliographicItemToKeep.setPagesOutput(bibliographicItemToKeep.getPagesOutput().toUpperCase());
  }
  ```
- The entire "Cochrane bibliographicItems without duplicates" loop at the end of `enrich()`
  (current lines 136–141).

**Add to `enrich()` (two-file support):**
After the existing `labelMap` loop, add a pass to mark new-file items that are duplicates
of old-file items as not-kept (they have `id > 0` but `label < 0`):
```java
bibliographicItems.stream()
        .filter(r -> r.getId() > 0 && r.getLabel() != null && r.getLabel() < 0)
        .forEach(r -> r.setKeptBibliographicItem(false));
```
This replaces the explicit `filteredBibliographicItems` stream that currently lives in
`DeduplicationService.deduplicateTwoFiles`.

**Add new method:**
```java
public void enrichCochrane(List<BibliographicItem> bibliographicItems) {
    for (BibliographicItem r : bibliographicItems) {
        if (r.isKeptBibliographicItem() && r.isCochrane() && r.getPagesOutput() != null) {
            r.setPagesOutput(r.getPagesOutput().toUpperCase());
        }
    }
}
```
This handles both Cochrane items with duplicates (previously done inside the loop in
`enrich()`) and Cochrane singletons (previously the standalone loop). The
`isKeptBibliographicItem` guard ensures only the representative is touched; it is correct
here because `enrichCochrane()` is called after `enrich()` has set the flags.

### 2. `BibliographicItemWriter.writeBibliographicItems` — add `progressReporter`, call enrich

File: `src/main/java/edu/dedupendnote/services/BibliographicItemWriter.java`

**New signature:**
```java
public int writeBibliographicItems(List<BibliographicItem> bibliographicItems,
        Path inputPath, Path outputPath, DeduplicationMode mode,
        Consumer<String> progressReporter)
```

**At the top of the method, before building `bibliographicItemsToKeep`:**
```java
if (mode == DeduplicationMode.REMOVE) {
    progressReporter.accept("Enriching the " + bibliographicItems.size() + " deduplicated results");
    enrichmentService.enrich(bibliographicItems);
    enrichmentService.enrichCochrane(bibliographicItems);
}
```

**Move `bibliographicItemsToKeep` computation to after the `if` block** (so it reflects
the `isKeptBibliographicItem` flags set by `enrich()`):
```java
List<BibliographicItem> bibliographicItemsToKeep = bibliographicItems.stream()
        .filter(BibliographicItem::isKeptBibliographicItem).toList();
log.debug("Publications to be kept: {}", bibliographicItemsToKeep.size());
if (mode == DeduplicationMode.REMOVE) {
    progressReporter.accept("Saving the " + bibliographicItemsToKeep.size() + " deduplicated results");
}
```

Note: "Saving X" now shows the actual number to be written (not the total). This is a minor
accuracy improvement over the current text (which showed the total).

**`recordIdMap` stays as-is** (`getId() > 0` filter is unchanged; it is sufficient for
all modes and both one-file and two-file).

**`enrichMap()` call inside `writeBibliographicItem` is unchanged** (already gated on
`mode == REMOVE`).

Add `import java.util.function.Consumer;`.

### 3. `DeduplicationService` — remove enrich calls, pass full list + reporter

File: `src/main/java/edu/dedupendnote/services/DeduplicationService.java`

**Remove:**
- `private final EnrichmentService enrichmentService;` field (and constructor param/assign).
- All four `enrichmentService.enrich(...)` / `enrichmentService.enrichCochrane(...)` call
  sites (they are now in the writer).
- The "Enriching the N …" / "Saving the N …" `progressReporter.accept(...)` calls in the
  one-file REMOVE branch.
- The `filteredBibliographicItems` stream and `log.error("Publications to write: ...")` in
  the two-file REMOVE branch.

**Update all four `writeBibliographicItems` call sites** to add `progressReporter` as the
last argument.

**Two-file REMOVE branch:** change `writeBibliographicItems(filteredBibliographicItems, ...)`
to `writeBibliographicItems(bibliographicItems, ...)` — the writer now owns the filtering
via `isKeptBibliographicItem`.

**Result strings are unchanged:**
- One-file REMOVE: `formatResultString(bibliographicItems.size(), numberWritten)` — list
  size is unchanged by enrich (enrich mutates objects, not the list).
- Two-file REMOVE: `(newBibliographicItems.size() - numberWritten)` — `newBibliographicItems`
  is still in scope and unchanged.

**Remove `import` for `EnrichmentService`** if it becomes unused.

### 4. Update the one hand-wired test constructor

File: `src/test/java/edu/dedupendnote/validation/experiments/AuthorExperimentsTests.java`
(line 69–70)

`DeduplicationService` constructor no longer takes `EnrichmentService`:
```java
DeduplicationService expService = new DeduplicationService(cs, new BibliographicItemReader(),
        new BibliographicItemWriter(enrichmentService));
```
`enrichmentService` local is still needed for `new BibliographicItemWriter(enrichmentService)`.

## Verification

1. `./mvnw test -Punit-tests` — compile (NullAway/Error Prone on the new `Consumer` param)
   + unit regression.
2. `./mvnw test -Pintegration-tests` — `DeduplicationServiceTests` (one-file + two-file,
   MARK + REMOVE) are the regression guard: they assert on returned result strings and record
   counts, confirming output is unchanged.
3. `./mvnw -Dtest=AuthorExperimentsTests test` — exercises the hand-wired constructor and
   the mark-mode re-read path end to end.

## Follow-up

Update the earlier plan file `.plans/2026-06-29-1603-colocate-enrichment-in-writer.md` to
note it is superseded by this one.
