# createPath utility and ValidationTests per-dataset deduplication

## Context

Two related problems:

**1. Duplicate extension-stripping logic.**
`createOutputFileName` uses a regex to strip the last extension before inserting a suffix.
`createOutputPath` calls `createOutputFileName`. The four `resolveSibling` call sites introduced
in the previous session use `UtilitiesService.removeFileExtension(inputPath.getFileName().toString()) + suffix`.
All three patterns do the same thing in different ways; one well-named helper can unify them.

**2. Structural duplication in `ValidationTests`.**
The 14 `checkResults_*` methods and ~10 `createInitialTruthFile_*` / `createRisWithTRUTH_*`
methods each construct 2–4 related paths (input, output, truth, asysd-gold) that follow a
consistent naming convention: strip the extension from the input filename, append a suffix, add
`.txt`. They are identical in shape but differ only in the input path and set name. This causes
significant copy-paste and makes adding a new dataset tedious.

---

## Proposed solution

### 1. New `UtilitiesService.createPath(Path, @Nullable String, String)`

```
Path createPath(Path inputPath, @Nullable String addition, String newExtension)
```

- Takes the filename of `inputPath`, strips the last extension (via `removeFileExtension`),
  appends `addition` if it is non-null and non-blank, appends `.` + `newExtension`.
- Returns a sibling `Path` in the same parent directory.
- `newExtension` must not be null (throws `IllegalArgumentException`); the user stated it
  cannot be empty or blank either, so validate that.
- NullAway: `inputPath.getParent()` is `@Nullable` — guard with the same null-check pattern
  used in `createOutputPath`.

Examples:
```
createPath(Path("/data/SRA2/Stroke.txt"),   "_TRUTH",       "txt") → /data/SRA2/Stroke_TRUTH.txt
createPath(Path("/data/SRA2/Stroke.txt"),   "_to_validate", "txt") → /data/SRA2/Stroke_to_validate.txt
createPath(Path("/data/TIL/TIL.txt"),       "_mark",        "txt") → /data/TIL/TIL_mark.txt
createPath(Path("/data/TIL/TIL_Zotero.ris"),"_mark",        "txt") → /data/TIL/TIL_Zotero_mark.txt
createPath(Path("/data/Stroke.txt"),         null,           "txt") → /data/Stroke.txt
```

### 2. Simplify `createOutputFileName` using `removeFileExtension`

The current regex approach can be replaced with the simpler `removeFileExtension` + string concat:

```java
String addition = mode == DeduplicationMode.MARK ? "_mark" : "_deduplicated";
if (extension == null || extension.isEmpty()) {
    return fileName + addition;   // removeFileExtension is a no-op; just append
}
return removeFileExtension(fileName) + addition + "." + extension;
```

This makes the implementation consistent with the `removeFileExtension` family. No behaviour
change; existing tests cover this.

### 3. Simplify `createOutputPath` using `createPath`

```java
public static Path createOutputPath(Path inputPath, DeduplicationMode mode) {
    String addition = mode == DeduplicationMode.MARK ? "_mark" : "_deduplicated";
    String extension = StringUtils.getFilenameExtension(inputPath.getFileName().toString());
    if (extension == null || extension.isEmpty()) {
        // createPath requires a non-blank extension; handle edge case directly
        Path parent = inputPath.getParent();
        if (parent == null) throw new IllegalArgumentException(...);
        return parent.resolve(removeFileExtension(inputPath.getFileName().toString()) + addition);
    }
    return createPath(inputPath, addition, extension);
}
```

### 4. Replace the four `resolveSibling` call sites with `createPath`

The `UtilitiesService.removeFileExtension(inputPath.getFileName().toString()) + "_mark.txt"` pattern
introduced in the previous session is replaced by the cleaner `createPath` call:

| File | Before | After |
|---|---|---|
| `ValidationTests.deduplicate()` | `resolveSibling(removeFileExtension(...) + "_mark.txt")` | `createPath(inputPath, "_mark", "txt")` |
| `ValidationService` (FP) | `resolveSibling(removeFileExtension(...) + "_FP_Analysis.txt")` | `createPath(inputPath, "_FP_Analysis", "txt")` |
| `ValidationService` (FN) | `resolveSibling(removeFileExtension(...) + "_FN_Analysis.txt")` | `createPath(inputPath, "_FN_Analysis", "txt")` |
| `AuthorExperimentsTests` | `resolveSibling(removeFileExtension(...) + "_mark.txt")` | `createPath(inputPath, "_mark", "txt")` |

After this change the explicit `removeFileExtension` import in `ValidationTests`,
`ValidationService`, and `AuthorExperimentsTests` is no longer needed (they import
`UtilitiesService` for `createPath`).

---

## ValidationTests: generic helpers

The per-dataset methods follow one of four shapes. A generic helper for each shape eliminates
the per-dataset path repetition.

