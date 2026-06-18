# Services package restructure

## Context

The `edu.dedupendnote.services` package had grown to 23 files with three distinct
concerns mixed in a flat structure. Naming conventions (`Default*`, `*NormalizationService`)
partially compensated, but the package was harder to navigate than necessary.

## Change

Split `services` into three packages:

| Package | Contents |
|---|---|
| `services` (root, 7 files) | `DeduplicationService`, `BibliographicItemReader`, `BibliographicItemWriter`, `EnrichmentService`, `UtilitiesService`, `InvalidRisFileException`, `package-info.java` |
| `services/comparison` (13 files) | 4 `*ComparisonService` interfaces, 4 `Default*ComparisonService` implementations, 3 `*Thresholds` value objects, `FieldComparators`, `package-info.java` |
| `services/normalization` (6 files) | `NormalizationService` + 4 `*NormalizationService` classes, `package-info.java` |

## Files modified

**New files created:**
- `services/comparison/package-info.java` (@NullMarked)
- `services/normalization/package-info.java` (@NullMarked)

**Moved via `git mv` (package declaration updated in each):** 17 files

**Import additions — root `services` classes referencing moved classes:**
- `BibliographicItemReader` → added 5 normalization imports
- `BibliographicItemWriter` → added `NormalizationService` import
- `DeduplicationService` → added `FieldComparators`, `DefaultJournalComparisonService`, `DefaultPagesComparisonService` imports

**Import additions — `comparison` classes referencing root `services`:**
- `DefaultJournalComparisonService` → added `UtilitiesService` import
- `DefaultPagesComparisonService` → added `UtilitiesService` import

**Import additions — `normalization` classes referencing root `services`:**
- `PagesNormalizationService` → added `UtilitiesService` import

**Visibility fix:**
- `UtilitiesService.setsContainSameString` promoted from package-private to `public`
  (callers in `comparison` subpackage now cross a package boundary)

**Test imports updated:** 20 test files

**Docs updated:**
- `CLAUDE.md` — Key packages section
- `docs/architecture.html` — service map package description

## Verification

`./mvnw test -Punit-tests` → 557 tests, 0 failures, 0 errors
