# Test taxonomy and ValidationOfProduction — Discussion

_2026-05-04_

---

## Question

I think that the distinction unit tests versus integration tests is incomplete:
- there certainly are unit tests
- there certainly are integration tests. E.g. in the folder `src/test/java/edu/dedupendnote` tests like `DedupEndNoteApplicationTests` (functions `deduplicateSmallFiles`,
  `deduplicate_withDuplicateIDs`, `deduplicate_OK`) and `MissedDuplicatesTests` (function `deduplicateMissedDuplicates`). These tests test the string returned by the
  `deduplicationService`, and in a sense the number of input records and the number of output records.

The current test `checkAllTruthFiles` in `src/test/java/edu/dedupendnote/integration/services/ValidationTests` does ***not*** test the string returned by the normal flow
in the `deduplicationService`, and in this sense is ***not*** an integration test.
What this test does is:
- (1) mimics the first part of the normal flow in the `deduplicationService`
- (2) compares a gold set of truth records with these deduplicated records, and stores the result of the comparisons (true positive, true negative, false positive, false negative) as
  attributes of these deduplicated records
- (3) has its own routine to write the deduplicated records (including these extra fields TP etc) in a tab delimited format
- (4) writes a summary of TP etc and scores (accuracy, precision, sensitivity etc, see `ValidationResult`) and compares them with stored scores etc
- (5) optionally writes the False Positive and False Negative results

The previous talk about checking the performance of alternative normalizations and comparisonservices is related with what is happening in `ValidationTests`.
Let's call `checkAllTruthFiles` in `ValidationTests` "ValidationOfProduction", and checking the performance of alternative normalizations and comparisonservices "ValidationOfAlternatives".

The main difference is that ValidationOfProduction tests the performance of the "alternatives" which ***are*** chosen for the production version,
while the ValidationOfAlternatives (and the alternatives in `ExperimentalAuthorsComparator` and `AuthorVariantsExperimentsTest`) test or should test the performance
of the alternatives ***not*** chosen.
Another difference is that the ValidationOfProduction should be run regularly to see if code changes change the scores positively or negatively, whereas the
ValidationOfAlternatives are not tests which should be run regularly, but maybe a command which should be called in the web interface in admin mode?
If the ValidationOfAlternatives are really production code (part of `src/main/java`), then maybe most of the code for ValidationOfProduction should also be part
of the production code and the test itself (in `src/test/java`) would call the code in `src/main/java`. Let's call this test "ValidationOfProductionTests".

The code for ValidationOfProduction and ValidationOfAlternatives does not have to be available on the production server. It would be sufficient if it can be run
on the development server/computer. The files needed for ValidationOfProduction and ValidationOfAlternatives should ***not*** be part of github data, but be reachable
by test code or production code for the developers.

Does this proposed division between (1) unit tests, (2) integration tests, and (3) ValidationOfProductionTests make sense?

It will probably be necessary to refactor the current ValidationOfProduction so that the first parts of the flow are more in line with the normal flow in production code.
Suppose the first steps would call the normal deduplication of 1 file (should this be in Mark mode?), then pick up the file with deduplicated (or marked?) records, transform them in
the tab delimited format, .... In that case there would/could be no divergence between the real and the "test" deduplication.

Once this refactoring is done, then the ValidationOfAlternatives probably would be quite different from the current form and from the proposal you made in the previous .thinking file.
Maybe they would differ from the ValidationOfProduction in the first part (inject alternative normalizationService, ComparisonService), but would the second part (postprocessing
of the output file, comparison with truth files, reporting the scores) be the same.

What is your opinion about this?
For the moment I don't want you to write a plan, but to get some idea of what this would entail, possibly with a global breakdown into steps.

---

## Answer

### The three-way taxonomy is correct

The current two-way split (unit / integration) is genuinely misleading. `ValidationTests.checkAllTruthFiles` is not an integration test in the conventional sense — it does not assert on the externally observable behaviour of `deduplicateOneFile` (the result string, the output file). It is a performance measurement tool. Giving it its own category clarifies this and stops it from being confused with regression tests.

Proposed taxonomy:

| Category | What it asserts | Files | Run frequency |
|---|---|---|---|
| Unit | Correctness of individual methods; no Spring context | `unit/` | Every commit |
| Integration | Exact output of `deduplicateOneFile` on small known inputs | `integration/` (excluding `ValidationTests`) | Every commit |
| ValidationOfProduction | Sensitivity/specificity on large validated datasets | `integration/services/ValidationTests` → move to `validation/` | On demand (before release, after structural changes) |

The distinction between integration and ValidationOfProduction is purpose: integration tests are regression guards (they fail if anything changes), validation tests are performance monitors (they report whether changes are improvements or regressions).

---

### On the divergence in the current ValidationTests.deduplicate()

The private `deduplicate()` helper in `ValidationTests` calls:

```
ioService.readPublications(inputFileName, ...)
deduplicationService.doSanityChecks(publications, inputFileName)
deduplicationService.searchYearOneFile(publications, ...)
```

The production path through `deduplicateOneFile` calls those same steps plus two more: **enrichment** (filling missing fields from duplicates) and **writeOutput**. So the current validation bypasses enrichment. Since enrichment does not change which records are marked as duplicates (it only improves kept records), this does not affect TP/FP counts. But it is still a divergence — if enrichment were ever to have a side effect on comparison, the validation test would not catch it.

