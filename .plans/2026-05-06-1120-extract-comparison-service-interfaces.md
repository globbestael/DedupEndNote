# Extract `TitleComparisonService`, `JournalComparisonService`, `PagesComparisonService`

**Status: executed**

## Context

`ComparisonService` (~337 lines) holds field-level comparison logic as `public static` methods:

- `compareTitles(r1, r2)` — JWS with three threshold cases (phase, sufficient pages/DOIs, insufficient)
- `compareJournals(r1, r2, isSameDois)` — exact match + JWS + abbreviation/initialism heuristics
- `compareStartPagesOrDois(r1, r2, map)` — exact-equality pages and DOIs (Cochrane + severalPages special cases)
- `compareIssns(r1, r2, isSameDois)` — exact set intersection
- `compareSameDois(r1, r2, isSameDois)` — reports the pre-computed DOI comparison result

This makes per-field algorithm experiments impossible without forking the entire file. The established pattern (`AuthorsComparisonService` / `DefaultAuthorsComparisonService` / `AuthorThresholds`) is extended to cover the three remaining JWS-comparable fields. `compareIssns` and `compareSameDois` have no thresholds and no current experiment hypothesis — they stay as static helpers on `ComparisonService`.

`DeduplicationService` (line 133–137) calls these static methods directly; after extraction those calls route through `ComparisonService` instance methods that delegate to the injected services.

This plan must be executed **after** `2026-05-06-1110-inject-author-thresholds.md`.

## Steps

### 1. Create `TitleThresholds`

`src/main/java/edu/dedupendnote/services/TitleThresholds.java`:

```java
package edu.dedupendnote.services;

public record TitleThresholds(
        double sufficientStartPagesOrDois,
        double insufficientStartPagesAndDois,
        double phase) {

    public TitleThresholds {
        validate("sufficientStartPagesOrDois", sufficientStartPagesOrDois);
        validate("insufficientStartPagesAndDois", insufficientStartPagesAndDois);
        validate("phase", phase);
    }

    private static void validate(String name, double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0.0, 1.0] but was " + value);
        }
    }

    public static final TitleThresholds DEFAULT = new TitleThresholds(0.89, 0.94, 0.96);
}
```

### 2. Create `TitleComparisonService` (interface)

`src/main/java/edu/dedupendnote/services/TitleComparisonService.java`:

```java
package edu.dedupendnote.services;

import edu.dedupendnote.domain.Publication;

public interface TitleComparisonService {
    boolean compare(Publication r1, Publication r2);
}
```

### 3. Create `DefaultTitleComparisonService`

`src/main/java/edu/dedupendnote/services/DefaultTitleComparisonService.java`:

- Lift the body of `ComparisonService.compareTitles` (lines 264–345) as an instance method.
- Replace the three threshold constants (`TITLE_SIMILARITY_*`) with reads from a `private final TitleThresholds thresholds` field.
- Constructors: no-arg delegating to `DefaultTitleComparisonService(TitleThresholds.DEFAULT)`; one-arg accepting custom thresholds.

### 4. Create `JournalThresholds`

`src/main/java/edu/dedupendnote/services/JournalThresholds.java`:

```java
package edu.dedupendnote.services;

public record JournalThresholds(double noReply, double reply) {

    public JournalThresholds {
        validate("noReply", noReply);
        validate("reply", reply);
    }

    private static void validate(String name, double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0.0, 1.0] but was " + value);
        }
    }

    public static final JournalThresholds DEFAULT = new JournalThresholds(0.90, 0.93);
}
```

### 5. Create `JournalComparisonService` (interface)

`src/main/java/edu/dedupendnote/services/JournalComparisonService.java`:

```java
package edu.dedupendnote.services;

import org.jspecify.annotations.Nullable;
import edu.dedupendnote.domain.Publication;

public interface JournalComparisonService {
    boolean compare(Publication r1, Publication r2, @Nullable Boolean isSameDois);
}
```

### 6. Create `DefaultJournalComparisonService`

`src/main/java/edu/dedupendnote/services/DefaultJournalComparisonService.java`:

- Lift the body of `ComparisonService.compareJournals` (lines 59–131) as an instance method.
- Move the three private helper methods (`compareJournals_FirstAsAbbreviation`, `_FirstAsInitialism`, `_FirstWithStartingInitialism`) and the three static `ConcurrentHashMap` caches (`ABBREVIATION_CACHE`, `INITIALISM_CACHE`, `STARTING_INITIALISM_CACHE`) into this class as private members. Keep the caches `static` to preserve exact production behaviour (one shared cache across all instances).
- Replace `JOURNAL_SIMILARITY_*` references with `thresholds.noReply()` / `thresholds.reply()`.
- Constructors: no-arg → `DefaultJournalComparisonService(JournalThresholds.DEFAULT)`; one-arg.

### 7. Create `PagesComparisonService` (interface)

`src/main/java/edu/dedupendnote/services/PagesComparisonService.java`:

