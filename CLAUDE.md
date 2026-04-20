# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- The section below is deliberately brief and trigger-based rather than prescriptive.
     The list covers the five things that actually went stale in this project
     (the test hierarchy was the one that just bit us). Placing it before "Commands"
     means it appears early enough to be read before any task begins, not buried
     after the architecture description. -->
## Keeping this file current

Update CLAUDE.md whenever a change affects something documented here. Triggers include:

- Test class renamed, added, deleted, or reclassified (hierarchy section)
- New service introduced or existing service's responsibility changed (services table)
- Build command, Maven profile, or port changed (commands / configuration sections)
- Architectural pattern added or removed (data flow, enrichment, modes)
- Code quality plugin version bumped or new plugin added

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
| `ComparisonService` | ~337 | 5-step duplicate detection algorithm |
| `IOService` | ~980 | Parses and writes RIS files; normalizes fields during read |
| `NormalizationService` | ~991 | Normalizes authors, titles, DOIs, pages, journals |
| `DefaultAuthorsComparisonService` | — | Jaro-Winkler author matching |

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

Tests live under `src/test/java/edu/dedupendnote/unit/` (no Spring context) and `src/test/java/edu/dedupendnote/integration/` (Spring Boot tests). Many tests validate against real-world datasets (SRA, McKeown, BIG_SET) and measure sensitivity/specificity.

### Test class hierarchy

**Unit (`edu.dedupendnote.unit.*`)**
- **`unit/BaseTest`** — provides `baseDir` (from `System.getProperty("user.home") + "/dedupendnote_files"`), `testDir`, `@BeforeEach initTestDir()`, plus utilities (`jws`, `getHighestSimilarityForAuthors`, `setLoggerToDebug`)
- **`unit/services/AuthorsBaseTest extends BaseTest`** — shared logic for author-comparison tests; public so `SimilarityAuthorTest` (integration) can extend it
- **`unit/services/JournalsBaseTest extends BaseTest`** — shared logic for journal-comparison tests
- **`unit/services/JWSimilarityTitleTest extends BaseTest`** — title JWS-similarity tests; also holds the out-of-scope `IOService` pattern tests
- **`unit/services/JWSimilarityAuthorTest extends AuthorsBaseTest`** — plain JUnit 5, no Spring; tests raw `jws.apply` score
- Standalone unit test classes (no Spring context): `ComparisonServiceTest`, `NormalizationService*Test` (6 files), `SimilarityJournalTest`, `JWSimilarityJournalTest`, `JWSimilarityAbstractTest`, `AuthorsComparisonThresholdTest`, `AuthorVariantsExperimentsTest`, etc.

**Integration (`edu.dedupendnote.integration.*`)**
- **`integration/AbstractIntegrationTest`** — base for all `@SpringBootTest` tests; provides `@ActiveProfiles("test")`, `@MockitoBean SimpMessagingTemplate`, `baseDir`, `testDir`, `@BeforeAll` (log level → INFO), `@BeforeEach initTestDir()`. Subclasses override `initTestDir()` when they need a subdirectory.
- **`integration/services/SimilarityAuthorTest extends AuthorsBaseTest`** — has its own `@SpringBootTest`; tests `authorsComparisonService.compare` (boolean result)
- Integration test classes extending `AbstractIntegrationTest`: `DedupEndNoteApplicationTests`, `MissedDuplicatesTests`, `TwoFilesTest`, `AuthorExperimentsTests`, `ValidationTests`

Test files follow a three-category taxonomy per field: **Normalization** (`NormalizationService*Test`) / **Similarity** (`Similarity*Test`, boolean/equality result) / **JWSimilarity** (`JWSimilarity*Test`, raw JWS score vs threshold). Files are further split by Spring-context requirement.

The split is enforced by folder: `unit/` vs `integration/`. The two Maven profiles in `pom.xml` use path-based filters: `unit-tests` (excludes `**/integration/**`) and `integration-tests` (includes only `**/integration/**/*Test(s).java`). Selecting the folder in VS Code's Test Explorer automatically runs only that category.

### Test profile

`@ActiveProfiles("test")` on `AbstractIntegrationTest` activates the `test` profile for all integration tests, loading `src/main/resources/application-test.properties`. Unit tests don't start Spring and get `baseDir` directly from `BaseTest` via `System.getProperty("user.home")`.

## Plans

Executed implementation plans are saved in `.plans/` at the repo root. Each file is a Markdown document describing context, changes made, files modified, and how to verify.

## Configuration

`src/main/resources/application.properties` sets:
- `server.port=9777`
- `spring.servlet.multipart.max-file-size=150MB`
- `dedup.upload-dir` — directory for uploaded/output files
