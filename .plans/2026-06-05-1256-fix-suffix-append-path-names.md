# Fix double-extension filenames from suffix-append path construction

## Context

When constructing a sibling path by appending a suffix to an existing `Path`, the pattern

```java
inputPath.resolveSibling(inputPath.getFileName() + "_mark.txt")
```

produces a double-extension filename because `getFileName()` returns the full filename
including its extension (e.g. `Haematology.txt`). The concatenation yields
`Haematology.txt_mark.txt` instead of the intended `Haematology_mark.txt`.
The same problem affects `.ris` inputs: `TIL_Zotero.ris_mark.txt`.

## Fix

`UtilitiesService.removeFileExtension(String filename)` was added by the user (with an
overload `removeFileExtension(String, boolean removeAllExtensions)`) and applied at all
four affected call sites.

## Files modified

| File | Change |
|---|---|
| `UtilitiesService` | `removeFileExtension(String)` and `removeFileExtension(String, boolean)` added (user-authored) |
| `ValidationTests` | `deduplicate()` helper: `_mark.txt` suffix; added `import UtilitiesService` |
| `ValidationService` | `_FP_Analysis.txt` and `_FN_Analysis.txt` suffixes; added `import UtilitiesService` |
| `AuthorExperimentsTests` | `_mark.txt` suffix; added `import UtilitiesService` |

`RecordDBService.saveRecordDBs` uses `replace("mark.", "markDB.")` on a filename that was
already correctly named — no extension-embedding problem there, left untouched.

## Verification

- `./mvnw test -Punit-tests` — 554 tests, 0 failures ✓
- `./mvnw test -Pintegration-tests` — 20 tests, 0 failures ✓
- Commit: `0ecd34c`
