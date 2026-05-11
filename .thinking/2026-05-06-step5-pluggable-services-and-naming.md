# Step 5 — Pluggable services, thresholds, and naming — Discussion

_2026-05-06_

---

## Question

Step 5 from the previous thinking session was: "Make thresholds constructor-injectable in `DefaultAuthorsComparisonService` (and others if extracted)."

Several follow-up questions:

**(1)** Other thresholds of `DefaultAuthorsComparisonService` should be pluggable. This enables threshold experiments without subclassing. Maybe thresholds need a class of their own.

**(2)** Thresholds in other `Default*ComparisonServices` must also be pluggable.

**Would this mean that the current `AuthorExperimentsTests` does not need an `ExperimentalAuthorsComparator` because it uses `DefaultAuthorsComparisonService` with an alternative threshold?**

**Would this mean that for experiments with other comparison functions, we need separate comparison services for each field / combination of fields, each with their own compare function? Would the constructor of a comparison service need a parameter reference to a `defaultCompare` or an `alternative*Compare` function?**

**Would this mean that for experiments with alternative normalizations, we should refactor `IOService` and `NormalizationService` so that alternative normalizations are pluggable? E.g. the `IOService` has a constructor or a `setNormalizationServices` where alternative normalizations can be specified (a hash of lambda functions or function references) and be used for the construction of a `NormalizationService`.**

**Choose the right name: in the previous prompts but also in the (test) code we use names like "alternatives", "experiments", "tests" and "performance". This concerns also the package name, file names and functions. Can we choose one name — which one?**

---

## Answer

### On injectable thresholds — and the fate of ExperimentalAuthorsComparisonService

Currently `DefaultAuthorsComparisonService` has three constants:

```java
AUTHOR_SIMILARITY_NO_REPLY = 0.67
AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS = 0.75
AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS = 0.80
```

These could become constructor parameters. The cleanest representation is a dedicated value object (Java record):

```java
public record AuthorThresholds(
    double noReply,
    double replySufficientStartPagesOrDois,
    double replyInsufficientStartPagesAndDois
) {
    public static final AuthorThresholds DEFAULT =
        new AuthorThresholds(0.67, 0.75, 0.80);
}
```

`DefaultAuthorsComparisonService` would then become:

```java
public class DefaultAuthorsComparisonService implements AuthorsComparisonService {
    private final AuthorThresholds thresholds;

    public DefaultAuthorsComparisonService() {
        this(AuthorThresholds.DEFAULT);
    }

    public DefaultAuthorsComparisonService(AuthorThresholds thresholds) {
        this.thresholds = thresholds;
    }
    // ...
}
```

**Effect on `ExperimentalAuthorsComparisonService`:**

Yes — the current `ExperimentalAuthorsComparisonService` would become unnecessary for threshold-only experiments. Instead of subclassing, the test would just write:

```java
AuthorThresholds experimental = new AuthorThresholds(1.17, 0.95, 1.0);
expService.setAuthorsComparisonService(new DefaultAuthorsComparisonService(experimental));
```

`ExperimentalAuthorsComparisonService` can be deleted once this is in place.

However: injectable thresholds only cover one kind of experiment — varying how *strict* the match is, using the *same* similarity metric (JaroWinkler) and the *same* logic structure (loop over all-author pairs, check threshold). If you want to experiment with a different similarity metric (Levenshtein, Ratcliff-Obershelp) or a different comparison structure (best-match instead of any-match), subclassing the interface is still the right tool. So the hierarchy would be:

- Threshold experiment → inject `AuthorThresholds` into `DefaultAuthorsComparisonService`
- Algorithm experiment → implement a new `AuthorsComparisonService`

This is a clean two-level design. `ExperimentalAuthorsComparisonService` was doing a threshold experiment (adding 0.5 to each threshold) using the subclassing mechanism — which was the wrong level. With injectable thresholds, it belongs at the first level and disappears as a class.

---

### On pluggable comparison functions within a service

The user asks whether experiments with "other comparison functions" would require separate services for each field, with a `Function` reference in the constructor.

This is a real design question. `ComparisonService` currently implements the full 5-step algorithm in one class, with all field comparisons (title, journal, pages, authors) inlined. If you want to experiment with, say, a different title comparison function, you have two options:

**Option A — Interface extraction (current direction)**

Extract interfaces for each comparison dimension:

```java
public interface TitleComparisonService {
    boolean compare(Publication r1, Publication r2);
}
public interface JournalComparisonService { ... }
public interface PagesComparisonService { ... }
```

`ComparisonService` would accept these via its constructor (alongside `AuthorsComparisonService`, which is already injectable). Experiments implement one interface and leave the others as default. This is the natural extension of the existing `AuthorsComparisonService` pattern.

**Option B — Function references in the constructor**

```java
public ComparisonService(
    BiFunction<Publication, Publication, Boolean> titleCompare,
    BiFunction<Publication, Publication, Boolean> journalCompare,
    // ...
) { ... }
```

This avoids creating new interface types but loses the ability to carry state (thresholds, similarity scores, debug output) in the comparison object. `AuthorsComparisonService.getSimilarity()` already shows that state matters — a bare `BiFunction` cannot return a similarity score alongside the boolean result.

**Recommendation:** Option A. The extracted interfaces follow the same pattern as `AuthorsComparisonService` (which is already established), can carry state, and are easier to name and document. Function references are attractive but they sacrifice structure that turns out to be useful.

For `ComparisonService` concretely, the extracted interfaces and their corresponding default implementations would be:

- `AuthorsComparisonService` / `DefaultAuthorsComparisonService` — already done
- `TitleComparisonService` / `DefaultTitleComparisonService`
- `JournalComparisonService` / `DefaultJournalComparisonService`
- `PagesComparisonService` / `DefaultPagesComparisonService`

