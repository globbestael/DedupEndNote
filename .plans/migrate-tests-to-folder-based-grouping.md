# Migrate from tag-based to folder-based test grouping

## Context

Today unit vs. integration tests are distinguished by JUnit `@Tag("integration")` on
`AbstractIntegrationTest` (and on the standalone `SimilarityAuthorTest`). The two
Maven profiles `unit-tests` / `integration-tests` select tests via
`<groups>` / `<excludedGroups>`. In VS Code's Test Explorer this requires a
named `java.test.config` setup and a dropdown pick.

We want the split to be visible from the filesystem: integration tests under
`edu.dedupendnote.integration.*`, unit tests under `edu.dedupendnote.unit.*`.
Clicking a folder in the Test Explorer then runs only that group — no config
needed. Maven profiles switch to path patterns instead of tags.

The move stays under `edu.dedupendnote.*` so Spring Boot's `@SpringBootTest`
auto-detection of `DedupEndNoteApplication` keeps working, and
`RecordDBService` (a `@Service` in test sources consumed by `ValidationTests`)
keeps being picked up by component scan.

On completion this plan should be copied to `.plans/` per CLAUDE.md.

## Target layout

```
src/test/java/edu/dedupendnote/
├── unit/
│   ├── BaseTest.java
│   ├── domain/
│   │   └── PublicationExperiment.java
│   └── services/
│       ├── AuthorsBaseTest.java
│       ├── JournalsBaseTest.java
│       ├── ComparisonServiceTest.java
│       ├── NormalizationServiceAuthorTest.java
│       ├── NormalizationServiceDoiTest.java
│       ├── NormalizationServiceJournalTest.java
│       ├── NormalizationServicePagesTest.java
│       ├── NormalizationServiceTextTest.java
│       ├── NormalizationServiceTitleTest.java
│       ├── SimilarityJournalTest.java
│       ├── JWSimilarityAbstractTest.java
│       ├── JWSimilarityAuthorTest.java
│       ├── JWSimilarityJournalTest.java
│       ├── JWSimilarityTitleTest.java
│       ├── AuthorsComparisonThresholdTest.java
│       ├── AuthorVariantsExperimentsTest.java
│       └── AuthorPermutationsExperimentsTest.java   (@Disabled)
├── integration/
│   ├── AbstractIntegrationTest.java
│   ├── DedupEndNoteApplicationTests.java
│   ├── MissedDuplicatesTests.java
│   ├── TwoFilesTest.java
│   ├── domain/
│   │   └── ValidationResult.java
│   ├── services/
│   │   ├── AuthorExperimentsTests.java
│   │   ├── SimilarityAuthorTest.java
│   │   ├── ValidationTests.java
│   │   └── RecordDBService.java
│   └── utils/
│       └── MemoryAppender.java
└── TimingExtension.java                              (left in place; orphan, unused)
```

Classifications are from the Phase 1 scan: 6 integration tests, 13 unit tests,
1 class-level `@Disabled` unit experiment, 5 base classes, 4 helpers.

Rationale for placement of shared pieces:
- `BaseTest` has no Spring annotations and is used only by unit-oriented code
  (3 direct, 4 transitive consumers via `AuthorsBaseTest`) → `unit/`.
- `AuthorsBaseTest` primary purpose is unit; `SimilarityAuthorTest` (the only
  integration consumer) imports it across packages.
- `JournalsBaseTest` has no consumers but follows the same pattern.
- `RecordDBService` must remain under `edu.dedupendnote.*` for component scan
  → `integration/services/`.
- `MemoryAppender` consumers are both integration → `integration/utils/`.
- `PublicationExperiment` / `ValidationResult` each have a single consumer; move
  to that side.

## Execution steps

### 1. Create directories and move files

Move each file listed above to its new path. No content changes in this step.

### 2. Update `package` declarations in every moved file

E.g. `package edu.dedupendnote;` → `package edu.dedupendnote.unit;` in
`BaseTest.java`; `package edu.dedupendnote.services;` →
`package edu.dedupendnote.unit.services;` in every moved unit test, and
similarly for integration.

### 3. Update `import` statements

The following implicit same-package references become cross-package and need
explicit imports:

- Consumers of `BaseTest` (now `edu.dedupendnote.unit.BaseTest`):
  `unit/services/AuthorsBaseTest.java`,
  `unit/services/JournalsBaseTest.java`,
  `unit/services/JWSimilarityTitleTest.java` (was same-package import;
  update to new package).
