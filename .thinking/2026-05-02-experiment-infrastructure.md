# Experiment Infrastructure — Discussion

_2026-05-02_

---

## Question

### Observation 1

The current `src/test/java/edu/dedupendnote/unit/services/AuthorVariantsExperimentsTest` test file tests the performance of several alternative normalizations of Author input fields.
Those alternative normalizations are functions in `src/test/java/edu/dedupendnote/domain/PublicationExperiment`.
In the recent past there have been some refactorings where the normalization is extracted to the static functions within the class `NormalizationService` (and `IOService::addNormalizedAuthor`).

The tests in `AuthorVariantsExperimentsTest` are different from the current unit and integration tests:
- they measure and compare the normal normalization with the performance (similarity) of the alternative normalizations, showing the cases where the normal normalization is (1) better than the alternative normalization, (2) worse, or (3) both the normal and alternative normalization are below the threshold, and showing a summary
- the assertion is a dummy assertion (proving as it were that this is not a real test)

These tests are not really unit tests, but more experiments and learning tests: they show the developers how alternative normalizations for the Author field perform compared with the normalization currently used.

They are not really tests of normalization because they compare the JWSimilarity to a threshold: this belongs more to the comparisonService than the normalizationService and its tests.

### Observation 2

The current `src/test/java/edu/dedupendnote/integration/services/AuthorExperimentsTest` test file is a minimal case for an alternative comparisonService and its functions.
This comparisonService for the Authors is up to now the only comparisonService which is extracted from ComparisonService.
The test file tests only if the number of duplicates found diminishes if the thresholds (`AUTHOR_SIMILARITY_NO_REPLY` etc) are higher. What we also want to know is how the performance is affected if the alternative normalizations of one or more fields (see Observation 1) are used.

The current test file deduplicates the same file twice, once with the normalizations and comparisons and thresholds from the production code, and once with the alternatives.
The numbers of duplicates of the first group are known from our test file `src/test/java/edu/dedupendnote/integration/services/ValidationTests` (in `Map<String, ValidationResult> validationResultsMap`). So the new integration tests should not deduplicate the test files twice.

### Observation 3

The `DeduplicationService` can handle alternative implementations for the `AuthorComparisonService` only. Should each of the compare functions of `ComparisonService` used in the `compareSet` function be extracted to a separate `Default...ComparisonService`.

Maybe some of these compare functions in `compareSet` could be merged. There are merged functions already (`compareStartPagesOrDois` which compares by 2 fields startingPage and dois).
The functions `compareSameDois`, `compareIssns` and `compareJournals` can be grouped into one function, possibly a new function which calls them with the same logic (`return a() || b() || c();`).
Would this make the experiments with alternative comparisons and normalizations easier?

### Observation 4

If it becomes easier to plugin alternative comparisonservices, normalizations, thresholds, then we might present some of these alternatives as a choice to the user of the web application.
E.g. a normal mode and a strict mode, where the strict mode would be less forgiving for false positives.

---

### Wishes / questions

- Be consistent with the division between normalizationServices and comparisonServices and their tests
- Alternative normalizations only exist for the Author field. Alternative normalizations for other fields might be worthwhile.
- How can we measure the performance of the deduplication of alternative normalization of 1 field?
- How can we measure the performance of the deduplication of alternative normalization of more than 1 field?
- The `ExperimentalAuthorsComparator` is an inner class of `AuthorExperimentsTests`. If we use more alternatives for the comparisonservice and normalizationservices, should these implementations be separate classes? The name `ExperimentalAuthorsComparator` should be changed to `ExperimentalAuthorsComparisonService`.
- It should also be possible to measure the performance of alternative thresholds.

Realizing the wishes may bring about a lot of changes. It is possible that the complexity might be too great (complexity as absolute value (cyclomatic complexity?) but also as a more vague measure for the developer).

What is your opinion about this? For the moment I don't want you to write a plan, but to get some idea of what this would entail, possibly with a global breakdown into steps.

---

## Answer

### The two levels of measurement

There is a structural tension running through all four observations. Experiments happen at two distinct levels:

