# Remove `static` from `BibliographicItemReader` helper methods

## Context

`BibliographicItemReader` is a Spring `@Service` (singleton bean) but exposed six
`public static` helper methods plus two `private static` helpers. These methods are
not side-effect free: each one mutates the `BibliographicItem` passed as an argument.
The `static` modifier sent a misleading signal — readers expect a static method to be
a pure utility, but these are core domain operations that build up a
`BibliographicItem`.

The only reason they were made static was pragmatic: tests call them directly as
fixtures without a Spring context. `BibliographicItemReader` has a no-arg constructor
and no injected dependencies, so a plain `new BibliographicItemReader()` works fine
in tests. Converting the methods to instance methods removes the misleading `static`
and aligns the class with its identity as a service, without changing any behaviour.

## Changes made

### Production

**`src/main/java/edu/dedupendnote/services/BibliographicItemReader.java`**

Removed `static` from these methods:

| Method | Visibility |
|---|---|
| `addNormalizedAuthor` | public |
| `addNormalizedJournal` | public |
| `addNormalizedPages` | public |
| `addNormalizedTitle` | public |
| `addReversedTitles` | public |
| `fillAllAuthors` | public |
| `detectReplyAndPhase` | private |
| `getCochranePagesFromDoi` | private |

No call-site changes inside the class — all internal calls were already unqualified
(e.g. `addNormalizedAuthor(fieldContent, …)`), which resolves through `this` for
instance methods exactly as it did for static ones.

The `public static final Pattern` constants (`REPLY_PATTERN`, `ERRATUM_PATTERN`,
`SOURCE_PATTERN`, `COMMENT_PATTERN`, `PHASE_PATTERN`, `RIS_LINE_PATTERN`), the
private `CONFERENCE_PATTERN`, and the package-private `skipNormalizationTitleFor` set
are genuine constants and remain `static`.

### Tests

All static call sites were in tests. Each affected test class received a
`private final BibliographicItemReader reader = new BibliographicItemReader()` field,
and calls were updated from `BibliographicItemReader.method(…)` to `reader.method(…)`.

`AuthorsBaseTest` hosts the `reader` field as `protected` so `AuthorVariantsExperimentsTest`
(which extends it) inherits it without a duplicate declaration.

Files updated:

- `src/test/java/edu/dedupendnote/unit/services/comparison/AuthorsBaseTest.java`
- `src/test/java/edu/dedupendnote/unit/services/comparison/AuthorVariantsExperimentsTest.java`
- `src/test/java/edu/dedupendnote/unit/services/comparison/DefaultTitleComparisonServiceTest.java`
- `src/test/java/edu/dedupendnote/unit/services/comparison/DefaultJournalComparisonServiceTest.java`
- `src/test/java/edu/dedupendnote/unit/services/comparison/JWSimilarityTitleTest.java`
- `src/test/java/edu/dedupendnote/unit/services/normalization/JournalsNormalizationServiceTest.java`
- `src/test/java/edu/dedupendnote/unit/services/normalization/TitlesNormalizationServiceTest.java`
- `src/test/java/edu/dedupendnote/unit/services/normalization/PagesNormalizationServiceTest.java`

### Documentation

**`docs/architecture.html`** line 123: changed "static helpers" to "instance helpers"
in the `BibliographicItemReader` service card.

No CLAUDE.md update required: no test reclassification, no service responsibility
change, no threshold or algorithm change.

## Verification

```
./mvnw clean test-compile   # BUILD SUCCESS
./mvnw test -Punit-tests    # 584 tests, 0 failures
./mvnw test -Pintegration-tests  # 23 tests, 0 failures
```