```java
package edu.dedupendnote.services;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import edu.dedupendnote.domain.Publication;

public interface PagesComparisonService {
    boolean compare(Publication r1, Publication r2, Map<String, @Nullable Boolean> map);
}
```

Note: `compareStartPagesOrDois` mutates `map` (writes `isSameDois`) as a side-effect — the interface signature preserves this.

### 8. Create `DefaultPagesComparisonService`

`src/main/java/edu/dedupendnote/services/DefaultPagesComparisonService.java`:

- Lift the body of `ComparisonService.compareStartPagesOrDois` (lines 195–262) as an instance method.
- No thresholds record (the comparison is exact-equality based, not JWS).
- Single no-arg constructor (nothing to inject).

### 9. Refactor `ComparisonService`

This step also absorbs `authorsComparisonService` from `DeduplicationService` — all four field-comparison services live on `ComparisonService` as `private final` fields, constructor-injected. This unifies the wiring pattern and removes the setter/getter from `DeduplicationService`.

Remove:
- The three static fields `ABBREVIATION_CACHE`, `INITIALISM_CACHE`, `STARTING_INITIALISM_CACHE` (moved to `DefaultJournalComparisonService`).
- Constants `JOURNAL_SIMILARITY_*`, `TITLE_SIMILARITY_*` (moved to threshold records).
- `public static boolean compareTitles(...)`, `compareJournals(...)`, `compareStartPagesOrDois(...)` (moved to default implementations).
- Private helpers `compareJournals_First*` (moved to `DefaultJournalComparisonService`).

Add:
- Four injected fields:
  ```java
  private final AuthorsComparisonService authorsComparisonService;
  private final TitleComparisonService titleComparisonService;
  private final JournalComparisonService journalComparisonService;
  private final PagesComparisonService pagesComparisonService;
  ```
- No-arg constructor (used by Spring `@Service` and by `new ComparisonService()` in tests):
  ```java
  public ComparisonService() {
      this(new DefaultAuthorsComparisonService(),
           new DefaultTitleComparisonService(),
           new DefaultJournalComparisonService(),
           new DefaultPagesComparisonService());
  }
  ```
- Four-arg constructor for experiments:
  ```java
  public ComparisonService(AuthorsComparisonService authorsComparisonService,
                           TitleComparisonService titleComparisonService,
                           JournalComparisonService journalComparisonService,
                           PagesComparisonService pagesComparisonService) {
      this.authorsComparisonService = authorsComparisonService;
      this.titleComparisonService = titleComparisonService;
      this.journalComparisonService = journalComparisonService;
      this.pagesComparisonService = pagesComparisonService;
  }
  ```
- Four thin instance-method delegates (so `DeduplicationService` call sites change minimally):
  ```java
  public boolean compareAuthors(Publication r1, Publication r2) {
      return authorsComparisonService.compare(r1, r2);
  }
  public boolean compareTitles(Publication r1, Publication r2) {
      return titleComparisonService.compare(r1, r2);
  }
  public boolean compareJournals(Publication r1, Publication r2, @Nullable Boolean isSameDois) {
      return journalComparisonService.compare(r1, r2, isSameDois);
  }
  public boolean compareStartPagesOrDois(Publication r1, Publication r2, Map<String, @Nullable Boolean> map) {
      return pagesComparisonService.compare(r1, r2, map);
  }
  ```

Keep `compareIssns` and `compareSameDois` as `public static` methods — no change.

### 10. Update `DeduplicationService`

Remove the `authorsComparisonService` field, its initialization in the constructor, its getter, and its setter:
- Line 31: delete `private AuthorsComparisonService authorsComparisonService;`
- Line 92: delete `this.authorsComparisonService = new DefaultAuthorsComparisonService();`
- Lines 457–459: delete `getAuthorsComparisonService()` method
- Lines 536–538: delete `setAuthorsComparisonService()` method

Update the comparison call (lines 133–137):

```java
// Before
ComparisonService.compareStartPagesOrDois(p, pivot, map)
    && authorsComparisonService.compare(p, pivot)
    && ComparisonService.compareTitles(p, pivot)
    && (ComparisonService.compareSameDois(...)
        || ComparisonService.compareIssns(...)
        || ComparisonService.compareJournals(...))

// After
comparisonService.compareStartPagesOrDois(p, pivot, map)
    && comparisonService.compareAuthors(p, pivot)
    && comparisonService.compareTitles(p, pivot)
    && (ComparisonService.compareSameDois(...)
        || ComparisonService.compareIssns(...)
        || comparisonService.compareJournals(...))
```

`compareIssns` and `compareSameDois` remain as `ComparisonService.compareIssns(...)` / `ComparisonService.compareSameDois(...)` (still static).

### 11. Update `AuthorExperimentsTests`

The test currently creates a `DeduplicationService` and then calls `setAuthorsComparisonService()` on it. Replace the setter-based wiring with a 4-arg `ComparisonService` constructor:

