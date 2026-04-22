# Reorganize per-field tests by category and Spring-context requirement

## Status: COMPLETED (2026-04-19)

All implementation steps finished and verified:
- Unit tests: 500 run, 5 failures (all pre-existing, same count as before refactor)
- Integration tests: `SimilarityAuthorTest` 44/44 green; other integration failures pre-existing
- `./mvnw clean package` passes (NullAway / Error Prone clean)
- All old class names confirmed absent from source tree

## Context

Today, per-field tests in `src/test/java/edu/dedupendnote/services/` mix three different concerns inside the same files:

1. **Normalization** — input → `NormalizationService.normalize…` → check output with equality / containsAll.
2. **Boolean (or equality) similarity** — two normalized values are compared by a production method that returns a boolean / string-equality verdict (e.g. `ComparisonService.compareJournals`, `AuthorsComparisonService.compare`).
3. **Jaro-Winkler similarity** — two normalized values are compared by `JaroWinklerSimilarity.apply`; the score is asserted against a threshold. The production caller is the one that decides accept/reject; the test checks only the score.

These are different beasts: they exercise different layers and fail for different reasons. Today they are entangled — `JaroWinklerJournalTest` contains a FIXME (issue #50) calling for the split; `JaroWinklerTitleTest` mixes JWS assertions with `publication.getTitles()` shape assertions and with `IOService.SOURCE_PATTERN` / `IOService.COMMENT_PATTERN` regex experiments; `JaroWinklerAuthorsTest` mixes Spring-required tests (`authorsComparisonService.compare`) with raw `jws.apply` tests in one `@SpringBootTest` class. `NormalizationServiceTest` itself groups authors, journal, pages and title into one file, while a sibling `NormalizationServiceTitleTest` already shows the preferred per-field pattern.

Goal: separate files by **category** (Normalization / Similarity / JWSimilarity) **and** by **Spring-context requirement**, one per field, so that a red test points immediately to the responsible layer.

## Arguments

### For the split

- **Each file has one job.** A failing test in `NormalizationServiceJournalTest` means normalization regressed; a failing test in `SimilarityJournalTest` means `ComparisonService.compareJournals` regressed; a failing test in `JWSimilarityJournalTest` means the raw Jaro-Winkler score on the normalized pair changed. No more ambiguity about which layer moved.
- **Precedent already exists.** `NormalizationServiceTitleTest` demonstrates the focused-per-field pattern.
- **The FIXME in `JaroWinklerJournalTest` (issue #50)** explicitly asks for the normalization portion to be pulled out.
- **Spring-context split speeds up unit runs.** Author tests currently all carry `@SpringBootTest` + `@Tag("integration")` overhead even when they only need `jws.apply`. Extracting the non-Spring ones moves them into the `unit-tests` Maven profile.
- **Grep locality.** Someone touching `NormalizationService.normalizeJournal` opens `NormalizationServiceJournalTest` and sees every case. Today they must also think to open `JaroWinklerJournalTest`.

### Against the split

- **Churn in stable files.** Long-lived files are renamed / deleted; git archaeology gets noisier (`git log --follow` still works per file).
- **More files to navigate.** Authors, journal, title each gain one or two sibling files instead of one combined one.
- **The category boundary is fuzzy in a few places.**
  - `testTitleSplitter` asserts on `publication.getTitles()` after `IOService.addNormalizedTitle` — strictly normalization-output, but goes through the IOService entry point rather than `NormalizationService.normalizeInputTitles` directly.
  - `testErrataFromFile`, `testPositiveCommentsFromFile`, `testNegativeCommentsFromFile`, `testPositiveCommentsAndRepliesFromFile` (in `JaroWinklerTitleTest`) verify `IOService.SOURCE_PATTERN` / `IOService.COMMENT_PATTERN` regex behaviour — they are neither normalization nor similarity tests. Flagged as out of scope below.
- **Refactor cost.** ~10 files touched, ~3 new files created, ~3 deleted. No behaviour change.

**Recommendation:** proceed. The clarity gain dominates for a codebase where the three concerns already drift into each other.

## Target structure

Per-field, per-category files. Each file holds only one category of test. Where the existing file mixes Spring-required and Spring-free tests, it is split.

```
src/test/java/edu/dedupendnote/services/

# Normalization tests (input -> normalized output; no similarity / no Spring)
  NormalizationServiceAuthorTest.java       (renamed from NormalizationServiceTest; author tests only)
  NormalizationServiceJournalTest.java      (NEW; receives normalization-shaped tests from old NormalizationServiceTest + from JaroWinklerJournalTest)
  NormalizationServiceTitleTest.java        (existing; receives normalizeTitleTest + testTitleSplitter)
  NormalizationServicePagesTest.java        (NEW; folds PagesTest + normalizePagesTest_new)
  NormalizationServiceDoiTest.java          (NEW; folds DoiTest)
  NormalizationServiceTextTest.java         (NEW; folds TextNormalizerTest — normalizeToBasicLatin + UNUSUAL_WHITESPACE_PATTERN)

# Boolean/equality similarity tests (production compare* methods, boolean return)
  SimilarityJournalTest.java                (NEW; from JaroWinklerJournalTest — fullPositiveTest + fullNegativeTest)
  SimilarityAuthorTest.java                 (NEW; from JaroWinklerAuthorsTest — the two methods using authorsComparisonService.compare; keeps @SpringBootTest)

# Jaro-Winkler similarity tests (jws.apply on two normalized values vs threshold)
  JWSimilarityJournalTest.java              (renamed from JaroWinklerJournalTest; keeps jwPositiveTest + jwNegativeTest)
  JWSimilarityTitleTest.java                (renamed from JaroWinklerTitleTest; keeps jwFullPositiveTest + jwFullNegativeTest)
  JWSimilarityAuthorTest.java               (NEW; from JaroWinklerAuthorsTest — the two methods using jws.apply only; NO Spring)
  JWSimilarityAbstractTest.java             (renamed from AbstracttextTest; promotes JwsAbstracttextTest's parametrized tests to top-level; keeps the @Disabled RatcliffObershelpAbstracttextTest nested class if it stays, or deletes it as dead code)

# Unchanged / out of scope
  JournalsBaseTest.java                     (unchanged)
  AuthorsBaseTest.java                      (unchanged — helper base class)
  ComparisonServiceTest.java                (unchanged)
  AuthorPermutationsExperimentsTest.java, AuthorExperimentsTests.java, AuthorVariantsExperimentsTest.java, AuthorsComparisonThresholdTest.java, ValidationTests.java, RecordDBService.java — unchanged

# Deleted (folded into Normalization* files above)
  NormalizationServiceTest.java             DELETED (split out as NormalizationServiceAuthorTest)
  PagesTest.java                            DELETED
  DoiTest.java                              DELETED
  TextNormalizerTest.java                   DELETED
```

All `NormalizationService*Test` files are plain JUnit 5 with no Spring context (`NormalizationService` is a static helper). The `@TestConfiguration` annotations currently present on some files are removed — they are not doing anything.

The `Similarity*Test` / `JWSimilarity*Test` files get `@TestConfiguration` only if they need no Spring context, or `@SpringBootTest @ActiveProfiles("test") @Tag("integration")` when they depend on an autowired service (today this is only `SimilarityAuthorTest`).

## Spring-context split — where it applies

- `JaroWinklerAuthorsTest` currently mixes both categories under `@SpringBootTest`:
  - Needs Spring (uses `authorsComparisonService.compare`): `jwFullPositiveTest_lowest_accepted_similarity`, `jwFullNegativeTest` → move to `SimilarityAuthorTest` (`@SpringBootTest @ActiveProfiles("test") @Tag("integration")`).
  - Does NOT need Spring (uses only `jws.apply`): `jwFullPositiveTest_highest_similarity`, `jwFullNegativeTest_defective` → move to `JWSimilarityAuthorTest` (plain JUnit 5; can still extend `AuthorsBaseTest` for `fillPublication` / providers / `jws`).
  - Both new files share `positiveAuthorsProvider` / `negativeAuthorsProvider`. Either duplicate the providers (simpler, independent files) or promote them to `AuthorsBaseTest` (DRY but couples the two files). **Recommend: duplicate** — each test file stays self-contained, and any future edits to provider rows will be a small, explicit sync.
- No other current file mixes Spring-required and Spring-free tests.

## Category mapping (what moves where)

### Authors

| Source | Kind | Destination |
|---|---|---|
| `NormalizationServiceTest.normalizeAuthorTest` + `authorArgumentProvider` | Normalization | `NormalizationServiceAuthorTest` (renamed file — stays) |
| `JaroWinklerAuthorsTest.jwFullPositiveTest_lowest_accepted_similarity` | Similarity (Spring) | `SimilarityAuthorTest` |
| `JaroWinklerAuthorsTest.jwFullNegativeTest` | Similarity (Spring) | `SimilarityAuthorTest` |
| `JaroWinklerAuthorsTest.jwFullPositiveTest_highest_similarity` | JWSimilarity (no Spring) | `JWSimilarityAuthorTest` |
| `JaroWinklerAuthorsTest.jwFullNegativeTest_defective` | JWSimilarity (no Spring) | `JWSimilarityAuthorTest` |
| `JaroWinklerAuthorsTest.java` file itself | — | DELETED (after split) |

### Journal

| Source | Kind | Destination |
|---|---|---|
| `NormalizationServiceTest.normalizeJournalTest` + `journalArgumentProvider` | Normalization | `NormalizationServiceJournalTest` |
| `JaroWinklerJournalTest.slashTest` + `slashArgumentProvider` | Normalization | `NormalizationServiceJournalTest` |
| `JaroWinklerJournalTest.journalWithSquareBracketsAtEnd` | Normalization | `NormalizationServiceJournalTest` |
| `JaroWinklerJournalTest.journalWithSquareBracketsAtStart` | Normalization | `NormalizationServiceJournalTest` |
| `JaroWinklerJournalTest.fullPositiveTest` + `fullPositiveArgumentProvider` | Similarity (no Spring) | `SimilarityJournalTest` |
| `JaroWinklerJournalTest.fullNegativeTest` + `fullNegativeArgumentProvider` | Similarity (no Spring) | `SimilarityJournalTest` |
| `JaroWinklerJournalTest.jwPositiveTest` + `positiveArgumentProvider` | JWSimilarity | `JWSimilarityJournalTest` (renamed from `JaroWinklerJournalTest`) |
| `JaroWinklerJournalTest.jwNegativeTest` + `negativeArgumentProvider` | JWSimilarity | `JWSimilarityJournalTest` |
| FIXME comment referring to issue #50 | — | removed once the split is complete |

Rows in `positiveArgumentProvider` stay in the JWSimilarity file unchanged — even when `normalize(a).equals(normalize(b))` the row is still meaningful as a score-vs-threshold sanity check (the production caller reads the score, not the normalized forms). Normalization-specific assertions belong in `NormalizationServiceJournalTest` where they are expressed as equality / containsAll on the normalized output, not as JWS thresholds.

### Title

| Source | Kind | Destination |
|---|---|---|
| `NormalizationServiceTest.normalizeTitleTest` + `titleArgumentProvider` | Normalization | `NormalizationServiceTitleTest` |
| `NormalizationServiceTitleTest.*` (existing methods) | Normalization | `NormalizationServiceTitleTest` (stays) |
| `JaroWinklerTitleTest.testTitleSplitter` | Normalization (through `IOService.addNormalizedTitle`) | `NormalizationServiceTitleTest` |
| `JaroWinklerTitleTest.jwFullPositiveTest` + `positiveArgumentProvider` | JWSimilarity | `JWSimilarityTitleTest` (renamed from `JaroWinklerTitleTest`) |
| `JaroWinklerTitleTest.jwFullNegativeTest` + `negativeArgumentProvider` | JWSimilarity | `JWSimilarityTitleTest` |
| `JaroWinklerTitleTest.testErrata`, `testErrataFromFile`, `testPositiveCommentsFromFile`, `testNegativeCommentsFromFile`, `testPositiveCommentsAndRepliesFromFile` | Pattern / file-based regression | Stay in `JWSimilarityTitleTest` for now — flagged below as out of scope (belongs in a future `IOServicePatternsTest`) |

### Pages, DOI, Text, Abstract

| Source | Kind | Destination |
|---|---|---|
| `NormalizationServiceTest.normalizePagesTest_new` + `pagesArgumentProvider` | Normalization (via `IOService.addNormalizedPages`) | `NormalizationServicePagesTest` |
| `PagesTest.parsePagesTest` + `argumentProvider` | Normalization (`normalizeInputPages`) | `NormalizationServicePagesTest` |
| `DoiTest.addDois` + `argumentProvider` | Normalization (`normalizeInputDois`) | `NormalizationServiceDoiTest` |
| `TextNormalizerTest.textNormalizerTest` + `argumentProvider` + `whiteSpaceReplacement` + `escapeUnicode` helper | Normalization (`normalizeToBasicLatin`, `UNUSUAL_WHITESPACE_PATTERN`) | `NormalizationServiceTextTest` |
| `AbstracttextTest.JwsAbstracttextTest.jwPositiveTest` / `jwNegativeTest` | JWSimilarity (on abstracts) | `JWSimilarityAbstractTest` |
| `AbstracttextTest.RatcliffObershelpAbstracttextTest` (`@Disabled`) | Experimental | Either carry over as a `@Disabled` nested class in `JWSimilarityAbstractTest` or delete (ask during implementation). Default: carry over, to preserve the experimental record. |
| `AbstracttextTest.cleanAbstracttext` helper (calls `normalizeToBasicLatin`) | Helper | Carry over into `JWSimilarityAbstractTest` (it is abstract-text-specific preprocessing, not a reusable utility). |

## Commented-out argument rows

Some argument providers contain commented-out `arguments(...)` lines — confirmed today in at least `JaroWinklerTitleTest` (`positiveArgumentProvider`, `negativeArgumentProvider`) and `DoiTest` (`argumentProvider`). Rule for the move:

- Move commented-out rows **together with** the live rows they belong to — preserve their relative ordering within the provider.
- If an edit is applied to a live row during the move (e.g. switching from a JWS threshold assertion to an equality assertion, renaming a parameter, adjusting the expected normalized form), apply the same edit mechanically to the commented-out rows in the same provider. The point is that a developer un-commenting a row later should not have to re-figure-out the edit history.
- Do not silently delete commented-out rows. If a row is intentionally retired, leave a one-line comment explaining why; otherwise carry it over as-is.

## Reused functions / utilities (no new helpers)

- `NormalizationService.normalizeInputAuthors` / `normalizeInputJournals` / `normalizeInputPages` / `normalizeInputDois` / `normalizeInputTitles` / `normalizeJournal` / `normalizeTitle` / `normalizeToBasicLatin` / `normalizeHyphensAndWhitespace` — `src/main/java/edu/dedupendnote/services/NormalizationService.java`.
- `IOService.addNormalizedJournal` / `addNormalizedTitle` / `addNormalizedPages` / `addNormalizedAuthor` / `fillAllAuthors` / `SOURCE_PATTERN` / `COMMENT_PATTERN` — `src/main/java/edu/dedupendnote/services/IOService.java`.
- `ComparisonService.compareJournals` / `JOURNAL_SIMILARITY_NO_REPLY` / `TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS` / `AUTHOR_SIMILARITY_NO_REPLY` — `src/main/java/edu/dedupendnote/services/ComparisonService.java`.
- `DefaultAuthorsComparisonService` (autowired via `DeduplicationService`) — used only in `SimilarityAuthorTest`.
- `AuthorsBaseTest.fillPublication` / `Triple` — reused by `JWSimilarityAuthorTest` (plain JUnit) and `SimilarityAuthorTest` (`@SpringBootTest`); both keep `extends AuthorsBaseTest`.
- `BaseTest` (`jws`, `testDir`, `baseDir`, `getHighestSimilarityForAuthors`) — `JWSimilarityTitleTest` continues to extend it (for the file-based tests that remain).

## Implementation order (once approved)

1. ✅ Create the four new `NormalizationService*Test` files (`JournalTest`, `PagesTest`, `DoiTest`, `TextTest`) — pure additions, no deletions yet.
2. ✅ Move the normalization methods + providers out of `NormalizationServiceTest`, `JaroWinklerJournalTest`, `JaroWinklerTitleTest`, `PagesTest`, `DoiTest`, `TextNormalizerTest` into the new files; run `./mvnw -Punit-tests test`.
3. ✅ Rename `NormalizationServiceTest` → `NormalizationServiceAuthorTest` (`git mv` + update class name).
4. ✅ Create `SimilarityJournalTest` and move `fullPositiveTest` / `fullNegativeTest` + providers out of `JaroWinklerJournalTest`.
5. ✅ Rename `JaroWinklerJournalTest` → `JWSimilarityJournalTest` (`git mv` + update class name + drop `@TestConfiguration` + drop issue-#50 FIXME).
6. ✅ Rename `JaroWinklerTitleTest` → `JWSimilarityTitleTest` (`git mv` + update class name).
7. ✅ Split `JaroWinklerAuthorsTest` into `SimilarityAuthorTest` (`@SpringBootTest`) + `JWSimilarityAuthorTest` (plain JUnit, extends `AuthorsBaseTest`); duplicate `positiveAuthorsProvider` / `negativeAuthorsProvider` into both; delete `JaroWinklerAuthorsTest`.
8. ✅ Rename `AbstracttextTest` → `JWSimilarityAbstractTest`; promote `JwsAbstracttextTest` nested class's tests to top-level; keep `RatcliffObershelpAbstracttextTest` as a `@Disabled` nested class.
9. ✅ Delete `PagesTest`, `DoiTest`, `TextNormalizerTest`.
10. ✅ Final pass: `./mvnw clean package` (exercises NullAway + Error Prone), `./mvnw -Punit-tests test`, `./mvnw -Pintegration-tests test`.

## Verification

1. `./mvnw -Punit-tests test` — all non-integration tests green; the number of executed tests should not decrease (moved rows still run).
2. `./mvnw -Pintegration-tests test` — `SimilarityAuthorTest` (new `@Tag("integration")`) runs in this profile; `JaroWinklerAuthorsTest` is gone.
3. Per-file targeted runs:
   - `./mvnw -Dtest=NormalizationServiceAuthorTest test`
   - `./mvnw -Dtest=NormalizationServiceJournalTest test`
   - `./mvnw -Dtest=NormalizationServicePagesTest test`
   - `./mvnw -Dtest=NormalizationServiceDoiTest test`
   - `./mvnw -Dtest=NormalizationServiceTitleTest test`
   - `./mvnw -Dtest=NormalizationServiceTextTest test`
   - `./mvnw -Dtest=SimilarityJournalTest test`
   - `./mvnw -Dtest=SimilarityAuthorTest test`
   - `./mvnw -Dtest=JWSimilarityJournalTest test`
   - `./mvnw -Dtest=JWSimilarityTitleTest test`
   - `./mvnw -Dtest=JWSimilarityAuthorTest test`
   - `./mvnw -Dtest=JWSimilarityAbstractTest test`
4. `./mvnw clean package` — NullAway / Error Prone produce no new warnings.
5. `git grep -n "class PagesTest\b\|class DoiTest\b\|class TextNormalizerTest\b\|class NormalizationServiceTest\b\|class JaroWinklerJournalTest\b\|class JaroWinklerTitleTest\b\|class JaroWinklerAuthorsTest\b\|class AbstracttextTest\b"` — must return nothing.
6. Sanity-diff: for each moved argument provider, compare total `arguments(...)` lines (live + commented-out) pre/post refactor.

## Out of scope (separate follow-ups)

- `JaroWinklerTitleTest`'s regex/pattern tests (`testErrata`, `testErrataFromFile`, `testPositiveCommentsFromFile`, `testNegativeCommentsFromFile`, `testPositiveCommentsAndRepliesFromFile`) — these test `IOService.SOURCE_PATTERN` / `IOService.COMMENT_PATTERN`. They are not similarity tests and not normalization tests; belong in a future `IOServicePatternsTest`. Left in `JWSimilarityTitleTest` for now to avoid scope creep.
- File-based integration tests (the four `*FromFile` methods above) should eventually be converted to `@SpringBootTest @ActiveProfiles("test") @Tag("integration")` subclasses of `AbstractIntegrationTest`, as the existing FIXME on `JaroWinklerTitleTest` notes. Out of scope for this refactor.
- No changes to production code in `NormalizationService`, `IOService`, or `ComparisonService`.
- No changes to `JournalsBaseTest`, `AuthorsBaseTest`, `ComparisonServiceTest`, or the `AuthorPermutations*` / `AuthorExperiments*` / `AuthorVariants*` / `AuthorsComparisonThresholdTest` experiments.

## Open items to confirm during implementation

- ✅ `@Disabled RatcliffObershelpAbstracttextTest` nested class kept in `JWSimilarityAbstractTest` as a record of the experimental work.
- ✅ `JWSimilarityAuthorTest` duplicates `positiveAuthorsProvider` / `negativeAuthorsProvider` (self-contained approach chosen).
