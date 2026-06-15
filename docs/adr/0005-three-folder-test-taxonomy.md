# ADR-0005 Three-folder test taxonomy with path-based Maven profiles

**Status:** Decided — stabilized in 1.1.6 (2026-05-20)  
**Date:** 2026-05-20  
**Context:** The test suite grew to include tests with very different performance characteristics and external-dependency requirements. A single test run was too slow for commit-time feedback loops.

## Decision

Split all tests across three source roots, each with a dedicated Maven profile:

| Folder | Profile | Spring context | When to run |
|---|---|---|---|
| `src/test/java/edu/dedupendnote/unit/` | `unit-tests` | None | Every commit |
| `src/test/java/edu/dedupendnote/integration/` | `integration-tests` | `@SpringBootTest` | Every commit |
| `src/test/java/edu/dedupendnote/validation/` | `validation-tests` | `@SpringBootTest` | Before release / structural changes |

Maven profiles use **path-based filters** rather than annotation-based tags:
- `unit-tests` excludes `**/integration/**` and `**/validation/**`
- `integration-tests` includes only `**/integration/**/*Tests.java`
- `validation-tests` includes only `**/validation/**/*Tests.java`

Within unit tests, a further three-category taxonomy applies per field: **Normalization** / **Comparison** / **JWSimilarity** — each in its own test class, split further by whether a Spring context is needed.

Validation tests require truth files in `~/dedupendnote_files` (not in git) and are intentionally excluded from CI. They measure sensitivity/specificity across 14 real-world datasets and are run on demand before releases.

## Alternatives considered

### 1. JUnit 5 `@Tag` annotations with a single source tree

All tests in one folder, annotated with `@Tag("unit")`, `@Tag("integration")`, `@Tag("validation")`. Maven Surefire's `groups` / `excludedGroups` parameters select the active set.

**Rejected** because:
- Tags are invisible in the file system; folder structure is visible in the IDE test explorer and `git status` output without any additional tooling.
- A developer can accidentally run the wrong set if tags are forgotten on a new test class.
- VS Code's Test Explorer respects folder structure natively; tag-based filtering requires extra IDE configuration.
- Path-based Maven profiles are simpler to configure and less error-prone than annotation-based include/exclude lists.

### 2. Two-tier split (unit vs everything else)

Unit tests fast, all `@SpringBootTest` tests in one folder.

**Rejected** because: validation tests are orders of magnitude slower than integration tests (they read large truth files not checked into git) and have an external dependency on `~/dedupendnote_files`. Putting them in the same folder as integration tests would mean either always running slow tests at commit time or maintaining a fragile annotation-based exclusion.

## What to watch for (conditions that would reopen this)

- Validation truth files are eventually checked into the repo (e.g. as git-lfs) and the build environment can always find them — at that point the on-demand restriction could be relaxed and validation tests promoted to every-commit.
- A fourth test category is needed (e.g. performance benchmarks using JMH) — add a fourth folder `benchmark/` with its own profile rather than mixing into `validation/`.
