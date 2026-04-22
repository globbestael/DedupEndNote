# Plan: Reduce test setup duplication with AbstractIntegrationTest

**Status: executed — `./mvnw compile test-compile` passes (BUILD SUCCESS)**

## Context

After the baseDir/testDir refactoring, 9 `@SpringBootTest` test classes shared a nearly identical setup: `@SpringBootTest`, `@ActiveProfiles("test")`, `@MockitoBean SimpMessagingTemplate`, `@Value("${baseDir}")`, `String testDir`, `String wssessionId`, and `@BeforeEach initTestDir()`. Several also duplicated a `@BeforeAll` that set the log level to INFO. The goal was to consolidate this into an abstract base class while also setting up a clear hierarchy to support future separation into unit tests vs integration tests.

## Changes made

### Step 1 — Clean up `BaseTest` ✅

Removed `@TestConfiguration` (incorrect for a base class). Kept `@Slf4j` and the utility methods (`jws`, `getHighestSimilarityForAuthors`, `setLoggerToDebug`).

**File:** `src/test/java/edu/dedupendnote/BaseTest.java`

---

### Step 2 — Create `AbstractIntegrationTest` ✅

New abstract base class for all `@SpringBootTest` tests:

```java
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest extends BaseTest {

    @MockitoBean
    protected SimpMessagingTemplate simpMessagingTemplate;

    @Value("${baseDir}")
    protected String baseDir = "";

    protected String testDir = "";

    protected String wssessionId = "";

    @BeforeAll
    static void setLogLevelToInfo() { ... }

    @BeforeEach
    void initTestDir() {
        testDir = baseDir;
    }
}
```

Fields are `protected` so subclasses in the `edu.dedupendnote.services` package can access them.

**File:** `src/test/java/edu/dedupendnote/AbstractIntegrationTest.java` *(new)*

---

### Step 3 — Update integration test classes

| Class | Status | Changes |
|---|---|---|
| `DedupEndNoteApplicationTests` | ✅ | Extends `AbstractIntegrationTest`; removed `@SpringBootTest`, `@ActiveProfiles`, `@MockitoBean`, `@Value baseDir`, `testDir`, `wssessionId`, `@BeforeAll`; overrides `initTestDir()` → `testDir = baseDir + "/experiments/"` |
| `TwoFilesTest` | ✅ | Same as above (no `@BeforeAll` to remove) |
| `MissedDuplicatesTests` | ✅ | Extends `AbstractIntegrationTest` (previously `BaseTest`); removed duplicate setup and local `initTestDir()` (identical to base); kept `addMemoryAppender()` |
| `ValidationTests` | ✅ | Extends `AbstractIntegrationTest`; removed `@SpringBootTest`, `@ActiveProfiles`, `@MockitoBean`, `@Value baseDir`, `testDir`, `wssessionId`; kept own `@BeforeAll` (assigns the logger to a class field for dynamic level changes) |
| `AuthorExperimentsTests` | ✅ | Extends `AbstractIntegrationTest`; removed duplicate setup |
| `AuthorsBaseTest` | ✅ | Extends `AbstractIntegrationTest` (previously `BaseTest`); removed `@SpringBootTest`, `@ActiveProfiles`, `@Value baseDir`, `testDir`, `@BeforeEach initTestDir()` |
| `JournalsBaseTest` | ✅ | Extends `AbstractIntegrationTest` (previously `BaseTest`); removed same + `@BeforeAll beforeAll()` |
| `JaroWinklerTitleTest` | ✅ | Extends `AbstractIntegrationTest`; removed duplicate setup and shadow `jws` field |
| `JaroWinklerAuthorsTest` | ✅ | Removed shadow `jws` field, `@SpringBootTest`, `@ActiveProfiles`, `@MockitoBean` (all inherited via `AuthorsBaseTest → AbstractIntegrationTest`) |

---

### Step 4 — Remove redundant `@BeforeAll beforeAll()` ✅

The identical "set log level to INFO" `@BeforeAll` was removed from:
- `DedupEndNoteApplicationTests` — removed
- `JournalsBaseTest` — removed
- `AuthorVariantsExperimentsTest` — removed (inherits via `AuthorsBaseTest`)
- `AuthorsComparisonThresholdTest` — removed (inherits via `AuthorsBaseTest`)

`ValidationTests` keeps its own `@BeforeAll` (stores the logger in a class field for dynamic level switching during tests).

---

### Step 5 — Remove shadow `jws` fields ✅

- `JaroWinklerTitleTest`: removed `JaroWinklerSimilarity jws = new JaroWinklerSimilarity()` (inherits `protected jws` from `BaseTest`)
- `JaroWinklerAuthorsTest`: removed same shadow field

---

### Step 6 — Fix `@TestConfiguration` on `AuthorsComparisonThresholdTest` ✅

Was incorrectly annotated with `@TestConfiguration`. Removed the annotation (class inherits `@SpringBootTest` from `AbstractIntegrationTest` via `AuthorsBaseTest`).

---

### Step 7 — Unit test classes — no changes ✅

These classes don't use `@SpringBootTest` and stayed standalone:
`ComparisonServiceTest`, `DoiTest`, `PagesTest`, `NormalizationServiceTest`, `NormalizationServiceTitleTest`, `TextNormalizerTest`, `AbstracttextTest`, `JaroWinklerJournalTest`, `AuthorPermutationsExperimentsTest`

These naturally form the "unit test" group for future separation.

## Files modified

1. `src/test/java/edu/dedupendnote/BaseTest.java` — removed `@TestConfiguration`
2. `src/test/java/edu/dedupendnote/AbstractIntegrationTest.java` — **new file**
3. `src/test/java/edu/dedupendnote/DedupEndNoteApplicationTests.java`
4. `src/test/java/edu/dedupendnote/TwoFilesTest.java`
5. `src/test/java/edu/dedupendnote/MissedDuplicatesTests.java`
6. `src/test/java/edu/dedupendnote/services/ValidationTests.java`
7. `src/test/java/edu/dedupendnote/services/AuthorExperimentsTests.java`
8. `src/test/java/edu/dedupendnote/services/AuthorsBaseTest.java`
9. `src/test/java/edu/dedupendnote/services/JournalsBaseTest.java`
10. `src/test/java/edu/dedupendnote/services/JaroWinklerTitleTest.java`
11. `src/test/java/edu/dedupendnote/services/JaroWinklerAuthorsTest.java`
12. `src/test/java/edu/dedupendnote/services/AuthorVariantsExperimentsTest.java` — removed `@BeforeAll`
13. `src/test/java/edu/dedupendnote/services/AuthorsComparisonThresholdTest.java` — removed `@BeforeAll` and `@TestConfiguration`

## Verification

```bash
./mvnw compile test-compile   # BUILD SUCCESS — 27 test source files compiled
./mvnw test                   # run to verify all tests pass
```