Each default implementation would have a corresponding `*Thresholds` record. Each could be injected into `ComparisonService` via its constructor, with sensible defaults for the no-arg constructor that production uses.

This is a significant refactoring of `ComparisonService` (~337 lines), but it is purely mechanical extraction — no behaviour changes — and it would make the 5-step algorithm far easier to experiment with.

---

### On pluggable normalizations

This is the most invasive change and deserves separate treatment.

Currently all normalization in `IOService.readPublications()` calls static methods on `NormalizationService`:

```java
NormalizationService.normalizeInputAuthors(fieldContent)
NormalizationService.normalizeInputJournals(fieldContent, fieldName)
// etc.
```

Static methods are not injectable. To make normalizations pluggable, `NormalizationService` would need to become an instance class with injectable behaviour, and `IOService` would need to accept a `NormalizationService` instance (or an equivalent).

The user's proposal — a map of field-name → function in `IOService`, used to construct a `NormalizationService` — is one valid shape. Another is:

```java
public class NormalizationService {
    private final Function<String, AuthorRecord> authorNormalizer;
    private final BiFunction<String, String, List<String>> journalNormalizer;
    // ...

    public static final NormalizationService DEFAULT = new NormalizationService(
        NormalizationService::normalizeInputAuthors,
        NormalizationService::normalizeInputJournals,
        // ...
    );
}
```

`IOService` would then hold a `NormalizationService` field (injected or constructed with the default), and the static calls become instance method calls.

**The real question is: what normalization experiment is planned?**

- If the experiment is "what happens if we normalize author names differently" → you only need to inject an alternative `authorNormalizer` function
- If the experiment is "what happens if we use a different journal-title matching strategy" → you need an alternative `journalNormalizer`
- If the experiment is "what happens if we keep raw field content without normalization" → you need a pass-through normalizer for that field

Without a specific hypothesis, the refactoring is speculative infrastructure. `NormalizationService` is large (~991 lines) and its methods are called throughout the codebase, not just in `IOService`. Making all of them injectable would be a large, risky change with unclear payoff.

**Recommendation:** Defer normalization pluggability until there is a concrete experiment that requires it. At that point, extract only the specific normalization that the experiment targets (e.g. author normalization), make that one injectable, and leave the rest as static. The parallel with comparison services is that `AuthorsComparisonService` was extracted from `ComparisonService` as the first injected field because there was a concrete hypothesis to test (what if we raise author-similarity thresholds?). The same criterion should apply to normalizations.

---

### On naming: "alternatives", "experiments", "tests", "performance"

The four names in use:

| Name | Where used | Problem |
|---|---|---|
| `tests` | Test files, Maven profiles | Overloaded — everything is a test |
| `performance` | Informal references | Ambiguous — performance could mean speed, not sensitivity/specificity |
| `alternatives` | Package `validation/alternatives/`, plan files | Structural (describes what's there: alternative implementations) not functional |
| `experiments` | `experiments/t1.txt`, class prefixes like `Experimental*` | Functional (describes the activity: running controlled experiments) |

The strongest case is for **`experiments`** as the canonical term:

1. It describes the *activity* rather than the *structure*. We are running A/B experiments with controlled variation of one component.
2. It is already used in the test data directory (`experiments/t1.txt`) — the convention is established.
3. It maps cleanly to domain language: sensitivity/specificity experiments, threshold experiments, normalization experiments. The word "alternatives" doesn't compose as naturally ("author alternatives"? "threshold alternatives"?).
4. It distinguishes from `validation/` (which validates the production engine) and `integration/` (which guards against regressions). The three categories become: unit, integration, validation, experiments.

**Concrete renaming implications:**

- Package: rename `validation/alternatives/` → `validation/experiments/`
- Classes: `ExperimentalAuthorsComparisonService` stays; `AuthorExperimentsTests` stays (already uses the right word)
- Concept: "ValidationOfAlternatives" → "ValidationExperiments" or just "experiments"
- Maven profile: could add `-Pexperiment-tests` alongside `-Pvalidation-tests` if experiments need their own profile
- CLAUDE.md: use "experiments" consistently in the Testing section

The one risk: "experiments" can sound like unfinished work ("oh it's just an experiment, not real"). In a scientific context it is precise and respectable; in a software project it might read as provisional. "Benchmarks" is another option — it conveys systematic measurement — but it has a speed connotation. "Experiments" is still the best fit.

**Recommendation: adopt `experiments` as the canonical name and rename the package from `validation/alternatives/` to `validation/experiments/`.**

---

### Overall view on Step 5

The three sub-problems (injectable thresholds, pluggable comparison functions, pluggable normalizations) have very different cost/benefit ratios:

| Sub-problem | Effort | Prerequisite | Payoff |
|---|---|---|---|
| Injectable thresholds | Low — a record type + a constructor parameter per service | None | Removes the need for `ExperimentalAuthorsComparisonService`; enables systematic threshold sweeps |
| Pluggable comparison functions | Medium — extract interfaces for Title/Journal/Pages, inject into `ComparisonService` | Injectable thresholds done first | Enables algorithm experiments for each comparison dimension |
| Pluggable normalizations | High — de-static-ify `NormalizationService`, thread it through `IOService` | Specific experiment hypothesis | Only worthwhile when there is a concrete normalization experiment planned |

The recommended order: threshold injection first (low effort, immediately useful), comparison-function extraction second (enables the experiments that are most likely to yield improvements), normalization last (only if there is a specific hypothesis).

The naming cleanup (package rename + consistent use of "experiments") is cheap and should accompany whatever step is done next.
