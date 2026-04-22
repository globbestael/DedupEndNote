# Plan: Tag and group tests into unit vs integration

**Status: executed — `./mvnw test -Punit-tests` passes (BUILD SUCCESS, 380 tests)**

## Context

The `AbstractIntegrationTest` refactoring created a clean hierarchy for all `@SpringBootTest` tests. This plan makes the unit/integration split explicit so both groups can be run independently — fast unit tests during development, full integration tests in CI.

There were no `@Tag` annotations, no Maven Surefire configuration, and no Maven profiles before this change.

## Changes made

### Step 1 — Add `@Tag("integration")` to `AbstractIntegrationTest` ✅

**File:** `src/test/java/edu/dedupendnote/AbstractIntegrationTest.java`

Added `@Tag("integration")` to the class. All 11 subclasses inherit it automatically:

| Integration test classes (inherit tag via hierarchy) |
|---|
| `DedupEndNoteApplicationTests` |
| `TwoFilesTest` |
| `MissedDuplicatesTests` |
| `AuthorExperimentsTests` |
| `ValidationTests` |
| `JaroWinklerTitleTest` |
| `AuthorsBaseTest` |
| `AuthorsComparisonThresholdTest` (via `AuthorsBaseTest`) |
| `AuthorVariantsExperimentsTest` (via `AuthorsBaseTest`) |
| `JaroWinklerAuthorsTest` (via `AuthorsBaseTest`) |
| `JournalsBaseTest` |

Unit test classes (9 standalone classes with no Spring context) carry no tag — they are implicitly the "unit" group.

---

### Step 2 — Add Maven profiles to `pom.xml` ✅

**File:** `pom.xml`

Added a `<profiles>` section at the end of the file with two profiles:

- **`unit-tests`**: configures Surefire to `<excludedGroups>integration</excludedGroups>`
- **`integration-tests`**: configures Surefire to `<groups>integration</groups>`

Default behavior (`./mvnw test`, no profile) is unchanged — all tests run.

---

### Step 3 — Update `CLAUDE.md` ✅

**File:** `CLAUDE.md`

- Added `./mvnw test -Punit-tests` and `./mvnw test -Pintegration-tests` to the Commands section
- Added a note in the Testing section explaining the `@Tag("integration")` / Maven profile mechanism

---

## Files modified

1. `src/test/java/edu/dedupendnote/AbstractIntegrationTest.java` — added `@Tag("integration")`
2. `pom.xml` — added `<profiles>` with `unit-tests` and `integration-tests`
3. `CLAUDE.md` — documented new commands and tag/profile setup

## Verification

```bash
./mvnw compile test-compile          # BUILD SUCCESS
./mvnw test -Punit-tests             # 380 unit tests pass, 9 integration tests skipped
./mvnw test -Pintegration-tests      # runs only the 11 integration test classes
./mvnw test                          # all tests run (unchanged default)
```

## Note

`JaroWinklerTitleTest` has a `FIXME` comment (added by the user) noting it is a candidate for splitting: most of its tests are pure unit tests, but a small number read external files via `testDir` — which is why the whole class currently extends `AbstractIntegrationTest` and carries the `integration` tag.