- Consumers of `AbstractIntegrationTest`
  (now `edu.dedupendnote.integration.AbstractIntegrationTest`):
  `integration/DedupEndNoteApplicationTests.java`,
  `integration/MissedDuplicatesTests.java`,
  `integration/TwoFilesTest.java`,
  `integration/services/AuthorExperimentsTests.java`,
  `integration/services/ValidationTests.java`.
- Consumers of `AuthorsBaseTest` (same package before, still same package
  after — all four subclasses land in `unit/services/` except
  `SimilarityAuthorTest` which moves to `integration/services/` and needs an
  explicit import).
- `SimilarityAuthorTest` also imports `AuthorsBaseTest` from
  `edu.dedupendnote.unit.services`.
- `AuthorVariantsExperimentsTest` → uses `PublicationExperiment`; both land
  in `unit/*`, update import path.
- `ValidationTests` → uses `RecordDBService`, `ValidationResult`,
  `MemoryAppender`; all three move to `integration/*`, update import paths.
- `MissedDuplicatesTests` → uses `MemoryAppender`; update import.

### 4. Remove redundant `@Tag("integration")` annotations

- `AbstractIntegrationTest` — drop the `@Tag("integration")` annotation and
  its import.
- `SimilarityAuthorTest` — drop the `@Tag("integration")` annotation and its
  import.

Folder location now carries the classification; the tag is no longer needed.

### 5. Update `pom.xml` Surefire profiles (path-based)

Replace the tag filters. Unit-tests profile:
```xml
<configuration>
    <excludes>
        <exclude>**/integration/**</exclude>
    </excludes>
</configuration>
```
Integration-tests profile:
```xml
<configuration>
    <includes>
        <include>**/integration/**/*Test.java</include>
        <include>**/integration/**/*Tests.java</include>
    </includes>
</configuration>
```
Keep the default Surefire config (the `argLine` UTF-8 entry) unchanged.

### 6. Update `CLAUDE.md`

The Testing section documents the `services/` subpackage layout and the
tag-based profile split. Update it to describe the new `unit/` and
`integration/` roots and the path-based profiles. Keep the three-category
(Normalization / Similarity / JWSimilarity) taxonomy description — that is
orthogonal to this move.

### 7. VS Code settings — no change required

`.vscode/settings.json` currently has `java.test.config` with only `vmArgs`
(the UTF-8 encoding fix). Folder-based selection works in Test Explorer
without any extra config, so leave this file alone.

## Files modified

- 23 test/helper files moved and their `package` + `import` statements updated
  (see target layout above).
- `src/test/java/edu/dedupendnote/AbstractIntegrationTest.java` →
  `integration/`; `@Tag("integration")` removed.
- `src/test/java/edu/dedupendnote/services/SimilarityAuthorTest.java` →
  `integration/services/`; `@Tag("integration")` removed.
- `pom.xml` — two profile blocks switched from tag groups to path
  includes/excludes (lines ~224–253 in the current file).
- `CLAUDE.md` — Testing / Test class hierarchy / Test profile sections updated.

## Verification

1. Compile: `./mvnw clean test-compile` — no unresolved imports or package
   errors.
2. Unit-tests profile: `./mvnw -Punit-tests test` — expect 500 tests run, 5
   failures (same pre-existing count recorded in the
   `reorganize-tests-by-category-…` plan).
3. Integration-tests profile: `./mvnw -Pintegration-tests test` — expect
   `SimilarityAuthorTest` 44/44 green plus the other integration suites to
   run as before.
4. Confirm no test is picked up by the wrong profile: for each profile, spot-check
   the list of test classes Surefire reports and confirm they live under the
   matching folder.
5. VS Code: reload window, open Test Explorer, click the `unit/` node — only
   unit tests run. Click the `integration/` node — only integration tests run
   (these require a running Spring context and the user-home data files).
6. Run a single unit test by clicking its run icon — confirms the
   `java.test.config` `vmArgs` UTF-8 setting still applies and non-ASCII
   characters render correctly in the output (the original bug that started
   this work).

## Out of scope

- Deleting or relocating the orphan `TimingExtension`.
- Converting `AuthorsBaseTest` / `JournalsBaseTest` to `abstract` (both still
  contain real `@Test` methods; changing that is a separate cleanup).
- Splitting `JWSimilarityTitleTest`'s embedded `IOService` pattern tests into
  a dedicated class (flagged in CLAUDE.md as a future task, unrelated to the
  folder move).
- Introducing a `learning-tests` folder/profile (discussed earlier; to be
  handled in its own plan once the current two-category split lands).
