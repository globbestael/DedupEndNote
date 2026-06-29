# Co-locate the two EnrichmentService calls in BibliographicItemWriter

## Context

EnrichmentService is invoked from two different services today:

- `EnrichmentService.enrich(list)` — mutates the in-memory Kept Bibliographic Items (merges DOIs, fills missing year/pages, fixes Reply/ClinicalTrials/Cochrane titles). Called from **`DeduplicationService`** in both REMOVE branches (`deduplicateOneFile` line 278, `deduplicateTwoFiles` line 345).
- `EnrichmentService.enrichMap(map, item)` — projects the enriched item onto the raw RIS field map during serialization. Called from **`BibliographicItemWriter.writeBibliographicItem`** (line 134).

The two halves of enrichment are split across two services. The user wants them co-located, and prefers them **both in `BibliographicItemWriter`** (the "produce the output" responsibility). This also lets the writer report progress: it currently has no `Consumer<String> progressReporter`, while `BibliographicItemReader` already accepts and uses one — adding it makes reader/writer symmetric.

Outcome: `DeduplicationService` hands the writer the full compared list plus the `progressReporter`; the writer owns enrichment, the REMOVE-mode keep-filter, progress messages, and serialization. Behavior (output files, counts, result strings) is unchanged.

## Why moving `enrich()` into the writer requires moving the keep-filter too

In `deduplicateTwoFiles` REMOVE the current order is:

1. `enrich(bibliographicItems)` on the **full** list (old + new). `enrich` internally skips labels starting with `-`.
2. Filter to `filteredBibliographicItems` = `!isPresentInOldFile && (label == null || !label.startsWith("-"))`.
3. `writeBibliographicItems(filteredBibliographicItems, …)`.

The `-`-labelled new-file items (duplicates of old-file records) keep `isKeptBibliographicItem == true` (enrich never touches them), so **without** the filter the writer would emit them. The filter is therefore essential and must run **after** `enrich`, on the full list. So both `enrich` and the keep-filter move into the writer together, and `DeduplicationService` passes the **full** list in all four call sites.

The filter is a no-op for one-file mode (no item is `presentInOldFile`, no `-` labels) and must **not** run in MARK mode (MARK intentionally writes the `-`-labelled new items, see the comment at `DeduplicationService` line 315). So the filter is gated on REMOVE mode, exactly like `enrich` already is.

## Changes

### 1. `BibliographicItemWriter.writeBibliographicItems` — add `progressReporter`, own enrich + filter

File: `src/main/java/edu/dedupendnote/services/BibliographicItemWriter.java`

- New signature:
  `public int writeBibliographicItems(List<BibliographicItem> bibliographicItems, Path inputPath, Path outputPath, DeduplicationMode mode, Consumer<String> progressReporter)`
- At the top of the method, before building `recordIdMap`:
  ```java
  List<BibliographicItem> itemsToWrite = bibliographicItems;
  if (mode == DeduplicationMode.REMOVE) {
      progressReporter.accept("Enriching the " + bibliographicItems.size() + " deduplicated results");
      enrichmentService.enrich(bibliographicItems);          // full list, as today
      itemsToWrite = bibliographicItems.stream()
              .filter(r -> !r.isPresentInOldFile()
                      && (r.getLabel() == null || !r.getLabel().startsWith("-")))
              .toList();
      progressReporter.accept("Saving the " + itemsToWrite.size() + " deduplicated results");
  }
  ```
  Then build `bibliographicItemsToKeep` and `recordIdMap` from `itemsToWrite` instead of the raw parameter (the `getId() > 0` guard on `recordIdMap` already drops old-file negative IDs, so the `isPresentInOldFile` clause is belt-and-braces and preserves the exact current predicate).
- Add `import java.util.function.Consumer;`.
- `enrichMap` call inside the private `writeBibliographicItem` is unchanged.

### 2. `DeduplicationService` — remove enrich + filter, pass full list + reporter

File: `src/main/java/edu/dedupendnote/services/DeduplicationService.java`

- `deduplicateOneFile` REMOVE branch (lines 276–285): delete the two `progressReporter.accept(...)` lines and the `enrichmentService.enrich(...)` call; call
  `writeBibliographicItems(bibliographicItems, inputPath, outputPath, mode, progressReporter)`.
  Keep `formatResultString(bibliographicItems.size(), numberWritten)` and the final `progressReporter.accept(s)`.
- `deduplicateOneFile` MARK branch (line 267) and `deduplicateTwoFiles` MARK branch (line 335): add the `progressReporter` argument to the `writeBibliographicItems` call (no other change; filter/enrich don't run in MARK).
- `deduplicateTwoFiles` REMOVE branch (lines 344–357): delete the `enrichmentService.enrich(...)` call, the `filteredBibliographicItems` stream, and the `log.error("Publications to write: …")` line; call
  `writeBibliographicItems(bibliographicItems, newInputPath, outputPath, mode, progressReporter)`.
  The result string still uses `newBibliographicItems.size() - numberWritten` and `numberWritten` (both still in scope) — unchanged.
- The `EnrichmentService enrichmentService` field/constructor param in `DeduplicationService` becomes unused after this — remove the field, the constructor parameter, and the assignment.

### 3. Update the one test that constructs the collaborators by hand

File: `src/test/java/edu/dedupendnote/validation/experiments/AuthorExperimentsTests.java` (line 69–70)

`DeduplicationService`'s constructor loses its `EnrichmentService` argument, so update:
```java
DeduplicationService expService = new DeduplicationService(cs, new BibliographicItemReader(),
        new BibliographicItemWriter(enrichmentService));
```
(The `enrichmentService` local is still needed for `new BibliographicItemWriter(enrichmentService)`.) No test calls `writeBibliographicItems` directly, so the new parameter needs no other test edits. All production call paths go through `deduplicate{One,Two}File`, whose signatures are unchanged.

### 4. Note behavioral nicety (no extra work)

After the move, two-file REMOVE now also emits the "Enriching …" / "Saving …" progress messages (previously only one-file REMOVE did). This is a small, desirable consistency improvement, not a regression.

## Verification

- `./mvnw test -Punit-tests` — fast compile + unit regression (confirms the refactor compiles, incl. NullAway/Error Prone on the new `Consumer` param).
- `./mvnw test -Pintegration-tests` — `DeduplicationServiceTests` (one-file and two-file REMOVE + MARK smoke tests) assert on the returned result strings and record counts; these are the regression guard that output is byte-for-byte unchanged.
- Optionally `./mvnw -Dtest=AuthorExperimentsTests test` to confirm the hand-wired constructor edit compiles and runs.
- Optional manual check: run `./mvnw spring-boot:run`, deduplicate a small RIS file in REMOVE mode, confirm the output file and the on-screen progress ("Enriching …", "Saving …", final DONE line) are as before.

## Out of scope

- No change to `EnrichmentService` itself (both methods keep their signatures and logic).
- No change to the comparison/label algorithm or `searchYear*` methods.
