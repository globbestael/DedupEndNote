# Inject `AuthorThresholds` into `DefaultAuthorsComparisonService`

**Status: executed**

## Context

`DefaultAuthorsComparisonService` hard-codes three JWS threshold constants:

```java
public static final Double AUTHOR_SIMILARITY_NO_REPLY = 0.67;
public static final Double AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS = 0.75;
public static final Double AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS = 0.80;
```

The only way to vary them in experiments today is to subclass `AuthorsComparisonService` and hard-code alternative values — the current `ExperimentalAuthorsComparisonService` does exactly this. After this plan, threshold-only experiments become a one-liner:

```java
new DefaultAuthorsComparisonService(new AuthorThresholds(1.0, 1.0, 1.0))
```

`ExperimentalAuthorsComparisonService` then has no remaining purpose and is deleted.

For *algorithmic* experiments (different similarity metric, different loop structure), a separate `AuthorsComparisonService` implementation is still the right tool — but none exist yet and this plan does not affect that level.

This plan must be executed **after** `2026-05-06-1100-rename-alternatives-to-experiments.md` (the package is `validation/experiments/` by this point).

## Steps

### 1. Create `AuthorThresholds`

New file: `src/main/java/edu/dedupendnote/services/AuthorThresholds.java`

```java
package edu.dedupendnote.services;

public record AuthorThresholds(
        double noReply,
        double replySufficientStartPagesOrDois,
        double replyInsufficientStartPagesAndDois) {

    public AuthorThresholds {
        validate("noReply", noReply);
        validate("replySufficientStartPagesOrDois", replySufficientStartPagesOrDois);
        validate("replyInsufficientStartPagesAndDois", replyInsufficientStartPagesAndDois);
    }

    private static void validate(String name, double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0.0, 1.0] but was " + value);
        }
    }

    public static final AuthorThresholds DEFAULT = new AuthorThresholds(0.67, 0.75, 0.80);
}
```

### 2. Refactor `DefaultAuthorsComparisonService`

- Replace the three `public static final Double` constants with `private final AuthorThresholds thresholds;`.
- Add a no-arg constructor delegating to the one-arg constructor:
  ```java
  public DefaultAuthorsComparisonService() {
      this(AuthorThresholds.DEFAULT);
  }

  public DefaultAuthorsComparisonService(AuthorThresholds thresholds) {
      this.thresholds = thresholds;
  }
  ```
- In `compare()`, replace the three constant references:
  - `AUTHOR_SIMILARITY_NO_REPLY` → `thresholds.noReply()`
  - `AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS` → `thresholds.replySufficientStartPagesOrDois()`
  - `AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS` → `thresholds.replyInsufficientStartPagesAndDois()`

### 3. Update unit tests referencing the old constants

Run: `grep -rn "AUTHOR_SIMILARITY_" src/test/`

For each reference, replace `DefaultAuthorsComparisonService.AUTHOR_SIMILARITY_NO_REPLY` with `AuthorThresholds.DEFAULT.noReply()`, and similarly for the other two. Once no test references the constants, remove them from `DefaultAuthorsComparisonService`.

### 4. Update `DeduplicationService` — no change needed

`DeduplicationService` line 92: `this.authorsComparisonService = new DefaultAuthorsComparisonService();`. The no-arg constructor still exists and picks up `AuthorThresholds.DEFAULT` — no change required.

### 5. Update `AuthorExperimentsTests`

In `src/test/java/edu/dedupendnote/validation/experiments/AuthorExperimentsTests.java`, replace:

```java
expService.setAuthorsComparisonService(new ExperimentalAuthorsComparisonService());
```

with:

```java
// Threshold == 1.0 (the max JWS score) — similarity > 1.0 is never true, so no author
// match ever succeeds; sensitivity drops to 0%, specificity reaches 100%.
AuthorThresholds noMatchThresholds = new AuthorThresholds(1.0, 1.0, 1.0);
expService.setAuthorsComparisonService(new DefaultAuthorsComparisonService(noMatchThresholds));
```

Remove the import of `ExperimentalAuthorsComparisonService`.

### 6. Delete `ExperimentalAuthorsComparisonService`

```bash
git rm src/test/java/edu/dedupendnote/validation/experiments/ExperimentalAuthorsComparisonService.java
```

Verify no remaining callers: `grep -r "ExperimentalAuthorsComparisonService" src/` must return nothing.

### 7. Update CLAUDE.md

- In the services table, add a note to `DefaultAuthorsComparisonService`: "constructor-injectable thresholds via `AuthorThresholds` record".
- In the test class hierarchy, remove the `ExperimentalAuthorsComparisonService` entry.

## Files modified

- `src/main/java/edu/dedupendnote/services/AuthorThresholds.java` (new)
- `src/main/java/edu/dedupendnote/services/DefaultAuthorsComparisonService.java`
- `src/test/java/edu/dedupendnote/validation/experiments/AuthorExperimentsTests.java`
- `src/test/java/edu/dedupendnote/validation/experiments/ExperimentalAuthorsComparisonService.java` (deleted)
- Unit test files referencing `AUTHOR_SIMILARITY_*` constants (identified by grep)
- `CLAUDE.md`

## Verification

1. `./mvnw clean test-compile` — no unresolved references; NullAway/Error Prone pass.
2. `./mvnw -Punit-tests test` — all author-comparison tests pass with identical results.
3. `./mvnw -Pintegration-tests test` — production behaviour unchanged (no-arg constructor uses `AuthorThresholds.DEFAULT` = same numbers as before).
4. `./mvnw -Pvalidation-tests test` — `ValidationTests` baseline sensitivity/specificity unchanged; `AuthorExperimentsTests` assertions hold (experimental sensitivity < baseline; experimental specificity ≥ baseline).
5. `grep -r "ExperimentalAuthorsComparisonService" src/` — no matches.
6. `grep -r "AUTHOR_SIMILARITY_" src/` — no matches.