```java
// Before
DeduplicationService expService = new DeduplicationService(new ComparisonService());
AuthorThresholds noMatchThresholds = new AuthorThresholds(1.0, 1.0, 1.0);
expService.setAuthorsComparisonService(new DefaultAuthorsComparisonService(noMatchThresholds));

// After
AuthorThresholds noMatchThresholds = new AuthorThresholds(1.0, 1.0, 1.0);
ComparisonService cs = new ComparisonService(
        new DefaultAuthorsComparisonService(noMatchThresholds),
        new DefaultTitleComparisonService(),
        new DefaultJournalComparisonService(),
        new DefaultPagesComparisonService());
DeduplicationService expService = new DeduplicationService(cs);
```

The comment, assertions, dataset, and surrounding test logic are unchanged.

### 12. Update unit tests

Three test files call the static methods directly:
- `src/test/java/edu/dedupendnote/unit/services/ComparisonServiceTest.java`
- `src/test/java/edu/dedupendnote/unit/services/SimilarityJournalTest.java`
- `src/test/java/edu/dedupendnote/unit/services/JournalsBaseTest.java`

Approach per file:
- **`SimilarityJournalTest`** and **`JournalsBaseTest`** — field-specific tests; replace `ComparisonService.compareJournals(...)` with `new DefaultJournalComparisonService().compare(...)`.
- **`ComparisonServiceTest`** — exercises the integrated flow; replace `ComparisonService.compareTitles(...)` etc. with `new ComparisonService().compareTitles(...)` (the delegate). Alternatively instantiate `new DefaultTitleComparisonService()` etc. if the test is truly field-specific.

Tests referencing `JOURNAL_SIMILARITY_NO_REPLY`, `JOURNAL_SIMILARITY_REPLY`, `TITLE_SIMILARITY_*` constants must be updated to use `JournalThresholds.DEFAULT.noReply()`, `TitleThresholds.DEFAULT.sufficientStartPagesOrDois()`, etc.

### 13. Update CLAUDE.md

Update services table — add new rows and revise the `ComparisonService` and `DeduplicationService` rows:

| Service | Responsibility |
|---|---|
| `ComparisonService` | Orchestrates the 5-step algorithm via four injected per-field comparison services (`AuthorsComparisonService`, `TitleComparisonService`, `JournalComparisonService`, `PagesComparisonService`); retains `compareIssns` and `compareSameDois` as static helpers |
| `DefaultAuthorsComparisonService` | Jaro-Winkler author matching; thresholds injectable via `AuthorThresholds` |
| `DefaultTitleComparisonService` | JWS title matching; thresholds injectable via `TitleThresholds` |
| `DefaultJournalComparisonService` | Journal matching with abbreviation/initialism heuristics; thresholds injectable via `JournalThresholds` |
| `DefaultPagesComparisonService` | Exact-equality pages-or-DOI step (no thresholds) |

Remove `DefaultAuthorsComparisonService` from the standalone row (it is now described in the table above). Remove `setAuthorsComparisonService` / `getAuthorsComparisonService` from the `DeduplicationService` description if mentioned.

## Files modified

- `src/main/java/edu/dedupendnote/services/TitleThresholds.java` (new)
- `src/main/java/edu/dedupendnote/services/TitleComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/DefaultTitleComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/JournalThresholds.java` (new)
- `src/main/java/edu/dedupendnote/services/JournalComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/DefaultJournalComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/PagesComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/DefaultPagesComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/ComparisonService.java` (heavily modified; gains `authorsComparisonService`)
- `src/main/java/edu/dedupendnote/services/DeduplicationService.java` (loses `authorsComparisonService` field/getter/setter; static → instance calls)
- `src/test/java/edu/dedupendnote/validation/experiments/AuthorExperimentsTests.java` (setter → constructor wiring)
- `src/test/java/edu/dedupendnote/unit/services/ComparisonServiceTest.java`
- `src/test/java/edu/dedupendnote/unit/services/SimilarityJournalTest.java`
- `src/test/java/edu/dedupendnote/unit/services/JournalsBaseTest.java`
- Any other test referencing `JOURNAL_SIMILARITY_*` or `TITLE_SIMILARITY_*` constants (verify with grep)
- `CLAUDE.md`

## Verification

1. `./mvnw clean test-compile` — no unresolved references; NullAway/Error Prone pass.
2. `./mvnw -Punit-tests test` — all comparison tests pass with identical pass/fail counts.
3. `./mvnw -Pintegration-tests test` — production behaviour unchanged (default thresholds = same numbers as before).
4. `./mvnw -Pvalidation-tests test` — `ValidationTests` TP/FP/FN/TN identical across all 14 datasets.
5. Spot-check: deduplicate one real dataset before and after the change and `diff` the output — byte-identical.
6. `grep -r "JOURNAL_SIMILARITY_\|TITLE_SIMILARITY_" src/` — no matches.
7. `grep -r "ComparisonService\.compareTitles\|ComparisonService\.compareJournals\|ComparisonService\.compareStartPagesOrDois" src/` — no matches (all calls now go through instance methods).
8. `grep -r "setAuthorsComparisonService\|getAuthorsComparisonService" src/` — no matches.
