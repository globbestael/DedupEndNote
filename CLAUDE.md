# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- The section below is deliberately brief and trigger-based rather than prescriptive.
     The list covers the five things that actually went stale in this project
     (the test hierarchy was the one that just bit us). Placing it before "Commands"
     means it appears early enough to be read before any task begins, not buried
     after the architecture description. -->
## Keeping this file current

Update CLAUDE.md whenever a change affects something documented here. Triggers include:

- Test class renamed, added, deleted, or reclassified (hierarchy section), or moved between unit / integration / validation categories
- New service introduced or existing service's responsibility changed (services table)
- Build command, Maven profile, or port changed (commands / configuration sections)
- Architectural pattern added or removed (data flow, enrichment, modes)
- Code quality plugin version bumped or new plugin added
- Plan-file naming convention changed (plans section)
- Release workflow or version-management mechanism changed (configuration section)

The update should land in the same commit as the code change.

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

DedupEndNote is a Spring Boot 4.0 / Java 21 web app that deduplicates bibliographic records in RIS format (exported from EndNote, Zotero, PubMed, EMBASE, etc.). It runs on port 9777 and is deployed as a fat JAR.

### Modes
- **Single-file dedup**: removes duplicates within one RIS file
- **Two-file dedup**: compares new records against existing ones, only outputs the new file with duplicates marked or removed
- **Mark mode**: keeps all records but labels duplicates with the ID of the kept record

### Key packages
- `controllers/` — HTTP endpoints; file upload and dedup triggers; uses virtual threads (Java 21) for concurrent dedup runs; creates the `Consumer<String>` that routes progress messages to WebSocket
- `domain/` — `Publication` (core model), `PublicationDB` (in-memory store), `NormPatterns` (50+ compiled regex patterns)
- `services/` — business logic (see below)

### Services and their responsibilities
| Service | Lines | Responsibility |
|---|---|---|
| `DeduplicationService` | ~547 | Orchestrates the full pipeline; accepts a `Consumer<String> progressReporter` for progress reporting |
| `ComparisonService` | ~90 | Thin orchestrator: holds four injected per-field comparison services; retains `compareIssns` and `compareSameDois` as static helpers |
| `IOService` | ~980 | Parses and writes RIS files; normalizes fields during read |
| `NormalizationService` | ~991 | Normalizes authors, titles, DOIs, pages, journals |
| `DefaultAuthorsComparisonService` | — | Jaro-Winkler author matching; thresholds injectable via `AuthorThresholds` record |
| `DefaultTitleComparisonService` | — | JWS title matching; thresholds injectable via `TitleThresholds` record |
| `DefaultJournalComparisonService` | — | Journal matching with abbreviation/initialism heuristics; thresholds injectable via `JournalThresholds` record |
| `DefaultPagesComparisonService` | — | Exact-equality pages-or-DOI step (no thresholds) |

### 5-step comparison algorithm (all steps must pass)
1. Publication year (±1 year, exact for Cochrane)
2. Starting page or DOI match
3. Authors (Jaro-Winkler > 0.67)
4. Title (Jaro-Winkler > 0.89)
5. ISBN/ISSN or journal name match

### Data flow
```
Upload RIS file(s)
  → DeduplicationService (virtual thread)
    → IOService.readPublications()   — parse + normalize all fields
    → ComparisonService              — O(n²) pair comparison, year-bucketed
    → DeduplicationService           — mark/remove duplicates, enrich kept records
    → IOService.writeOutput()        — write result RIS file
  → WebSocket progress messages → client
  → User downloads result
```

