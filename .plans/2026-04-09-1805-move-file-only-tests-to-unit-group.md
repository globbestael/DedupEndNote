# Plan: Move file-only test classes from integration to unit group

**Status: executed — compile passes, 6 integration / ~14 unit test classes**

## Context

5 of the 11 integration test classes only needed Spring context to get `baseDir`/`testDir` (from `@Value("${baseDir}")`). By initializing `baseDir` in `BaseTest` from `System.getProperty("user.home")` instead, these classes can extend `BaseTest` directly and run as unit tests — no Spring startup required.

Additionally, `AbstractIntegrationTest` was decoupled from `BaseTest` (no integration test uses `BaseTest` utilities), and `baseDir` was removed from `application-test.properties` (no longer needed by any Spring test).

## Classes moved to unit tests

| Class | Why it was integration |
|---|---|
| `AuthorsBaseTest` | needed `baseDir`/`testDir` |
| `AuthorsComparisonThresholdTest` | inherited via `AuthorsBaseTest` |
| `AuthorVariantsExperimentsTest` | inherited via `AuthorsBaseTest` |
| `JournalsBaseTest` | needed `baseDir`/`testDir` |
| `JaroWinklerTitleTest` | needed `baseDir`/`testDir` |

## Classes remaining as integration tests

| Class | Beans injected |
|---|---|
| `DedupEndNoteApplicationTests` | `DeduplicationService` |
| `TwoFilesTest` | `DeduplicationService` |
| `MissedDuplicatesTests` | `DeduplicationService` |
| `AuthorExperimentsTests` | `DeduplicationService` + `simpMessagingTemplate` |
| `ValidationTests` | `DeduplicationService`, `IOService`, `RecordDBService` |
| `JaroWinklerAuthorsTest` | `DeduplicationService` (special case — see below) |

## Changes made

### Step 1 — Add `baseDir`, `testDir`, `initTestDir()` to `BaseTest` ✅

**File:** `src/test/java/edu/dedupendnote/BaseTest.java`

```java
protected String baseDir = System.getProperty("user.home", "") + "/dedupendnote_files";
protected String testDir = "";

@BeforeEach
void initTestDir() { testDir = baseDir; }
```

`System.getProperty("user.home", "")` avoids NullAway warnings.

---

### Step 2 — Restructure `AbstractIntegrationTest` ✅

**File:** `src/test/java/edu/dedupendnote/AbstractIntegrationTest.java`

- Removed `extends BaseTest` — no integration test class uses `BaseTest` utilities
- Removed `@Value("${baseDir}")` — `baseDir` now initialized via system property (same value)
- Added own `baseDir`/`testDir` fields initialized the same way as `BaseTest`
- Kept: `@SpringBootTest`, `@ActiveProfiles("test")`, `@Tag("integration")`, `@MockitoBean SimpMessagingTemplate`, `wssessionId`, `@BeforeAll setLogLevelToInfo()`, `@BeforeEach initTestDir()`

---

### Step 3 — Switch 3 classes from `AbstractIntegrationTest` to `BaseTest` ✅

| Class | File |
|---|---|
| `AuthorsBaseTest` | `services/AuthorsBaseTest.java` |
| `JournalsBaseTest` | `services/JournalsBaseTest.java` |
| `JaroWinklerTitleTest` | `services/JaroWinklerTitleTest.java` |

`AuthorsComparisonThresholdTest` and `AuthorVariantsExperimentsTest` inherit the change via `AuthorsBaseTest` — no modification needed.

---

### Step 4 — Add Spring annotations directly to `JaroWinklerAuthorsTest` ✅

**File:** `src/test/java/edu/dedupendnote/services/JaroWinklerAuthorsTest.java`

Added `@SpringBootTest`, `@ActiveProfiles("test")`, `@Tag("integration")`, and `@MockitoBean SimpMessagingTemplate simpMessagingTemplate` directly. This class extends `AuthorsBaseTest` (now a unit test base class) but needs Spring for `@Autowired DeduplicationService`.

---

### Step 5 — Remove `baseDir` from `application-test.properties` ✅

**File:** `src/main/resources/application-test.properties`

Removed `baseDir = ${user.home}/dedupendnote_files`. All tests now get `baseDir` from their base class.

---

### Step 6 — Update `CLAUDE.md` ✅

Updated the test class hierarchy section to reflect the new structure.

## Files modified

1. `src/test/java/edu/dedupendnote/BaseTest.java`
2. `src/test/java/edu/dedupendnote/AbstractIntegrationTest.java`
3. `src/test/java/edu/dedupendnote/services/AuthorsBaseTest.java`
4. `src/test/java/edu/dedupendnote/services/JournalsBaseTest.java`
5. `src/test/java/edu/dedupendnote/services/JaroWinklerTitleTest.java`
6. `src/test/java/edu/dedupendnote/services/JaroWinklerAuthorsTest.java`
7. `src/main/resources/application-test.properties`
8. `CLAUDE.md`

## Verification

```bash
./mvnw compile test-compile                    # BUILD SUCCESS
./mvnw test -Punit-tests                       # ~14 unit test classes (was 9); 4 pre-existing failures unrelated to this change
./mvnw test -Pintegration-tests                # 6 integration test classes (was 11); 3 pre-existing failures unrelated to this change
```

## Note on pre-existing failures

The following test failures exist independently of this refactoring:
- `AuthorsComparisonThresholdTest.test` — threshold assertion no longer matches current dataset
- `JaroWinklerTitleTest.testPositiveCommentsFromFile` / `testNegativeCommentsFromFile` / `testPositiveCommentsAndRepliesFromFile` — comment/reply detection mismatches
- `DedupEndNoteApplicationTests.deduplicateSmallFiles[1]` / `[3]` — specific test data file issues
- `MissedDuplicatesTests.deduplicateMissedDuplicates[2]` — test data file issue