- **Field level** (unit-ish): does an alternative normalization produce better raw JWS scores for known duplicate pairs? This is what `AuthorVariantsExperimentsTest` does.
- **Pipeline level** (integration): does an alternative normalization or threshold change TP/FP across full validated test files? This is what `AuthorExperimentsTests` *attempts* but does incompletely — it counts duplicates, not sensitivity/specificity.

The inconsistency is real: `AuthorVariantsExperimentsTest` applies a threshold inside a "normalization" test, which is a comparison concern. And `AuthorExperimentsTests` compares against known duplicate counts but doesn't use `ValidationTests`' TP/FN/Sensitivity infrastructure. Both experiments are therefore only half-informative.

---

### On the specific questions

**Consistency between normalization and comparison tests**

The cleanest split: normalization experiments at unit level report *raw score distributions* for known pairs, without applying any threshold. The threshold question belongs entirely at the integration level, where sensitivity/specificity can be measured. `AuthorVariantsExperimentsTest` currently conflates both, which blurs what you're actually measuring.

**Measuring 1-field normalization at pipeline level**

The hard part is that normalization happens inside `IOService.readPublications()`, tightly coupled to parsing. To inject an alternative normalization cleanly you need either:
- A strategy/function parameter flowing through `DeduplicationService` down to `IOService` (significant plumbing), or
- A subclass of `IOService` per experiment (simpler but sprawly if many variants).

For authors specifically, the current `PublicationExperiment` approach (subclass `Publication`, override `addAuthors*`) sidesteps this by bypassing `IOService` entirely — which is why it only works at the field level, not the pipeline level.

**Measuring multi-field normalizations**

This is where complexity can explode. With N binary choices (default vs alternative per field) you get 2^N combinations. Unless there are concrete alternatives for 3+ fields, the combinatorial infrastructure would cost more than it delivers. Sequential experiments (fix author normalization, then vary journal normalization) are more pragmatic.

**Extracting all compare functions to separate services**

Grouping `compareSameDois || compareIssns || compareJournals` into one method is a good cleanup regardless of the rest — it already mirrors the logic of `compareStartPagesOrDois` and makes the step 5 boundary explicit. But extracting full `ComparisonService` sub-interfaces for title, journal, pages follows the same pattern as `AuthorsComparisonService` and would enable threshold experiments per field. The cost: four new interfaces, four default implementations, injection plumbing in `DeduplicationService`. Not huge, but not trivial.

**Naming and structure of experimental implementations**

`ExperimentalAuthorsComparisonService` as a separate top-level class in a test `experiments/` package is clearly better than an inner class once you have more than one such experiment. Inner classes don't scale.

**Alternative thresholds**

This is the lowest-effort, highest-value change. Making thresholds constructor-injectable (rather than `static final` constants) in each `Default*ComparisonService` would allow threshold experiments without subclassing, and would directly support the "strict mode" product idea.

---

### Overall opinion

The full vision is coherent and the pieces fit together. But complexity is real: the full pluggable infrastructure (injectable normalizations + injectable comparison services + injectable thresholds, for all fields, with multi-field combination testing) is a significant investment.

Assessment in layers:

**Worth doing now, low effort:**
- Rename `ExperimentalAuthorsComparator` → `ExperimentalAuthorsComparisonService`, move to separate file
- Make `AuthorExperimentsTests` use `ValidationTests`' known TP/FP numbers instead of re-deduplicating
- Add sensitivity/specificity output to the integration experiment
- Group `compareSameDois || compareIssns || compareJournals` into one step-5 method

**Worth doing if you have concrete experiments to run:**
- Make thresholds constructor-injectable in `Default*ComparisonService`
- Extract `AuthorsComparisonService`-style interfaces for other fields

**Probably premature:**
- Full injectable normalization strategy through `IOService`
- Multi-field combination experiment infrastructure

The "strict mode for users" goal is worth keeping as a north star, but the product decision (which settings to expose) should drive the infrastructure — not the other way around. If you validate that higher author thresholds reduce false positives without hurting sensitivity on the BIG_SET, that's the evidence you need before exposing a knob to users.