### Shape A — `checkResults_*` (13 of 14 datasets)

Naming convention: `truthPath` = input with `_TRUTH.txt` suffix; `outputPath` = input with
`_to_validate.txt` suffix. Set name must be passed because it is not mechanically derivable from
the path (e.g. `"Clinical_trials"` comes from the directory, `"McKeown_2021"` comes from the
filename, `"ASySD_SRSR_Human"` omits the intermediate `dedupendnote_files` directory).

```java
ValidationResult checkResultsFor(String setName, Path inputPath) throws IOException {
    return checkResults(setName, inputPath,
        UtilitiesService.createPath(inputPath, "_to_validate", "txt"),
        UtilitiesService.createPath(inputPath, "_TRUTH", "txt"));
}
```

Each per-dataset method becomes a one-liner:
```java
ValidationResult checkResults_SRA2_Stroke() throws IOException {
    return checkResultsFor("SRA2_Stroke", testDir.resolve("SRA2/Stroke.txt"));
}
```

**Exception — `checkResults_TIL_Zotero`**: its truth file is `TIL_TRUTH.txt`, shared with the
`TIL` dataset — not `TIL_Zotero_TRUTH.txt`. Keep this method with its explicit paths; add a
comment explaining the exception.

### Shape B — `createInitialTruthFile_*` without ASySD gold file (6 methods)

Naming convention: `outputPath` = input with `_for_truth.txt` suffix.

```java
void createInitialTruthFileFor(Path inputPath) {
    createInitialTruthFile(inputPath,
        UtilitiesService.createPath(inputPath, "_for_truth", "txt"));
}
```

Each per-dataset method becomes:
```java
void createInitialTruthFile_SRA2_Haematology() {
    createInitialTruthFileFor(testDir.resolve("SRA2/Haematology.txt"));
}
```

### Shape C — `createInitialTruthFile_*` with ASySD gold file (4 methods)

Naming convention: `asysdInputPath` = input with `_asysd_gold.txt` suffix;
`outputPath` = input with `_for_truth.txt` suffix.

```java
void createInitialTruthFileWithASySDFor(Path inputPath) {
    createInitialTruthFile(inputPath,
        UtilitiesService.createPath(inputPath, "_asysd_gold", "txt"),
        UtilitiesService.createPath(inputPath, "_for_truth",  "txt"));
}
```

Each per-dataset method becomes:
```java
void createInitialTruthFile_ASySD_Diabetes() {
    createInitialTruthFileWithASySDFor(
        testDir.resolve("ASySD/dedupendnote_files/Diabetes.txt"));
}
```

### Shape D — `createRisWithTRUTH_*` (2 of 3 methods)

Naming convention: `truthPath` = input with `_TRUTH.txt` suffix;
`outputPath` = input with `_with_TRUTH.txt` suffix.

```java
void createRisWithTRUTHFor(Path inputPath) throws IOException {
    createRisWithTRUTH(inputPath,
        UtilitiesService.createPath(inputPath, "_TRUTH",      "txt"),
        UtilitiesService.createPath(inputPath, "_with_TRUTH", "txt"));
}
```

**Exception — `createRisWithTRUTH_BIG_SET_DS`**: its truth file is in a different directory
(`own/BIG_SET_TRUTH.txt`) from the input (`Dedupe-sweep/.../BIG_SET_mark_DS.txt`). Keep this
method with explicit paths.

---

## Files to change

| File | Change |
|---|---|
| `UtilitiesService` | Add `createPath(Path, @Nullable String, String)`; simplify `createOutputFileName` to use `removeFileExtension`; simplify `createOutputPath` to use `createPath` |
| `ValidationTests` | Add 4 generic helpers; replace 13 `checkResults_*`, ~6 simple `createInitialTruthFile_*`, 4 ASySD `createInitialTruthFile_*`, 2 `createRisWithTRUTH_*` with one-liner calls; keep 2 explicit exceptions |
| `ValidationService` | Replace `resolveSibling(removeFileExtension(...) + "...")` with `createPath(...)` |
| `AuthorExperimentsTests` | Same |
| `UtilitiesServiceTest` | Add tests for `createPath` |
| `changelog.html` | Add Internal bullet for 1.1.7 |
| `CLAUDE.md` | Update file-path naming convention section to mention `createPath` |

---

## What is NOT changed

- `RecordDBService.saveRecordDBs` — its `replace("mark.", "markDB.")` pattern manipulates a
  known substring within an already-correctly-named file; it is not a suffix-append pattern and
  `createPath` does not apply.
- `checkResults_TIL_Zotero` and `createRisWithTRUTH_BIG_SET_DS` — exceptions documented above.

---

## Verification

- `./mvnw test -Punit-tests` — all unit tests including new `createPath` tests pass
- `./mvnw test -Pintegration-tests` — all integration tests pass
- No behaviour change: the same file paths are produced, just via the new helper
