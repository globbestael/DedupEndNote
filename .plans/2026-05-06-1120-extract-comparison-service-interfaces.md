# Extract `TitleComparisonService`, `JournalComparisonService`, `PagesComparisonService`

**Status: pending**

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

Remove:
- The three static fields `ABBREVIATION_CACHE`, `INITIALISM_CACHE`, `STARTING_INITIALISM_CACHE` (moved to `DefaultJournalComparisonService`).
- Constants `JOURNAL_SIMILARITY_*`, `TITLE_SIMILARITY_*` (moved to threshold records).
- `public static boolean compareTitles(...)`, `compareJournals(...)`, `compareStartPagesOrDois(...)` (moved to default implementations).
- Private helpers `compareJournals_First*` (moved to `DefaultJournalComparisonService`).

Add:
- Three injected fields:
  ```java
  private final TitleComparisonService titleComparisonService;
  private final JournalComparisonService journalComparisonService;
  private final PagesComparisonService pagesComparisonService;
  ```
- No-arg constructor (used by Spring `@Service` and by `new ComparisonService()` in tests):
  ```java
  public ComparisonService() {
      this(new DefaultTitleComparisonService(),
           new DefaultJournalComparisonService(),
           new DefaultPagesComparisonService());
  }
  ```
- Three-arg constructor for experiments:
  ```java
  public ComparisonService(TitleComparisonService titleComparisonService,
                           JournalComparisonService journalComparisonService,
                           PagesComparisonService pagesComparisonService) {
      this.titleComparisonService = titleComparisonService;
      this.journalComparisonService = journalComparisonService;
      this.pagesComparisonService = pagesComparisonService;
  }
  ```
- Three thin instance-method delegates (so `DeduplicationService` call sites change minimally):
  ```java
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

### 10. Update `DeduplicationService` call sites

`DeduplicationService` (lines 133–137) currently calls:

```java
ComparisonService.compareStartPagesOrDois(p, pivot, map)
ComparisonService.compareTitles(p, pivot)
ComparisonService.compareJournals(p, pivot, map.get("isSameDois"))
```

After step 9, `compareStartPagesOrDois` etc. are instance methods, not static. Replace each call with:

```java
comparisonService.compareStartPagesOrDois(p, pivot, map)
comparisonService.compareTitles(p, pivot)
comparisonService.compareJournals(p, pivot, map.get("isSameDois"))
```

`compareIssns` and `compareSameDois` remain `ComparisonService.compareIssns(...)` / `ComparisonService.compareSameDois(...)`.

### 11. Update unit tests

Three test files call the static methods directly:
- `src/test/java/edu/dedupendnote/unit/services/ComparisonServiceTest.java`
- `src/test/java/edu/dedupendnote/unit/services/SimilarityJournalTest.java`
- `src/test/java/edu/dedupendnote/unit/services/JournalsBaseTest.java`

Approach per file:
- **`SimilarityJournalTest`** and **`JournalsBaseTest`** — field-specific tests; replace `ComparisonService.compareJournals(...)` with `new DefaultJournalComparisonService().compare(...)`.
- **`ComparisonServiceTest`** — exercises the integrated flow; replace `ComparisonService.compareTitles(...)` etc. with `new ComparisonService().compareTitles(...)` (the delegate). Alternatively instantiate `new DefaultTitleComparisonService()` etc. if the test is truly field-specific.

Tests referencing `JOURNAL_SIMILARITY_NO_REPLY`, `JOURNAL_SIMILARITY_REPLY`, `TITLE_SIMILARITY_*` constants must be updated to use `JournalThresholds.DEFAULT.noReply()`, `TitleThresholds.DEFAULT.sufficientStartPagesOrDois()`, etc.

### 12. Update CLAUDE.md

Update services table — add new rows and revise the `ComparisonService` row:

| Service | Responsibility |
|---|---|
| `ComparisonService` | Orchestrates the 5-step algorithm via three injected per-field comparison services; retains `compareIssns` and `compareSameDois` as static helpers |
| `DefaultTitleComparisonService` | JWS title matching; thresholds injectable via `TitleThresholds` |
| `DefaultJournalComparisonService` | Journal matching with abbreviation/initialism heuristics; thresholds injectable via `JournalThresholds` |
| `DefaultPagesComparisonService` | Exact-equality pages-or-DOI step (no thresholds) |

## Files modified

- `src/main/java/edu/dedupendnote/services/TitleThresholds.java` (new)
- `src/main/java/edu/dedupendnote/services/TitleComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/DefaultTitleComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/JournalThresholds.java` (new)
- `src/main/java/edu/dedupendnote/services/JournalComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/DefaultJournalComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/PagesComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/DefaultPagesComparisonService.java` (new)
- `src/main/java/edu/dedupendnote/services/ComparisonService.java` (heavily modified)
- `src/main/java/edu/dedupendnote/services/DeduplicationService.java` (static → instance calls)
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
