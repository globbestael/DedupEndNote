# createPath utility and ValidationTests per-dataset deduplication

## Context

Three related problems:

**1. Duplicate extension-stripping logic.**
`createOutputFileName` uses a regex to strip the last extension before inserting a suffix.
`createOutputPath` calls `createOutputFileName`. The four `resolveSibling` call sites introduced
in the previous session use `UtilitiesService.removeFileExtension(inputPath.getFileName().toString()) + suffix`.
All three patterns do the same thing in different ways; one well-named helper can unify them.

**2. Unnecessary intermediate helpers.**
`createOutputFileName(String, mode)` is a String→String transform that exists only to bridge
the browser-boundary `String` to the service-layer `Path`. Once the controller resolves the
input to a `Path` first (via `resolveInUploadDir`), `createOutputFileName` is no longer needed —
the output path can be derived directly from the input `Path`. Similarly, `createOutputPath` is
just a thin wrapper that translates a `DeduplicationMode` to a suffix string and calls
`createOutputFileName`. Both can be eliminated.

**3. Structural duplication in `ValidationTests`.**
The 14 `checkResults_*` methods and ~10 `createInitialTruthFile_*` / `createRisWithTRUTH_*`
methods each construct 2–4 related paths that follow a consistent naming convention: strip the
extension from the input filename, append a suffix, add `.txt`. They differ only in the input
path and set name — pure copy-paste.

---

## Design decisions made

- **No 2-argument overload of `createPath`** — always pass the new extension explicitly.
- **Extension is always `"txt"`** for derived output files. This is safer than preserving the
  original extension: a `.ris` output file double-clicked on a Windows machine would be
  auto-imported into any open EndNote database. Using `.txt` avoids that risk.
- **`DeduplicationMode.toString()` is NOT changed** — it is used in log messages where `MARK`
  and `REMOVE` are the correct representations. A dedicated `filenameSuffix()` method carries
  the filename contribution instead.

---

## Proposed solution

### 1. `DeduplicationMode.filenameSuffix()`

Add a method to the enum that returns the filename suffix for each mode:

```java
public String filenameSuffix() {
    return this == MARK ? "_mark" : "_deduplicated";
}
```

### 2. `UtilitiesService.createPath(Path inputPath, @Nullable String addition, String newExtension)`

```java
Path createPath(Path inputPath, @Nullable String addition, String newExtension)
```

- Strips the last extension from `inputPath.getFileName()` via `removeFileExtension`.
- Appends `addition` if it is non-null and non-blank.
- Appends `.` + `newExtension`.
- Returns a sibling `Path` in the same parent directory.
- `newExtension` must not be null, empty, or blank — validate and throw
  `IllegalArgumentException`.
- NullAway: guard `inputPath.getParent()` with the same null-check pattern as the existing
  `createOutputPath`.

Examples:
```
createPath(Path(".../SRA2/Stroke.txt"),    "_TRUTH",        "txt") → .../SRA2/Stroke_TRUTH.txt
createPath(Path(".../SRA2/Stroke.txt"),    "_to_validate",  "txt") → .../SRA2/Stroke_to_validate.txt
createPath(Path(".../TIL/TIL.txt"),        "_mark",         "txt") → .../TIL/TIL_mark.txt
createPath(Path(".../TIL/TIL_Zotero.ris"), "_mark",         "txt") → .../TIL/TIL_Zotero_mark.txt
createPath(Path(".../Stroke.txt"),          null,            "txt") → .../Stroke.txt
```

### 3. Delete `createOutputFileName` and `createOutputPath`

Both helpers are eliminated:

- **`createOutputFileName(String, mode)`** — its only call sites are in the controller and
  internally in `createOutputPath`. Controller call sites are restructured (see below).
- **`createOutputPath(Path, mode)`** — replaced at all call sites by
  `createPath(inputPath, mode.filenameSuffix(), "txt")`.

### 4. Controller restructured to use `createPath` directly

**Before** (String→String→Path detour):
```java
Path inputPath  = UtilitiesService.resolveInUploadDir(uploadDir, inputFileName);
Path outputPath = UtilitiesService.resolveInUploadDir(uploadDir,
        UtilitiesService.createOutputFileName(inputFileName, mode));
```

**After** (Path-first, single resolution):
```java
Path inputPath  = UtilitiesService.resolveInUploadDir(uploadDir, inputFileName);
Path outputPath = UtilitiesService.createPath(inputPath, mode.filenameSuffix(), "txt");
```

This applies to `startOneFile`, `startTwoFiles`, and `getResultFile`. The second
`resolveInUploadDir` call on the output is removed — the output is guaranteed to be a sibling
of the already-validated input, so no separate traversal check is needed.

### 5. Replace the four `resolveSibling` call sites

| File | Before | After |
|---|---|---|
| `ValidationTests.deduplicate()` | `resolveSibling(removeFileExtension(...) + "_mark.txt")` | `createPath(inputPath, "_mark", "txt")` |
| `ValidationService` (FP) | `resolveSibling(removeFileExtension(...) + "_FP_Analysis.txt")` | `createPath(inputPath, "_FP_Analysis", "txt")` |
| `ValidationService` (FN) | `resolveSibling(removeFileExtension(...) + "_FN_Analysis.txt")` | `createPath(inputPath, "_FN_Analysis", "txt")` |
| `AuthorExperimentsTests` | `resolveSibling(removeFileExtension(...) + "_mark.txt")` | `createPath(inputPath, "_mark", "txt")` |

