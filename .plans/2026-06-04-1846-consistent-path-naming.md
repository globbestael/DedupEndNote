# Consistent path naming: service methods take Path; test base dirs are Path

## Context

Variable names in the codebase used `fileName` / `inputFileName` / `outputFileName`
where a full absolute path was actually stored as a `String`. This created two problems:

1. **Semantic mismatch** — `fileName` implies a bare name like `t1.txt`; a full path is
   not a filename.
2. **Type mismatch** — passing absolute paths as `String` bypasses the NIO2 type system and
   forces `Path.of(string)` round-trips at every call site; the compiler cannot distinguish a
   bare name from a full path.

The agreed naming convention:
- `String ...FileName` — bare filename only (e.g. `t1.txt`), exclusively at the
  browser/upload boundary: controller `@RequestParam` fields and `UtilitiesService`
  helpers used by the controller.
- `Path ...Path` — `java.nio.file.Path` object everywhere else in the service and test
  layers.

Two separate passes of work were done:

**Pass 1 (service layer):** Replace all `String` file-path parameters in production services and
tests with `Path`. Add `UtilitiesService.createOutputPath` for path-level callers (service / test
code) alongside the existing `createOutputFileName` which is kept for the controller.

**Pass 2 (test base dirs):** Change the `baseDir` and `testDir` fields in the two test base
classes (`AbstractIntegrationTest`, `BaseTest`) from `String` to `Path` with direct field
initialization. Replace all derived `Path.of(testDir + "...")` string concatenations with
`testDir.resolve("...")`, and all suffix-append patterns (`Path.of(inputPath + "_mark.txt")`)
with `resolveSibling`.

## Naming convention boundary

```
Browser ──→ Controller (@RequestParam String fileName)
               │  resolveInUploadDir() / createOutputPath()
               ▼
         Service layer (Path inputPath, Path outputPath, …)
```

`String` never crosses into the service layer carrying a full path; `Path` never goes to
the browser.

## Files modified

### Production code (Pass 1)

| File | Change |
|---|---|
| `UtilitiesService` | `detectBom(Path)`, `createOutputPath(Path, mode)` added; `detectBom(String)` removed |
| `BibliographicItemReader` | both `readBibliographicItems` overloads and `countRecords` take `Path` |
| `BibliographicItemWriter` | `writeDeduplicatedBibliographicItems` and `writeMarkedBibliographicItems` take `Path inputPath, Path outputPath` |
| `DeduplicationService` | `deduplicateOneFile` and `deduplicateTwoFiles` take `Path`; `doSanityChecks` uses `inputPath.getFileName()` |
| `DedupEndNoteController` | passes `Path` objects directly to service methods; bare `String fileName` kept only for `@RequestParam` and `createOutputFileName` |

### Test code (Pass 1)

| File | Change |
|---|---|
| `DedupEndNoteApplicationTests` | `String inputFileName/outputFileName` → `Path inputPath/outputPath`; bonus fix: error-message assertion used full path instead of `getFileName()` |
| `TwoFilesTests` | same `String` → `Path` rename |
| `MissedDuplicatesTests` | same |
| `ValidationService` | `checkResults`, `readTruthFile`, `writeFNandFPresults` take `Path` |
| `RecordDBService` | `writeMarkedRecordsForDB`, `saveRecordDBs`, `convertToRecordDB` take `Path` |
| `ValidationIOService` | `writeRisWithTRUTH` and `writeRisWithTRUTH_forDS` take `Path` |
| `ValidationTests` | all helpers and per-dataset methods take `Path`; `deduplicate()` helper updated |
| `AuthorExperimentsTests` | `String inputFile/markFile/…` → `Path` |

### Test base classes (Pass 2)

| File | Change |
|---|---|
| `AbstractIntegrationTest` | `String baseDir/testDir` → `Path baseDir/testDir`, initialized directly; `initTestDir()` unchanged as override point |
| `BaseTest` | same |
| `DedupEndNoteApplicationTests` | `initTestDir()`: `testDir = baseDir + "/experiments/"` → `testDir = baseDir.resolve("experiments")` |
| `TwoFilesTests` | same `initTestDir()` fix |
| `MissedDuplicatesTests` | `Path.of(testDir + fileName)` → `testDir.resolve(fileName.startsWith("/") ? fileName.substring(1) : fileName)` (CsvSource values carry a leading `/`) |
| `ValidationTests` | ~40 `Path.of(testDir + "/X/Y")` → `testDir.resolve("X/Y")`; ~6 `String dir = testDir + "..."` → `Path dir = testDir.resolve("...")`; `_mark.txt` suffix → `resolveSibling` |
| `AuthorExperimentsTests` | `String subdir` removed; all paths use `testDir.resolve("SRA2/...")`; `_mark.txt` → `resolveSibling` |
| `AuthorsBaseTest` | `String fileName` + `Path.of(fileName)` collapsed to `Path path = testDir.resolve(...)` |
| `DefaultJournalComparisonServiceTest` | same |
| `JWSimilarityTitleTest` | same (4 occurrences) |
| `ValidationService` | `_FP_Analysis.txt` / `_FN_Analysis.txt` → `resolveSibling` |

### Documentation

| File | Change |
|---|---|
| `docs/architecture.html` | sequence diagram call labels updated to `Path` parameters |
| `src/main/resources/templates/changelog.html` | 1.1.7 Internal bullet added |
| `.thinking/2026-06-04-filename-filepath-path-naming.md` | design Q&A notes |

## Key technical decisions

**Why not `String ...FilePath`?** The "workhorse/racehorse" problem: both `String ...FilePath`
and `Path ...Path` contain "path", making them hard to tell apart at a glance. Eliminating
`String` full-paths from the service layer entirely means only one "path" type remains in that
layer — `Path` — so the naming problem disappears.

**`createOutputFileName` kept for controller, `createOutputPath` added for services.**
`createOutputFileName(String, mode)` is used by the controller with a bare filename from the
request; `createOutputPath(Path, mode)` is used by service/test callers that already have a
`Path`. Both exist in `UtilitiesService`; no caller needed to change.

**`resolveSibling` for suffix-append patterns.**
`Path.of(inputPath + "_mark.txt")` (implicit `Path.toString()` + concat) was replaced with
`inputPath.resolveSibling(inputPath.getFileName() + "_mark.txt")`, which stays in the same
directory without string manipulation.

**Leading `/` in `MissedDuplicatesTests` CsvSource values.**
On Windows, `path.resolve("/foo")` takes only the drive letter as root, yielding `C:\foo`
instead of `C:\testDir\foo`. The CsvSource file suffixes (`'/problems/Rayyan/...'`) carry a
leading `/` as a path separator, not as a root indicator — stripped via `substring(1)`.

**NullAway on `inputPath.getParent()`.**
`Path.getParent()` is `@Nullable`. `createOutputPath` guards with an explicit null-check and
throws `IllegalArgumentException("inputPath must have a parent directory: " + inputPath)`.

## Verification

- `./mvnw clean test -Punit-tests` — 554 tests, 0 failures ✓
- `./mvnw test -Pintegration-tests` — 20 tests, 0 failures ✓
- Commit: `280026f`
