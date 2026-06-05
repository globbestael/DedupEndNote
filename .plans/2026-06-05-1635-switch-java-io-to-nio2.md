# Switch java.io file I/O to NIO2

## Context

After the Path refactoring, all file paths are `java.nio.file.Path` objects. However, the
actual file I/O still uses `java.io.FileReader` / `FileWriter` / `BufferedReader` /
`BufferedWriter`, which require a `File` argument — forcing a `.toFile()` conversion at every
call site. This is a code smell: we converted to `Path` throughout and then immediately convert
back to `File` just to open a stream.

Two additional benefits of switching:

**Explicit UTF-8 encoding.** `FileReader(File)` and `FileWriter(File)` use the JVM's
platform-default encoding. On Windows this is historically not UTF-8 (e.g. Windows-1252),
which means the BOM detection in `UtilitiesService.detectBom` could silently fail on Windows:
the UTF-8 BOM bytes `0xEF 0xBB 0xBF` would be decoded as `ï»¿` instead of `﻿`.
`Files.newBufferedReader(path)` and `Files.newBufferedWriter(path)` default to UTF-8.

**Simpler code.** `new BufferedReader(new FileReader(path.toFile()))` →
`Files.newBufferedReader(path)`.

---

## Uniform replacements

| Before | After |
|---|---|
| `new BufferedReader(new FileReader(path.toFile()))` | `Files.newBufferedReader(path)` |
| `new BufferedWriter(new FileWriter(path.toFile()))` | `Files.newBufferedWriter(path)` |
| `path.toFile().delete()` | `Files.deleteIfExists(path)` |
| `assertThat(path.toFile()).exists()` | `assertThat(path).exists()` (AssertJ `PathAssert`) |

`Files.newBufferedWriter` uses `CREATE, TRUNCATE_EXISTING` by default — identical to
`new FileWriter(file, false)`. No behaviour change for non-append writes.

`Files.deleteIfExists` throws checked `IOException`; the two call sites
(`ValidationService.checkResults`) are inside a method that already declares `throws
IOException`, so no signature change is needed.

---

## Special case: Jackson CSV `readValues(File)`

`ValidationService.readTruthFile` calls Jackson's `ObjectReader.readValues(File)`. Jackson
has no `readValues(Path)` overload, but does have `readValues(Reader)`:

```java
// Before
.readValues(truthPath.toFile())

// After
.readValues(Files.newBufferedReader(truthPath))
```

The `MappingIterator` returned by `readValues(Reader)` does not close the reader itself.
However, `it.readAll()` consumes the iterator fully and the reader can be closed. Wrapping
in a try-with-resources is the safe approach:

```java
try (BufferedReader reader = Files.newBufferedReader(truthPath)) {
    MappingIterator<BibliographicItemDB> it = mapper
            .readerFor(BibliographicItemDB.class)
            .with(schema)
            ...
            .readValues(reader);
    return it.readAll();
}
```

---

## Files to change

**Production code**

| File | `FileReader` removals | `FileWriter` removals |
|---|---|---|
| `UtilitiesService` | 1 (detectBom) | — |
| `BibliographicItemReader` | 1 | — |
| `BibliographicItemWriter` | 2 (one per write method) | 2 (one per write method) |

**Test code**

| File | `FileReader` removals | `FileWriter` removals | Other `.toFile()` |
|---|---|---|---|
| `ValidationService` | — | 1 | 2× `.toFile().delete()`, 1× Jackson `readValues` |
| `RecordDBService` | 1 | 1 | — |
| `ValidationIOService` | 2 | 2 | — |
| `DedupEndNoteApplicationTests` | — | — | 1× `assertThat(.toFile()).exists()` |
| `MissedDuplicatesTests` | — | — | 1× `assertThat(.toFile()).exists()` |

**Imports**: Remove `java.io.FileReader` and `java.io.FileWriter` from all affected files.
Add `import java.nio.file.Files` where not already present (`BibliographicItemReader` already
uses `Files.lines`; check others). `java.io.BufferedReader` and `java.io.BufferedWriter`
remain — `Files.newBufferedReader/Writer` return those types.

---

## What is NOT changed (original plan)

- `import java.io.IOException` — stays everywhere; NIO2 throws the same exception type.
- `import java.io.BufferedReader` / `BufferedWriter` — still needed for variable types.
- The BOM skip logic (`br.skip(1)`) — works identically with `Files.newBufferedReader`.
- Any `Files.*` already-NIO2 calls (`Files.lines`, `Files.readAllLines`, `Files.copy`,
  `Files.deleteIfExists`) — already correct.
- `java.io.InputStream` in `DedupEndNoteController` (used for `Files.copy` to servlet
  output stream) and `java.io.StringReader` in `DedupEndNoteApplicationTests` (in-memory
  string wrapping) — no NIO2 equivalent.

## Follow-on changes (commit `01f5138`)

Two further eliminations were made after the initial migration:

### `DedupEndNoteController` — `InputStream` replaced by NIO channels ✓
`Files.copy(path, response.getOutputStream())` used `java.io.InputStream` implicitly via
the servlet output stream. Replaced with explicit `FileChannel` / `ReadableByteChannel`
(NIO2), removing the `java.io.InputStream` import entirely.

### `DedupEndNoteApplicationTests` — `StringReader` / `BufferedReader` removed ✓
The `lineSeparator` test previously wrapped a `String` in `new StringReader(...)` then
`new BufferedReader(...)` to call `.lines()`. Replaced with `String.lines()` (Java 11+),
removing both `java.io.StringReader` and `java.io.BufferedReader` from the test class.

### `pom.xml` — commons-io activated ✓
The `commons-io 2.22.0` dependency (previously commented out) was activated to support
`PathUtils.deleteDirectory(Path)` used in `DedupEndNoteApplication`.

---

## Implementation status (executed 2026-06-05 17:14)

All items implemented as planned. No `.toFile()`, `FileReader`, or `FileWriter` calls remain
anywhere in the source tree.

### Uniform replacements ✓
All `new BufferedReader(new FileReader(path.toFile()))` → `Files.newBufferedReader(path)` and
`new BufferedWriter(new FileWriter(path.toFile()))` → `Files.newBufferedWriter(path)` applied.
`FileReader`/`FileWriter` imports removed from all 6 affected files; `Files` import added where
missing (`BibliographicItemWriter`, `RecordDBService`, `ValidationIOService`, `ValidationService`).

### `.toFile().delete()` ✓
`ValidationService`: `fpAnalysisPath.toFile().delete()` / `fnAnalysisPath.toFile().delete()`
→ `Files.deleteIfExists(fpAnalysisPath)` / `Files.deleteIfExists(fnAnalysisPath)`.
Method already declared `throws IOException`.

### Jackson `readValues(File)` ✓
Wrapped in try-with-resources using `var reader = Files.newBufferedReader(truthPath)`.
No additional `BufferedReader` import needed (type inferred by `var`).

### `assertThat(path.toFile()).exists()` ✓
Replaced with AssertJ `PathAssert`: `assertThat(path).exists()` in
`DedupEndNoteApplicationTests` and `MissedDuplicatesTests`.

### Comment updated ✓
`UtilitiesService.detectBom` comment removed the `FileReader` reference.

## Verification

- `./mvnw test -Punit-tests` — 561 tests, 0 failures ✓
- `./mvnw test -Pintegration-tests` — 20 tests, 0 failures ✓
- Commit: `7fbd105` — 9 files, +37 / −35 lines
- Follow-on commit: `01f5138` — controller NIO channels, String.lines(), commons-io activated
