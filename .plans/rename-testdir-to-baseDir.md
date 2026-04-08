# Plan: Rename and consolidate testdir properties

**Status: executed — committed `83b249a`**

## Context

There were two things called "testdir" with different meanings:
1. A **property** in `application-test.properties`: `testdir = ${user.home}/dedupendnote_files` — the base directory for all test files
2. An **attribute** in 8 test classes: `String testdir = homeDir + "/dedupendnote_files/..."` — sometimes the base dir, sometimes a subdirectory

Additionally, line 7 of `application-test.properties` was a comment missing its `#` prefix, causing it to be parsed as a broken property key. The `testdir` property itself was never injected (dead config).

## Changes made

### `application-test.properties`
- Removed broken comment line (no `#` prefix)
- Renamed `testdir` → `baseDir`

### `src/test/resources/application.properties` (new)
- Sets `spring.profiles.active = test` so all `@SpringBootTest` tests automatically load `application-test.properties` without needing `@ActiveProfiles` on every class

### `@SpringBootTest` test classes — inject `baseDir` via `@Value`, derive `testDir`

Replaced `String homeDir = System.getProperty("user.home")` + `String testdir = ...` with:
```java
@Value("${baseDir}")
String baseDir = "";    // initialized empty; Spring injects real value before @BeforeEach

String testDir = "";    // set in @BeforeEach from baseDir (± subdirectory suffix)
```

| File | `testDir` value |
|---|---|
| `DedupEndNoteApplicationTests.java` | `baseDir + "/experiments/"` |
| `TwoFilesTest.java` | `baseDir + "/experiments/"` |
| `MissedDuplicatesTests.java` | `baseDir` |
| `AuthorExperimentsTests.java` | `baseDir` |
| `ValidationTests.java` | `baseDir` |

`baseDir = ""` (not just `baseDir;`) is required because NullAway treats unannotated fields as `@NonNull` and flags uninitialized fields as compile errors. Spring overrides the empty-string default before tests run.

### Non-Spring test classes — converted to `@SpringBootTest`

| File | Before | After |
|---|---|---|
| `AuthorsBaseTest.java` | plain class | `@SpringBootTest` |
| `JournalsBaseTest.java` | plain class | `@SpringBootTest` |
| `JaroWinklerTitleTest.java` | `@TestConfiguration` | `@SpringBootTest` |

Subclasses of `AuthorsBaseTest` (`AuthorsComparisonThresholdTest`, `AuthorVariantsExperimentsTest`, `JaroWinklerAuthorsTest`) inherit the Spring context via `@SpringBootTest`'s `@Inherited` meta-annotation.

## Files modified

1. `src/main/resources/application-test.properties`
2. `src/test/resources/application.properties` *(new)*
3. `src/test/java/edu/dedupendnote/DedupEndNoteApplicationTests.java`
4. `src/test/java/edu/dedupendnote/TwoFilesTest.java`
5. `src/test/java/edu/dedupendnote/MissedDuplicatesTests.java`
6. `src/test/java/edu/dedupendnote/services/AuthorExperimentsTests.java`
7. `src/test/java/edu/dedupendnote/services/ValidationTests.java`
8. `src/test/java/edu/dedupendnote/services/AuthorsBaseTest.java`
9. `src/test/java/edu/dedupendnote/services/JournalsBaseTest.java`
10. `src/test/java/edu/dedupendnote/services/JaroWinklerTitleTest.java`

## Verification

```bash
./mvnw test
```

Key tests to watch: `ValidationTests` (heaviest file path usage) and `DedupEndNoteApplicationTests`.
