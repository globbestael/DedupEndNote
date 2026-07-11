# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Keeping this file current

Update CLAUDE.md (same commit as the code change) when any of the following change:

- Test class renamed, added, deleted, or reclassified → also update `docs/testing-guide.md`
- New service introduced or existing service's responsibility changed → also update `docs/architecture.html`
- Algorithm step, threshold, or special-type handling changed → also update `docs/algorithm.md`
- Domain term or mode definition changed → also update `CONTEXT.md`
- Build command, Maven profile, or port changed (commands / configuration sections)
- Architectural pattern added or removed (naming conventions, path helpers)
- Code quality plugin version bumped or new plugin added
- Plan-file naming convention changed (plans section)
- Release workflow or version-management mechanism changed (configuration section)
- Doc file added to `docs/` or existing one renamed/removed (documentation map section)

## Documentation map

| What you need | Where to find it |
|---|---|
| Domain term meanings (Bibliographic Item, Duplicate Set, Modes, item types, validation) | [CONTEXT.md](CONTEXT.md) |
| Pipeline diagram, service responsibilities, sequence diagram | [docs/architecture.html](docs/architecture.html) |
| Algorithm steps, threshold values, INSUFFICIENT_DATA, special types, enrichment | [docs/algorithm.md](docs/algorithm.md) |
| Test class listing, base class details, taxonomy, test profile, how to run tests | [docs/testing-guide.md](docs/testing-guide.md) |
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
./mvnw test -Pintegration-tests               # Run only integration tests (@SpringBootTest), excludes browser/
./mvnw test -Pbrowser-tests                  # Run browser tests only (requires Chromium — see below)
./mvnw test -Pall-integration-tests          # Run all integration tests including browser (requires Chromium)
./mvnw test -Pvalidation-tests               # Run only validation tests (slow, requires truth files)
# One-time Chromium install for browser tests:
./mvnw exec:java -e -D exec.classpathScope=test -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
./mvnw -Dtest=ClassNameTest test              # Run a single test class
./mvnw -Dtest=ClassNameTest#methodName test   # Run a single test method

# Security scanning
./mvnw verify -DskipTests                      # Run SpotBugs + Find Security Bugs (skip tests for speed)
./mvnw dependency-check:check                  # Run OWASP dependency CVE scan (slow, needs NVD download)

# Coverage
# JaCoCo report is written to target/site/jacoco/ after any test run except -Pvalidation-tests
# VS Code: Coverage Gutters extension reads target/site/jacoco/jacoco.xml → Display Coverage
```

## Architecture

DedupEndNote is a Spring Boot 4.1 / Java 21 web app that deduplicates bibliographic records in RIS format (exported from EndNote, Zotero, PubMed, EMBASE, etc.). Runs on port 9777, deployed as a fat JAR.

See [`docs/architecture.html`](docs/architecture.html) for the pipeline diagram, full service map, and runtime sequence diagram.

### Key packages
- `controllers/` — HTTP endpoints; file upload and dedup triggers; delegates run lifecycle (concurrency cap, timeout, cancel) to `BoundedDedupRunner`; WebSocket progress routing
- `domain/` — `BibliographicItem` (core model), `BibliographicItemDB` (in-memory store), `DeduplicationMode` (enum: `REMOVE` / `MARK`)
- `services/` — `DeduplicationService`, `BibliographicItemReader`, `BibliographicItemWriter`, `EnrichmentService`, `UtilitiesService`, `BoundedDedupRunner` (run orchestration: bounded concurrency permit + per-run timeout + cancel-by-session; guarantees permit release on every outcome)
- `services/comparison/` — four `*ComparisonService` interfaces, four `Default*ComparisonService` implementations, three `*Thresholds` value objects, `FieldComparators` record, `BoundedPatternCache` (size-bounded LRU of compiled journal-name patterns)
- `services/normalization/` — `NormalizationService` plus four `*NormalizationService` classes

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

One test-phase plugin runs automatically on every `./mvnw test` (skipped for `-Pvalidation-tests`):
- **JaCoCo** (v0.8.12): instruments bytecode and writes coverage data to `target/site/jacoco/`. Excluded from the report: `DedupEndNoteApplication`, `WebConfig`, `WebSocketConfig`, `ProjectVersionAdvice`, and `package-info` stubs. Lombok-generated methods are excluded automatically via `@lombok.Generated`. The `argLine` property in `pom.xml` uses `@{argLine}` late-binding so JaCoCo's agent string is injected at execution time.

One verify-phase plugin runs during `./mvnw verify`:
- **SpotBugs + Find Security Bugs** (v4.10.2.0 / findsecbugs v1.14.0): scans compiled bytecode for OWASP Top 10 security patterns. Only `SECURITY`-category bugs are reported (see `spotbugs-security-include.xml`). Known false positives and accepted-risk findings are documented in `spotbugs-security-exclude.xml`.

## Testing

Tests live under four roots, each with a corresponding Maven profile:

| Folder | Profile | Spring context | Run frequency |
|---|---|---|---|
| `src/test/java/edu/dedupendnote/unit/` | `unit-tests` | No | Every commit |
| `src/test/java/edu/dedupendnote/integration/` | `integration-tests` | `@SpringBootTest` | Every commit |
| `src/test/java/edu/dedupendnote/integration/browser/` | `browser-tests` / `all-integration-tests` | `@SpringBootTest(RANDOM_PORT)`, real WebSocket | On demand (Chromium required) |
| `src/test/java/edu/dedupendnote/validation/` | `validation-tests` | `@SpringBootTest` | On demand |

Note: `-Pintegration-tests` excludes `integration/browser/`; `-Pall-integration-tests` includes it. The exact glob path-filters for every profile are documented once, in [`docs/testing-guide.md`](docs/testing-guide.md) — don't restate them here.

**Integration tests** assert on the string returned by `deduplicateOneFile` (or record counts) on small known inputs — they are regression guards that fail if behaviour changes.

**Validation tests** measure sensitivity/specificity against manually validated truth files in `~/dedupendnote_input_files` (not in git). They are slow and intended to be run before releases or after structural changes, not on every commit.

### Full testing guide

**[`docs/testing-guide.md`](docs/testing-guide.md) is the canonical testing reference** — start there for the per-class listing, base-class hierarchy, taxonomy, profile path-filters, and test utilities. It also carries the "new developer, read in this order" entry point.

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

- **Mermaid HTML files**: add `<!-- htmlhint-disable -->` after `<body>` to suppress htmlhint errors on Mermaid diagram syntax.
- **Issue tracker**: issues live under `.scratch/` — see [`docs/agents/issue-tracker.md`](docs/agents/issue-tracker.md)
- **Triage labels**: five-role vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix) — see [`docs/agents/triage-labels.md`](docs/agents/triage-labels.md)
- **Domain docs**: single-context layout (`CONTEXT.md` + `docs/adr/`) — see [`docs/agents/domain.md`](docs/agents/domain.md)
