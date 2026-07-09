# Testing guide

This is the canonical reference for how tests are organized in DedupEndNote. **New here? Read in this order:**

1. **[CLAUDE.md](../CLAUDE.md) → Commands / Testing** — how to run tests and which Maven profile does what (quick operational lookup).
2. **This file** — what tests exist, the base-class hierarchy, the three-category taxonomy, profile activation, and test utilities.
3. **[CONTEXT.md](../CONTEXT.md) → Validation** — the domain vocabulary the validation tests measure (sensitivity, specificity, TP/FP/FN/TN).

The exact Maven profile path-filters live here (see [Test taxonomy](#test-taxonomy)); CLAUDE.md keeps only the summary table and defers to this file for the glob detail.

## Unit (`edu.dedupendnote.unit.*`)

- **`unit/BaseTest`** — provides `Path baseDir` (`~/dedupendnote_input_files`) and `Path testDir` (both initialized directly as fields), `@BeforeEach initTestDir()`, plus utilities (`jws`, `getHighestSimilarityForAuthors`, `setLoggerToDebug`)
- **`unit/services/BibliographicItemReaderTest extends BaseTest`** — tests the reader's title-derived patterns (`REPLY_PATTERN`, `ERRATUM_PATTERN`, `SOURCE_PATTERN`, `COMMENT_PATTERN`, `PHASE_PATTERN`, `RIS_LINE_PATTERN`); inline `@ValueSource` cases plus file-based tests against the curated `All__*` example sets.
- Standalone unit test classes directly in `unit/services/`: `UtilitiesServiceTest`

- **`unit/services/comparison/AuthorsBaseTest extends BaseTest`** — shared logic for author-comparison tests
- **`unit/services/comparison/DefaultJournalComparisonServiceTest extends BaseTest`** — boolean `compare()` tests for journals (inline parameterized) and a file-based test against validated journal pairs
- **`unit/services/comparison/JWSimilarityTitleTest extends BaseTest`** — title JWS-similarity tests only
- **`unit/services/comparison/JWSimilarityAuthorTest extends AuthorsBaseTest`** — plain JUnit 5, no Spring; tests raw `jws.apply` score
- Standalone comparison test classes (no Spring context): `DefaultJournalComparisonServiceIssnTest`, `DefaultTitleComparisonServiceTest`, `DefaultAuthorsComparisonServiceThresholdTest`, `JWSimilarityJournalTest`, `JWSimilarityAbstractTest`, `AuthorVariantsExperimentsTest`, `AuthorPermutationsExperimentsTest`

- Normalization test classes in `unit/services/normalization/` (no Spring context): `AuthorsNormalizationServiceTest`, `JournalsNormalizationServiceTest`, `PagesNormalizationServiceTest`, `TitlesNormalizationServiceTest`, `NormalizationServiceTextTest`, `NormalizationServiceDoiTest`, `NormalizationServiceIssnTest`

## Integration (`edu.dedupendnote.integration.*`)

Two base classes exist; the choice depends on whether the test needs real HTTP (see ADR-0010):

- **`integration/AbstractIntegrationTest`** — mock web environment (`@SpringBootTest` default); service methods called directly via `@Autowired`. Provides `@ActiveProfiles("test")`, `@MockitoBean SimpMessagingTemplate`, `Path baseDir`, `Path testDir`, `@BeforeAll` (log level → INFO), `@BeforeEach initTestDir()`. Subclasses override `initTestDir()` when they need a subdirectory. Also provides `deleteDerivedOutputs(Path inputPath)` — call this as the first action in any test that writes output files so a failed run cannot leave a previous run's output on disk to mislead a developer. **Default choice** for new integration tests.
- Integration test classes extending `AbstractIntegrationTest`: `DeduplicationServiceTests` (one-file and two-file deduplication smoke tests), `MissedDuplicatesTests`, `RecordCountCapTests`

- **`integration/AbstractRandomPortIntegrationTest`** — real HTTP via `RestTemplate` on `RANDOM_PORT`; exercises the controller's routing, HTTP status codes, and error branches. Use only when the test specifically needs real HTTP or a property override that affects Spring Boot startup behaviour.
- Integration test classes extending `AbstractRandomPortIntegrationTest`: `ConcurrentRunsTests` (semaphore cap → 429), `PathTraversalTests` (upload/getResultFile path traversal → 400; happy-path smoke test), `DeduplicationTimeoutTests` (timeout → 503), `RateLimitTests` (upload rate limit → 429), `CancellationTests` (cancel with no running task → 404; cancel mid-run → ERROR response)

- Integration test classes in `integration/browser/` (no common Spring-context parent; do NOT add `@MockitoBean SimpMessagingTemplate` — real WebSocket required so the browser receives progress messages): `BrowserCancellationTests` (Playwright end-to-end: Cancel button visibility, `#results` WebSocket updates, reading-phase and comparison-phase cancellation). `@ActiveProfiles("test")` declared directly on the class. Run with `-Pbrowser-tests` or `-Pall-integration-tests`; requires Chromium binary.

## Validation (`edu.dedupendnote.validation.*`)

- **`validation/ValidationTests`** — measures sensitivity/specificity of the production deduplication engine across 14 validated real-world datasets; not a regression guard but a performance monitor. Requires truth files in `~/dedupendnote_input_files` (not in git). Run with `-Pvalidation-tests`.
- **`validation/experiments/AuthorExperimentsTests`** — runs `DefaultAuthorsComparisonService` with experimental thresholds (`AuthorThresholds(1.0, 1.0, 1.0)`) against a validated dataset and asserts on relative sensitivity/specificity. The `experiments` sub-package holds controlled A/B experiments against production-engine baselines.
- **`validation/services/ValidationService`** — test-only Spring `@Service` that encapsulates the truth-file scoring logic (TP/FP/FN/TN computation, FN/FP analysis file writing). Shared by `ValidationTests` and future experiments tests.
- **`validation/services/RecordDBService`** — test-only Spring `@Service` for reading/writing the tab-delimited DB export format.
- **`validation/domain/ValidationResult`** — POJO holding per-dataset scores (sensitivity, specificity, precision, accuracy, F1, FN/FP pair maps).

## Test utilities (`edu.dedupendnote.integration.utils.*`)

Helpers, not test classes (no `@Test` methods), so they are not covered by the Maven profile path filters directly — they are compiled and used by the classes that reference them.

- **`integration/utils/MemoryAppender`** — a Logback `ListAppender<ILoggingEvent>` that buffers log events in memory and exposes query helpers (`contains`, `search`, `filterByPattern(s)`, `showMessages`, …) for asserting on or extracting logged trace.
- **`integration/utils/TraceLogCapture`** — `AutoCloseable` fixture that owns the deduplication-trace capture lifecycle: `attach()` raises the canonical set of comparison loggers (`TRACE_LOGGER_NAMES`) to `TRACE` and wires up a `MemoryAppender`; `close()` restores each logger's original level (per-logger) and detaches the appender. Holds the single source of truth for the logger list and the trace-line `TRACE_PATTERNS` (step 0 = year pre-check, steps 1-4 = the comparison algorithm). Used by `MissedDuplicatesTests` (via `@BeforeEach`/`@AfterEach`) and `ValidationService#writeFNandFPresults` (via try-with-resources).

- **`integration/utils/TraceLogCapture`** — `AutoCloseable` fixture that owns the deduplication-trace capture lifecycle: `attach()` raises the canonical set of comparison loggers (`TRACE_LOGGER_NAMES`) to `TRACE` and wires up a `MemoryAppender`; `close()` restores each logger's original level (per-logger) and detaches the appender. Holds the single source of truth for the logger list and the trace-line `TRACE_PATTERNS` (step 0 = year pre-check, steps 1-4 = the comparison algorithm). Used by `MissedDuplicatesTests` (via `@BeforeEach`/`@AfterEach`) and `ValidationService#writeFNandFPresults` (via try-with-resources).

The TraceLogCapture shows the outcome of the positive and negative steps in the algorithm. In 'MissedDuplicatesTests' in the log output on screen and 'logs/dedupendnote_tests.log', in 'ValidationTests::checkAllTruthFiles()' there is a test outputfile for all False Positives and for all False Negatives with this trace output.

## Test taxonomy

Test files follow a three-category taxonomy per field: **Normalization** (`*NormalizationServiceTest` for concrete service classes; `NormalizationService*Test` for base-class topics like DOI, ISSN, text) / **Comparison** (`Default*ComparisonServiceTest`, boolean result from `compare()` or equivalent static helpers) / **JWSimilarity** (`JWSimilarity*Test`, raw JWS score vs threshold). Files are further split by Spring-context requirement and mirror the production subfolder structure (`services/comparison/`, `services/normalization/`).

The Maven profiles in `pom.xml` use path-based filters: `unit-tests` (excludes `**/integration/**` and `**/validation/**`), `integration-tests` (includes `**/integration/**/*Tests.java`, excludes `**/integration/browser/**`), `browser-tests` (includes only `**/integration/browser/**/*Tests.java`), `all-integration-tests` (includes all of `**/integration/**/*Tests.java`), `validation-tests` (includes only `**/validation/**/*Tests.java`). Selecting the folder in VS Code's Test Explorer automatically runs only that category.

Tests with expected errors are NOT disabled — they use an `int EXPECTED_NUMBER_OF_ERRORS` constant in the assertion.

## Test profile

`@ActiveProfiles("test")` activates the `test` profile for all integration and validation tests, loading `src/main/resources/application-test.properties`. It is declared on `AbstractIntegrationTest` (inherited by subclasses), repeated on each `AbstractRandomPortIntegrationTest` subclass (which has no common Spring-context parent), and declared directly on `BrowserCancellationTests` (which extends no base class). Unit tests don't start Spring and get `baseDir` directly from `BaseTest` via `System.getProperty("user.home")`.
