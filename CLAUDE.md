# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Keeping this file current

Update CLAUDE.md whenever a change affects something documented here. Triggers include:

- Test class renamed, added, deleted, or reclassified (hierarchy section), or moved between unit / integration / validation categories
- New service introduced or existing service's responsibility changed → also update `docs/architecture.html` (service map)
- Algorithm step, threshold, or special-type handling changed → also update `docs/algorithm.md`
- Domain term or mode definition changed → also update `CONTEXT.md`
- Build command, Maven profile, or port changed (commands / configuration sections)
- Architectural pattern added or removed (naming conventions, path helpers)
- Code quality plugin version bumped or new plugin added
- Plan-file naming convention changed (plans section)
- Release workflow or version-management mechanism changed (configuration section)
- Doc file added to `docs/` or existing one renamed/removed (documentation map section)

The update should land in the same commit as the code change.

## Documentation map

| What you need | Where to find it |
|---|---|
| Domain term meanings (Bibliographic Item, Duplicate Set, Modes, item types, validation) | [CONTEXT.md](CONTEXT.md) |
| Pipeline diagram, service responsibilities, sequence diagram | [docs/architecture.html](docs/architecture.html) |
| Algorithm steps, threshold values, INSUFFICIENT_DATA, special types, enrichment | [docs/algorithm.md](docs/algorithm.md) |
| Commands, coding rules, test structure, config, release | This file (CLAUDE.md) |
| Architecture decisions (why X, rejected alternatives) | [docs/adr/](docs/adr/) |

## Commands

```bash
# Build
./mvnw clean package          # Build fat JAR
./mvnw spring-boot:run        # Run locally (port 9777)

# Test
./mvnw test                                    # Run all tests
./mvnw test -Punit-tests                       # Run only unit tests (no Spring context, fast)
./mvnw test -Pintegration-tests               # Run only integration tests (@SpringBootTest)
./mvnw test -Pvalidation-tests               # Run only validation tests (slow, requires truth files)
./mvnw -Dtest=ClassNameTest test              # Run a single test class
./mvnw -Dtest=ClassNameTest#methodName test   # Run a single test method
```

## Architecture

DedupEndNote is a Spring Boot 4.1 / Java 21 web app that deduplicates bibliographic records in RIS format (exported from EndNote, Zotero, PubMed, EMBASE, etc.). Runs on port 9777, deployed as a fat JAR.

See [`docs/architecture.html`](docs/architecture.html) for the pipeline diagram, full service map, and runtime sequence diagram.

### Key packages
- `controllers/` — HTTP endpoints; file upload and dedup triggers; virtual-thread concurrency; WebSocket progress routing
- `domain/` — `BibliographicItem` (core model), `BibliographicItemDB` (in-memory store), `DeduplicationMode` (enum: `REMOVE` / `MARK`)
- `services/` — `DeduplicationService`, `BibliographicItemReader`, `BibliographicItemWriter`, `EnrichmentService`, four `Default*ComparisonService` classes, five `*NormalizationService` classes, `FieldComparators` record

### Modes

`DeduplicationMode.REMOVE` (default) removes duplicates and enriches the Kept Bibliographic Item; `DeduplicationMode.MARK` keeps all items and labels duplicates. See [CONTEXT.md](CONTEXT.md) for definitions.

### 5-step comparison algorithm (all steps must pass)

1. Publication year (±1 year, exact for Cochrane Reviews)
2. Starting page or DOI match
3. Authors (Jaro-Winkler similarity)
4. Title (Jaro-Winkler similarity)
5. ISBN/ISSN or journal name match

See [`docs/algorithm.md`](docs/algorithm.md) for threshold values, INSUFFICIENT_DATA logic, year-bucketing, special-type handling, and enrichment detail.

### File-path naming convention

Two distinct types carry file locations; never mix them:

| Type | Name pattern | Where used |
|---|---|---|
| `String` | `...FileName` | Bare filenames only (e.g. `t1.txt`) — exclusively at the browser/upload boundary: `@RequestParam` fields and `UtilitiesService` helpers called by the controller |
| `java.nio.file.Path` | `...Path` | Full absolute paths — everywhere in the service layer and tests |

```
Browser ──→ Controller (@RequestParam String fileName)
               │  resolveInSessionDir() / createPath()
               ▼
         Service layer (Path inputPath, Path outputPath, …)
```