### Record enrichment (non-mark mode)
When a duplicate is found the kept record is enriched with data from the duplicate: missing DOI, missing year, missing/abbreviated pages, short titles replaced with full titles.

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
- **`unit/BaseTest`** — provides `baseDir` (from `System.getProperty("user.home") + "/dedupendnote_files"`), `testDir`, `@BeforeEach initTestDir()`, plus utilities (`jws`, `getHighestSimilarityForAuthors`, `setLoggerToDebug`)
- **`unit/services/AuthorsBaseTest extends BaseTest`** — shared logic for author-comparison tests
- **`unit/services/JournalsBaseTest extends BaseTest`** — shared logic for journal-comparison tests
- **`unit/services/JWSimilarityTitleTest extends BaseTest`** — title JWS-similarity tests; also holds the out-of-scope `IOService` pattern tests
- **`unit/services/JWSimilarityAuthorTest extends AuthorsBaseTest`** — plain JUnit 5, no Spring; tests raw `jws.apply` score
- Standalone unit test classes (no Spring context): `ComparisonServiceTest`, `NormalizationService*Test` (6 files), `SimilarityJournalTest`, `JWSimilarityJournalTest`, `JWSimilarityAbstractTest`, `AuthorsComparisonThresholdTest`, `AuthorVariantsExperimentsTest`, etc.

**Integration (`edu.dedupendnote.integration.*`)**
- **`integration/AbstractIntegrationTest`** — base for all `@SpringBootTest` tests; provides `@ActiveProfiles("test")`, `@MockitoBean SimpMessagingTemplate`, `baseDir`, `testDir`, `@BeforeAll` (log level → INFO), `@BeforeEach initTestDir()`. Subclasses override `initTestDir()` when they need a subdirectory.
- Integration test classes extending `AbstractIntegrationTest`: `DedupEndNoteApplicationTests`, `MissedDuplicatesTests`, `TwoFilesTests`

**Validation (`edu.dedupendnote.validation.*`)**
- **`validation/ValidationTests`** — measures sensitivity/specificity of the production deduplication engine across 14 validated real-world datasets; not a regression guard but a performance monitor. Requires truth files in `~/dedupendnote_files` (not in git). Run with `-Pvalidation-tests`.
- **`validation/experiments/AuthorExperimentsTests`** — runs `DefaultAuthorsComparisonService` with experimental thresholds (`AuthorThresholds(1.0, 1.0, 1.0)`) against a validated dataset and asserts on relative sensitivity/specificity. The `experiments` sub-package holds controlled A/B experiments against production-engine baselines.
- **`validation/services/ValidationService`** — test-only Spring `@Service` that encapsulates the truth-file scoring logic (TP/FP/FN/TN computation, FN/FP analysis file writing). Shared by `ValidationTests` and future experiments tests.
- **`validation/services/RecordDBService`** — test-only Spring `@Service` for reading/writing the tab-delimited DB export format.
- **`validation/domain/ValidationResult`** — POJO holding per-dataset scores (sensitivity, specificity, precision, accuracy, F1, FN/FP pair maps).

Test files follow a three-category taxonomy per field: **Normalization** (`NormalizationService*Test`) / **Similarity** (`Similarity*Test`, boolean/equality result) / **JWSimilarity** (`JWSimilarity*Test`, raw JWS score vs threshold). Files are further split by Spring-context requirement.

The split is enforced by folder. The Maven profiles in `pom.xml` use path-based filters: `unit-tests` (excludes `**/integration/**` and `**/validation/**`), `integration-tests` (includes only `**/integration/**/*Tests.java`), `validation-tests` (includes only `**/validation/**/*Tests.java`). Selecting the folder in VS Code's Test Explorer automatically runs only that category.

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

The user-facing version is defined once: `<app.version>x.y.z</app.version>` in `pom.xml`'s `<properties>` section. Everything else derives from it at build time:
- `application.properties` contains `app.version=@app.version@` (Maven-filtered at build)
- `AppVersionAdvice` reads `${app.version}` and injects `appVersion` into every Thymeleaf model — consumed by `index.html` (navbar + citing accordion) and `twofiles.html` (navbar)
- `src/main/cff/citation.cff` is the template; `maven-resources-plugin` filters it to the root `citation.cff` on every build

**Release workflow:** update `<app.version>` in `pom.xml` → run `./mvnw package` → commit `pom.xml` and the regenerated root `citation.cff` → add the new `<h2>` + `<ul>` entry to `changelog.html` manually.

## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the default five-role vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
