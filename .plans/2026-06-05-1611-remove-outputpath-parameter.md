# Remove outputPath parameter from deduplicateOneFile and deduplicateTwoFiles

## Context

After `createPath` and `DeduplicationMode.filenameSuffix()` were introduced, every call site
of `deduplicateOneFile` and `deduplicateTwoFiles` computed `outputPath` the same way before
passing it in:

```java
Path outputPath = UtilitiesService.createPath(inputPath, mode.filenameSuffix(), "txt");
deduplicationService.deduplicateOneFile(inputPath, outputPath, mode, progressReporter);
```

`outputPath` is a derived value — fully determined by `inputPath` and `mode`, both of which
are already parameters of the method. Passing it as a third argument is redundant and creates
a potential for mismatch between what the caller computes and what the method would compute
internally.

## Change

Move `outputPath` computation inside both methods:

```java
public String deduplicateOneFile(Path inputPath, DeduplicationMode mode,
        Consumer<String> progressReporter) {
    Path outputPath = UtilitiesService.createPath(inputPath, mode.filenameSuffix(), "txt");
    ...
}

public String deduplicateTwoFiles(Path newInputPath, Path oldInputPath,
        DeduplicationMode mode, Consumer<String> progressReporter) {
    Path outputPath = UtilitiesService.createPath(newInputPath, mode.filenameSuffix(), "txt");
    ...
}
```

`UtilitiesService` is in the same package as `DeduplicationService` — no import needed.

## Special case: ValidationTests and AuthorExperimentsTests

Both files read the mark output file after calling `deduplicateOneFile`, so they still need
to know the output path. `markPath` is computed **after** the call (no longer before), using
the same `createPath` formula to locate the file that was just written:

```java
expService.deduplicateOneFile(inputPath, DeduplicationMode.MARK, message -> {});
Path markPath = UtilitiesService.createPath(inputPath, DeduplicationMode.MARK.filenameSuffix(), "txt");
List<BibliographicItem> items = reader.readBibliographicItems(markPath, ...);
```

## Files changed

| File | Change |
|---|---|
| `DeduplicationService` | Remove `outputPath` from both method signatures; add internal `createPath` call |
| `DedupEndNoteController` | Remove `outputPath` variable from `startOneFile` and `startTwoFiles` |
| `DedupEndNoteApplicationTests` | Remove `outputPath` variable (3 sites); remove `UtilitiesService` import |
| `TwoFilesTests` | Remove `outputPath` variable (2 sites); remove `UtilitiesService` import |
| `MissedDuplicatesTests` | Remove `outputPath` variable; remove `UtilitiesService` import |
| `ValidationTests` | `deduplicate()` helper: move `markPath` computation after the call |
| `AuthorExperimentsTests` | Same — move `markPath` computation after the call |
| `docs/architecture.html` | Sequence diagram: `deduplicateOneFile(inputPath, outputPath, mode, …)` → `deduplicateOneFile(inputPath, mode, …)` |

## Verification

- `./mvnw test -Punit-tests` — 561 tests, 0 failures ✓
- `./mvnw test -Pintegration-tests` — 20 tests, 0 failures ✓
- Commit: `6c7f270` — 8 files, +22 / −26 lines
