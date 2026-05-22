# Align code terminology with CONTEXT.md (Ubiquitous Language)

## Context

Domain-Driven Design's ubiquitous language principle says the code should speak the same vocabulary as the domain model. CONTEXT.md now defines **Bibliographic Item** as the central object (what the code has always called `Publication`), and distinguishes it from a **Publication** (the real-world scholarly item). Keeping `Publication` as the class name means developers and AI agents reading the code see a different word than the domain model — exactly the gap DDD aims to close.

This plan renames the core class and its companions, updates all method and variable names that encode the class name, and leaves user-facing UI strings unchanged (where "publication" is plain English appropriate for end users).

## Scope

### Classes to rename

| Old name | New name | File |
|---|---|---|
| `Publication` | `BibliographicItem` | `src/main/java/edu/dedupendnote/domain/Publication.java` |
| `PublicationDB` | `BibliographicItemDB` | `src/main/java/edu/dedupendnote/domain/PublicationDB.java` |
| `PublicationExperiment` (test) | `BibliographicItemExperiment` | `src/test/java/edu/dedupendnote/unit/domain/PublicationExperiment.java` |

Use the IDE's **Rename** refactoring (IntelliJ: Shift+F6 / VS Code Java: F2) for each class — it atomically renames the file and updates all imports and type references across ~14 production files and ~20 test files.

### Method names to rename

These are public methods whose names encode the old class name. Rename them alongside the class renames (all three classes are touched in one session anyway).

| Class | Old method | New method |
|---|---|---|
| `IOService` | `readPublications(...)` (×2) | `readBibliographicItems(...)` |
| `IOService` | `writePublication(...)` (private) | `writeBibliographicItem(...)` |
| `IOService` | `writeDeduplicatedPublications(...)` | `writeDeduplicatedBibliographicItems(...)` |
| `IOService` | `writeMarkedPublications(...)` | `writeMarkedBibliographicItems(...)` |
| `BibliographicItem` | `isKeptPublication()` / `setKeptPublication(...)` | `isKeptBibliographicItem()` / `setKeptBibliographicItem(...)` |
| `NormalizationService` | `containsOnlyPublicationsWithoutPublicationYear(...)` | `containsOnlyBibliographicItemsWithoutPublicationYear(...)` |
| `NormalizationService` | `containsPublicationsWithoutId(...)` | `containsBibliographicItemsWithoutId(...)` |

**Keep as-is** — these contain "publication" in the sense of the real-world publication, not the class:
- `getPublicationYear()` / `setPublicationYear(...)` — the year a Publication was published; domain-correct
- `normalizeInputPublicationYear(...)` — same reasoning
- Any `containsOnly…WithoutPublicationYear` suffix after the class-name part is renamed (see above)

### Variable/parameter names to update manually

These do NOT benefit from IDE rename (they're local variables, not class references). Update them by file:

**`IOService`**: `publication` → `bibliographicItem` (method-local), `publications` list → `bibliographicItems`

**`DeduplicationService`**:
- `publications` / `filteredPublications` / `newPublications` / `publicationList` → `bibliographicItems` / `filteredBibliographicItems` / `newBibliographicItems` / `bibliographicItemList`
- Keep `r1`, `r2`, `pivot`, `p` — these are neutral short forms and do not encode the class name

**`NormalizationService`**: `publication` → `bibliographicItem` where used as a parameter name

**All `*ComparisonService` interfaces and impls**: parameters `r1`, `r2` are already neutral — leave them.

**`AuthorsBaseTest`**: `fillPublication(...)` → `fillBibliographicItem(...)` (factory helper)

### What NOT to rename

- **User-facing progress/result strings** in `DeduplicationService` (e.g. `"written %s publications"`, `"All publications of the input file..."`) — "publication" here is plain English for end users, not a code term. These deliberately diverge from the domain model vocabulary.
- **`publicationYear` field** — refers to the year of the underlying real-world publication; semantically correct as-is.
- **`Cochrane`, `Phase`, `ClinicalTrials.gov` identifiers** — already align with CONTEXT.md Bibliographic Item type names; no change needed.
- **Log messages and comments** — update opportunistically but not required.

## Execution order

1. ✅ **Rename classes** — done via PowerShell regex replace across all 35 affected Java files; 3 files renamed on disk (`BibliographicItem.java`, `BibliographicItemDB.java`, `BibliographicItemExperiment.java`)
2. ✅ **Rename methods** — `readPublications`, `writePublication`, `writeDeduplicatedPublications`, `writeMarkedPublications`, `isKeptPublication`, `setKeptPublication`, `containsOnlyPublicationsWithoutPublicationYear`, `containsPublicationsWithoutId`, `fillPublication` — all renamed in 12 files
3. ✅ **Rename local variables** — `publication`→`bibliographicItem`, `publications`→`bibliographicItems`, `newPublications`→`newBibliographicItems`, etc. — 20 files; `r1`/`r2`/`pivot` left untouched
4. ✅ **Build** — `BUILD SUCCESS` (2026-05-22 13:11)
5. ✅ **Unit tests** — 4 failures, all pre-existing (confirmed on original branch); no regressions
6. ✅ **Integration tests** — 1 failure (`MissedDuplicatesTests`), pre-existing; no regressions

**Note on execution method:** Steps 1–3 were done with PowerShell `[System.Text.RegularExpressions.Regex]::Replace` rather than IDE rename. A BOM issue (UTF-8 with BOM written by .NET's default `System.Text.Encoding.UTF8`) required a post-pass to strip the BOM from 38 files before the build succeeded.

## Files primarily affected

**Production** (src/main/java/):
- `domain/Publication.java` (renamed + internal changes)
- `domain/PublicationDB.java` (renamed)
- `services/IOService.java` (method renames + variable renames)
- `services/DeduplicationService.java` (variable renames)
- `services/NormalizationService.java` (method + variable renames)
- `services/ComparisonService.java` (type references only)
- All `services/Default*ComparisonService.java` (type references only, `r1`/`r2` kept)

**Tests** (src/test/java/):
- `unit/domain/PublicationExperiment.java` (renamed)
- `unit/services/AuthorsBaseTest.java` (factory method rename)
- ~17 other test files (type references only, updated by IDE rename)

## Verification

```bash
./mvnw clean package -Dmaven.test.skip=true   # must compile clean
./mvnw test -Punit-tests                       # all pass
./mvnw test -Pintegration-tests                # all pass
```

After the rename, grep for any stray `Publication` (as a Java identifier, not in comments/strings):
```bash
grep -rn "\bPublication\b" src/main/java/ src/test/java/ --include="*.java"
```
Expected: zero matches (only `publicationYear`, `BibliographicItem` etc. remain, which don't match the word-boundary pattern).
