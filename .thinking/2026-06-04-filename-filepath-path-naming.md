# Naming convention: fileName vs filePath vs Path

**Date:** 2026-06-04  
**Branch:** design

## Problem

The application used `fileName` (and `inputFileName`, `outputFileName`, etc.) throughout the
codebase, but in most places these variables hold **full absolute paths**
(e.g. `C:\Users\geert\uploads\t1.txt`), not bare filenames (e.g. `t1.txt`).
The misnaming was most visible in `DeduplicationService.doSanityChecks`, which had to call
`Path.of(fileName).getFileName()` to extract the real filename for an error message — a
code smell that directly exposed the confusion.

## Two distinct concepts

| Concept | Example value |
|---|---|
| Bare filename — just the name, no directory | `t1.txt` |
| Full path — directory + name, as a String | `/tmp/uploads/t1.txt` |
| Full path — as a `java.nio.file.Path` object | `Path.of("/tmp/uploads/t1.txt")` |

## Naming options considered

### Option A — string-only rename
Use `String ...FileName` for bare names, `String ...FilePath` for full paths.
*Rejected*: `...FilePath` (String) and `...Path` (java.nio.file.Path) are both "paths" —
like "workhorse" and "racehorse" both being horses. Easy to confuse.

### Option B — eliminate String paths in services (chosen)
Switch service-layer parameters from `String` to `java.nio.file.Path`.
The "full path as String" concept disappears from the service layer entirely.

**Result — two clean categories:**

| Concept | Java type | Suffix | Example |
|---|---|---|---|
| Bare filename (browser boundary) | `String` | `…FileName` | `inputFileName` |
| Resolved file location (service layer) | `Path` | `…Path` | `inputPath`, `outputPath` |

## Consequence for file I/O

Switching service parameters from `String` to `Path` requires minor mechanical changes:

| Old (String) | New (Path) |
|---|---|
| `new FileReader(s)` | `new FileReader(path.toFile())` |
| `new FileWriter(s)` | `new FileWriter(path.toFile())` |
| `Files.lines(Path.of(s))` | `Files.lines(path)` — simpler |
| `Path.of(s).getFileName()` | `path.getFileName()` — simpler |

No behavior difference when using `.toFile()`. Zero risk.

## Boundary diagram

```
Browser → Controller:   String fileName    (bare name from HTML form field)
Controller:             resolveInUploadDir()  →  Path inputPath  (safe, absolute)
Controller → Services:  Path inputPath, Path outputPath
Services:               path.toFile() for FileReader/FileWriter
                        path.getFileName() for user-facing messages
```

## UtilitiesService strategy

`createOutputFileName(String fileName, DeduplicationMode mode)` is kept for the controller,
which works with bare filenames before calling `resolveInUploadDir`.

A new `createOutputPath(Path inputPath, DeduplicationMode mode)` is added for use in
services and tests. It calls `createOutputFileName` on the filename component, then resolves
the result against the parent directory.

## Files changed

- `UtilitiesService` — `detectBom(Path)`, add `createOutputPath(Path, DeduplicationMode)`
- `BibliographicItemReader` — both `readBibliographicItems` overloads + private `countRecords`
- `BibliographicItemWriter` — `writeDeduplicatedBibliographicItems`, `writeMarkedBibliographicItems`
- `DeduplicationService` — `deduplicateOneFile`, `deduplicateTwoFiles`, `doSanityChecks`
- `DedupEndNoteController` — drop `.toString()`, rename `newFilePath`/`oldFilePath` → `newInputPath`/`oldInputPath`
- Integration tests (3 files) — use `Path.of(...)` and `createOutputPath`
- `ValidationTests` — all per-dataset methods, `checkResults`, `deduplicate`, `createInitialTruthFile`
- `ValidationService` — `checkResults`, `readTruthFile`, `writeFNandFPresults`
- `RecordDBService` — `convertToRecordDB`, `saveRecordDBs`, `writeMarkedRecordsForDB`
- `AuthorExperimentsTests` — local variables
- `docs/architecture.html` — sequence diagram labels