After this change the explicit `UtilitiesService` import in `ValidationService` and
`AuthorExperimentsTests` is already present; `removeFileExtension` is no longer called directly
but `createPath` is.

---

## ValidationTests: generic helpers

The per-dataset methods follow one of four shapes. A generic helper for each shape eliminates
the per-dataset path repetition. All derived paths use `"txt"` as extension.

### Shape A — `checkResults_*` (13 of 14 datasets)

`truthPath` = input with `_TRUTH.txt` suffix; `outputPath` = input with `_to_validate.txt`
suffix. Set name must be passed — it is not mechanically derivable from the path.

```java
ValidationResult checkResultsFor(String setName, Path inputPath) throws IOException {
    return checkResults(setName, inputPath,
        UtilitiesService.createPath(inputPath, "_to_validate", "txt"),
        UtilitiesService.createPath(inputPath, "_TRUTH",       "txt"));
}
```

Per-dataset becomes a one-liner:
```java
ValidationResult checkResults_SRA2_Stroke() throws IOException {
    return checkResultsFor("SRA2_Stroke", testDir.resolve("SRA2/Stroke.txt"));
}
```

**Exception — `checkResults_TIL_Zotero`**: truth file is `TIL_TRUTH.txt`, shared with the `TIL`
dataset — not `TIL_Zotero_TRUTH.txt`. Keep explicit paths; add a comment.

### Shape B — `createInitialTruthFile_*` without ASySD gold file (6 methods)

`outputPath` = input with `_for_truth.txt` suffix.

```java
void createInitialTruthFileFor(Path inputPath) {
    createInitialTruthFile(inputPath,
        UtilitiesService.createPath(inputPath, "_for_truth", "txt"));
}
```

### Shape C — `createInitialTruthFile_*` with ASySD gold file (4 methods)

`asysdInputPath` = input with `_asysd_gold.txt` suffix; `outputPath` = `_for_truth.txt`.

```java
void createInitialTruthFileWithASySDFor(Path inputPath) {
    createInitialTruthFile(inputPath,
        UtilitiesService.createPath(inputPath, "_asysd_gold", "txt"),
        UtilitiesService.createPath(inputPath, "_for_truth",  "txt"));
}
```

### Shape D — `createRisWithTRUTH_*` (2 of 3 methods)

`truthPath` = input with `_TRUTH.txt`; `outputPath` = input with `_with_TRUTH.txt`.

```java
void createRisWithTRUTHFor(Path inputPath) throws IOException {
    createRisWithTRUTH(inputPath,
        UtilitiesService.createPath(inputPath, "_TRUTH",      "txt"),
        UtilitiesService.createPath(inputPath, "_with_TRUTH", "txt"));
}
```

**Exception — `createRisWithTRUTH_BIG_SET_DS`**: truth file is in a different directory from
the input. Keep explicit paths.

---

## Files to change

| File | Change |
|---|---|
| `DeduplicationMode` | Add `filenameSuffix()` method |
| `UtilitiesService` | Add `createPath(Path, @Nullable String, String)`; delete `createOutputFileName` and `createOutputPath` |
| `DedupEndNoteController` | Remove `createOutputFileName` calls; use `createPath(inputPath, mode.filenameSuffix(), "txt")` |
| `ValidationTests` | Add 4 generic helpers; replace 13 `checkResults_*`, ~6 simple `createInitialTruthFile_*`, 4 ASySD `createInitialTruthFile_*`, 2 `createRisWithTRUTH_*` with one-liner calls; keep 2 explicit exceptions |
| `ValidationService` | Replace `resolveSibling(removeFileExtension(...) + "...")` with `createPath(...)` |
| `AuthorExperimentsTests` | Same |
| Integration tests (`DedupEndNoteApplicationTests`, `TwoFilesTests`, `MissedDuplicatesTests`) | Replace `createOutputPath(inputPath, mode)` with `createPath(inputPath, mode.filenameSuffix(), "txt")` |
| `UtilitiesServiceTest` | Replace `createOutputFileName` tests; add `createPath` and `DeduplicationMode.filenameSuffix` tests |
| `changelog.html` | Add Internal bullet for 1.1.7 |
| `CLAUDE.md` | Update file-path naming convention section |

---

## What is NOT changed

- `RecordDBService.saveRecordDBs` — `replace("mark.", "markDB.")` manipulates a known substring
  within an already-correctly-named file; `createPath` does not apply.
- `checkResults_TIL_Zotero` and `createRisWithTRUTH_BIG_SET_DS` — exceptions documented above.
- `DeduplicationMode.toString()` — unchanged; `"MARK"` / `"REMOVE"` are the right log
  representations.

---

## Verification

- `./mvnw test -Punit-tests` — all unit tests including new `createPath` and
  `filenameSuffix` tests pass
- `./mvnw test -Pintegration-tests` — all integration tests pass
- Behaviour note: output files that previously had a `.ris` extension (when the input was
  `.ris`) will now be `.txt`. This is intentional — `.ris` output files risk auto-import
  into open EndNote databases on double-click.