`UtilitiesService` provides these path helpers:
- `createPath(Path inputPath, @Nullable String addition, String newExtension)` — strips the last extension, appends `addition` (if non-blank), appends `.newExtension`; always use `"txt"` as the extension for derived output files (avoids auto-import of `.ris` files into EndNote)
- `DeduplicationMode.filenameSuffix()` — returns `"_mark"` or `"_deduplicated"` for use as the `addition` argument

When constructing a sibling path from an existing `Path` (e.g. adding a `_mark.txt` suffix),
use `resolveSibling` — never string-concatenate a `Path` and re-parse:
```java
// correct
inputPath.resolveSibling(inputPath.getFileName() + "_mark.txt")
// wrong — implicit toString() + concat
Path.of(inputPath + "_mark.txt")
```

## Code quality

Two compile-time plugins are active — violations are **build errors**:
- **NullAway** (v0.12.12): enforces JSpecify null-safety annotations on all public APIs. Annotate new public methods with `@Nullable` where applicable; unannotated parameters are treated as `@NonNull`.
- **Error Prone** (v2.42.0): catches common Java mistakes at compile time.

## Testing

Tests live under three roots, each with a corresponding Maven profile:

| Folder | Profile | Spring context | Run frequency |
|---|---|---|---|
| `src/test/java/edu/dedupendnote/unit/` | `unit-tests` | No | Every commit |
| `src/test/java/edu/dedupendnote/integration/` | `integration-tests` | `@SpringBootTest` | Every commit |
| `src/test/java/edu/dedupendnote/validation/` | `validation-tests` | `@SpringBootTest` | On demand |

**Integration tests** assert on the string returned by `deduplicateOneFile` (or record counts) on small known inputs — they are regression guards that fail if behaviour changes.

**Validation tests** measure sensitivity/specificity against manually validated truth files in `~/dedupendnote_files` (not in git). They are slow and intended to be run before releases or after structural changes, not on every commit. Validation runs `deduplicateOneFile` in mark mode to exercise the full production code path, then re-reads the mark-mode output with `includeLabelField=true` to extract deduplication groups.

### Test class hierarchy

**Unit (`edu.dedupendnote.unit.*`)**
- **`unit/BaseTest`** — provides `Path baseDir` (`~/dedupendnote_files`) and `Path testDir` (both initialized directly as fields), `@BeforeEach initTestDir()`, plus utilities (`jws`, `getHighestSimilarityForAuthors`, `setLoggerToDebug`)
- **`unit/services/AuthorsBaseTest extends BaseTest`** — shared logic for author-comparison tests
- **`unit/services/DefaultJournalComparisonServiceTest extends BaseTest`** — boolean `compare()` tests for journals (inline parameterized) and a file-based test against validated journal pairs; absorbed `JournalsBaseTest`
- **`unit/services/JWSimilarityTitleTest extends BaseTest`** — title JWS-similarity tests; also holds the out-of-scope `BibliographicItemReader` pattern tests
- **`unit/services/JWSimilarityAuthorTest extends AuthorsBaseTest`** — plain JUnit 5, no Spring; tests raw `jws.apply` score
- Standalone unit test classes (no Spring context): `NormalizationService*Test` (6 files), `DefaultJournalComparisonServiceIssnTest`, `DefaultJournalComparisonServiceTest`, `DefaultTitleComparisonServiceTest`, `JWSimilarityJournalTest`, `JWSimilarityAbstractTest`, `AuthorsComparisonThresholdTest`, `AuthorVariantsExperimentsTest`, etc.

**Integration (`edu.dedupendnote.integration.*`)**
- **`integration/AbstractIntegrationTest`** — base for all `@SpringBootTest` tests; provides `@ActiveProfiles("test")`, `@MockitoBean SimpMessagingTemplate`, `Path baseDir`, `Path testDir`, `@BeforeAll` (log level → INFO), `@BeforeEach initTestDir()`. Subclasses override `initTestDir()` when they need a subdirectory.
- Integration test classes extending `AbstractIntegrationTest`: `DedupEndNoteApplicationTests`, `MissedDuplicatesTests`, `TwoFilesTests`

