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

## What is NOT changed

- `import java.io.IOException` — stays everywhere; NIO2 throws the same exception type.
- `import java.io.BufferedReader` / `BufferedWriter` — still needed for variable types.
- The BOM skip logic (`br.skip(1)`) — works identically with `Files.newBufferedReader`.
- Any `Files.*` already-NIO2 calls (`Files.lines`, `Files.readAllLines`, `Files.copy`,
  `Files.deleteIfExists`) — already correct.

---

## Verification

- `./mvnw test -Punit-tests` — all unit tests pass (encoding behaviour verified via
  existing `Non_Latin_input.txt` test)
- `./mvnw test -Pintegration-tests` — all integration tests pass
- Update `changelog.html` Internal section for 1.1.7