The user's proposal — run `deduplicateOneFile` in mark mode, then pick up the output file — would close this gap completely, because the validation would exercise exactly the production code path. The tradeoff: mark-mode output is a RIS file, not a `List<Publication>` in memory. The current `checkResults` logic needs a `List<Publication>` with `dedupid` set. So you would need either:
- (a) A new `DeduplicationService` method that performs mark-mode dedup and returns `List<Publication>` rather than writing to a file (essentially the current `searchYearOneFile` promoted to a public API), or
- (b) Call `deduplicateOneFile` in mark mode, write the output, then re-read that output file and extract dedupids.

Option (b) is the purest (no special API needed) but requires that the mark-mode RIS output format is parseable back into a form where dedupid can be extracted. That should be feasible since `ioService.readPublications` already parses RIS files and mark mode just adds a label to a field. However this adds an extra I/O round-trip per dataset, which matters for the 14-dataset run.

Option (a) is more pragmatic: add a `deduplicateAndReturn(String inputFileName, Consumer<String> progressReporter)` method to `DeduplicationService` that performs sanityChecks + readPublications + searchYearOneFile + enrichment and returns `List<Publication>`. The validation test calls this instead of the private helper. This closes the enrichment gap without the round-trip cost and without exposing internal steps.

The minimal approach — add only the enrichment call to the existing `ValidationTests.deduplicate()` — is also valid if the goal is simply to align the code paths rather than to test `deduplicateOneFile` itself.

---

### On extracting shared postprocessing code

The strongest argument for the refactoring is this: ValidationOfProduction and ValidationOfAlternatives differ only in **what deduplication engine is used**. The postprocessing (compare output against truth file, compute TP/FP/FN/TN, write tab-delimited output, report scores) is identical. That shared postprocessing code should be extracted once and reused by both.

Currently all of it lives in `ValidationTests.checkResults()`. Extracting it to a `ValidationService` class (which could live in `src/main/java` under a `dev` or `validation` profile) would enable:
- `ValidationOfProductionTests` to call `validationService.checkResults(inputFileName, truthFileName, deduplicationService, ...)`
- `ValidationOfAlternativesTests` to call `validationService.checkResults(inputFileName, truthFileName, experimentalService, ...)`

The key parameter is what deduplication engine is used — a `DeduplicationService` configured with different comparison services, thresholds, or normalization strategies. This directly connects to the pluggable-services discussion from the previous `.thinking` file.

---

### On moving validation code to src/main

The argument for `src/main`: if you want a web admin endpoint to trigger ValidationOfAlternatives without rebuilding, the code must be in `src/main`. The counter-argument: the truth files live on the development computer and are not in git, so this admin endpoint only works in a local development context anyway. A Spring profile (`@Profile("validation")`) could make it available locally without polluting production deployments — but it still adds complexity to the production code base for what is essentially a developer convenience.

An alternative: keep everything in `src/test` but make the `ValidationService` (postprocessing code) a separate class in `src/test`. The validation tests call it; the experimental tests call it too. No production code changes. The loss: you can't trigger validation through the web interface, only from the IDE or Maven. Given that the truth files are only on developer machines anyway, this seems acceptable.

If the web admin interface becomes a genuine requirement (e.g. running on a shared team development server), the move to `src/main` with a `validation` profile makes sense at that point.

---

### Global breakdown of steps

**Step 1 — Taxonomy clarification (low effort, immediate value)**
- Move `ValidationTests` to a new `validation/` package (or rename the folder `src/test/java/edu/dedupendnote/validation/`)
- Update `pom.xml` profile filters if needed so validation tests are excluded from the regular `-Pintegration-tests` run (they are slow and require external files)
- Update CLAUDE.md with the three-way taxonomy

**Step 2 — Align ValidationOfProduction with production flow (medium effort)**
- Add enrichment call to `ValidationTests.deduplicate()`, or
- Extract a `deduplicateAndReturn()` method from `DeduplicationService` and use it
- The choice depends on whether you want `deduplicateOneFile` fully tested by the validation path

**Step 3 — Extract shared postprocessing (medium effort, high value)**
- Extract `checkResults()` logic from `ValidationTests` into a reusable `ValidationService` class
- The signature should accept a `DeduplicationService` (or an interface) as a parameter
- Keep this in `src/test` unless the web-interface requirement materialises

**Step 4 — ValidationOfAlternatives using shared infrastructure (medium effort)**
- Refactor `AuthorExperimentsTests` to use `ValidationService.checkResults()` with an injected experimental service
- Remove the double-deduplication; use known scores from `validationResultsMap` as baseline
- Report sensitivity/specificity rather than just duplicate counts

**Step 5 — Pluggable services and thresholds (higher effort, enables systematic experiments)**
- Make thresholds constructor-injectable in `DefaultAuthorsComparisonService` (and others if extracted)
- This enables threshold experiments without subclassing
- Connect to the layer-2 items from the previous `.thinking` file

**Step 6 — Optional web admin interface**
- Only if there is a concrete team use case for triggering validation via browser
- Move `ValidationService` to `src/main` under `@Profile("validation")`
- Add a protected admin endpoint

---

### Overall opinion

The taxonomy is right and worth formalising. Steps 1 and 3 are the most impactful relative to effort: naming clarity and shared postprocessing code. Step 2 is worth doing but the minimal variant (adding enrichment to the existing private helper) is probably sufficient. Steps 4 and 5 depend on whether there are concrete experiments to run — the infrastructure investment is only worthwhile if experiments are actively being done. Step 6 should wait until there is a real team use case.

The risk of doing all six steps at once is that the codebase is in flux across many files simultaneously. Sequential execution (1 → 3 → 2 → 4 → 5) keeps each step independently reviewable and the tests green throughout.
