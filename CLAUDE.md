# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./mvnw clean package          # Build fat JAR
./mvnw spring-boot:run        # Run locally (port 9777)

# Test
./mvnw test                                    # Run all tests
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
- `controllers/` — HTTP endpoints; file upload and dedup triggers; uses virtual threads (Java 21) for concurrent dedup runs
- `domain/` — `Publication` (core model), `PublicationDB` (in-memory store), `NormPatterns` (50+ compiled regex patterns)
- `services/` — business logic (see below)

### Services and their responsibilities
| Service | Lines | Responsibility |
|---|---|---|
| `DeduplicationService` | ~547 | Orchestrates the full pipeline; sends WebSocket (STOMP) progress messages |
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

Tests live in `src/test/java/edu/dedupendnote/services/`. Many tests validate against real-world datasets (SRA, McKeown, BIG_SET) and measure sensitivity/specificity. The test Spring profile is activated via `src/test/resources/application.properties` (`spring.profiles.active=test`), which loads `application-test.properties`. That file defines `baseDir = ${user.home}/dedupendnote_files`. Test classes inject it with `@Value("${baseDir}") String baseDir = ""` and derive a `testDir` field in `@BeforeEach`.

## Plans

Executed implementation plans are saved in `.plans/` at the repo root. Each file is a Markdown document describing context, changes made, files modified, and how to verify.

## Configuration

`src/main/resources/application.properties` sets:
- `server.port=9777`
- `spring.servlet.multipart.max-file-size=150MB`
- `dedup.upload-dir` — directory for uploaded/output files