**Validation (`edu.dedupendnote.validation.*`)**
- **`validation/ValidationTests`** — measures sensitivity/specificity of the production deduplication engine across 14 validated real-world datasets; not a regression guard but a performance monitor. Requires truth files in `~/dedupendnote_files` (not in git). Run with `-Pvalidation-tests`.
- **`validation/experiments/AuthorExperimentsTests`** — runs `DefaultAuthorsComparisonService` with experimental thresholds (`AuthorThresholds(1.0, 1.0, 1.0)`) against a validated dataset and asserts on relative sensitivity/specificity. The `experiments` sub-package holds controlled A/B experiments against production-engine baselines.
- **`validation/services/ValidationService`** — test-only Spring `@Service` that encapsulates the truth-file scoring logic (TP/FP/FN/TN computation, FN/FP analysis file writing). Shared by `ValidationTests` and future experiments tests.
- **`validation/services/RecordDBService`** — test-only Spring `@Service` for reading/writing the tab-delimited DB export format.
- **`validation/domain/ValidationResult`** — POJO holding per-dataset scores (sensitivity, specificity, precision, accuracy, F1, FN/FP pair maps).

Test files follow a three-category taxonomy per field: **Normalization** (`NormalizationService*Test`) / **Comparison** (`Default*ComparisonServiceTest`, boolean result from `compare()` or equivalent static helpers) / **JWSimilarity** (`JWSimilarity*Test`, raw JWS score vs threshold). Files are further split by Spring-context requirement.

The split is enforced by folder. The Maven profiles in `pom.xml` use path-based filters: `unit-tests` (excludes `**/integration/**` and `**/validation/**`), `integration-tests` (includes only `**/integration/**/*Tests.java`), `validation-tests` (includes only `**/validation/**/*Tests.java`). Selecting the folder in VS Code's Test Explorer automatically runs only that category.

There are a number of tests with expected errors. These tests are NOT disabled, but use an int EXPECTED_NUMBER_OF_ERRORS which is used in an assertion.

### Test profile

`@ActiveProfiles("test")` on `AbstractIntegrationTest` activates the `test` profile for all integration and validation tests, loading `src/main/resources/application-test.properties`. Unit tests don't start Spring and get `baseDir` directly from `BaseTest` via `System.getProperty("user.home")`.

## Plans

Executed implementation plans are saved in `.plans/` at the repo root. Each file is a Markdown document describing context, changes made, files modified, and how to verify.

Filename format: `YYYY-MM-DD-HHMM-<slug>.md`, where the date/time is the commit time of the plan's execution (local time, minute precision). This makes the folder strictly sortable by filename alone — no `git log` needed to disambiguate plans committed on the same day.

## Configuration

`src/main/resources/application.properties` sets:
- `server.port=9777`
- `spring.servlet.multipart.max-file-size=150MB`
- `dedup.upload-dir` — directory for uploaded/output files

### Version number

The version is defined once: `<version>x.y.z</version>` at the top of `pom.xml` (the standard Maven project version). Everything else derives from it at build time:
- `application.properties` contains `spring.application.version=@project.version@` (Maven-filtered at build)
- `ProjectVersionAdvice` reads `${spring.application.version}` and injects `projectVersion` into every Thymeleaf model — consumed by `fragments.html` (navbar, used by all pages) and `index.html` (citing accordion)
- `src/main/cff/citation.cff` is the template; `maven-resources-plugin` filters it to the root `citation.cff` on every build. Both `version` and `date-released` are substituted: `version` from `${project.version}`, `date-released` from `${build.date}` (which captures `${maven.build.timestamp}` formatted as `yyyy-MM-dd`)
- The fat JAR is named `DedupEndNote.jar` (version excluded via `<finalName>${project.artifactId}</finalName>` in `pom.xml`)

**Release workflow:** update `<version>` in `pom.xml` → run `./mvnw package` → commit `pom.xml` and the regenerated root `citation.cff` → add the new `<h2>` + `<ul>` entry to `changelog.html` manually.

## Agent skills

### HTML files with Mermaid diagrams

When writing an HTML file that contains a `<pre class="mermaid">` block, add `<!-- htmlhint-disable -->` as a comment immediately after the opening `<body>` tag. This prevents htmlhint from flagging Mermaid diagram syntax as HTML errors.

### Issue tracker

Issues live as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the default five-role vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
