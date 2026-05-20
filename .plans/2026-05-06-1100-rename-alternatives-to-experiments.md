# Rename `validation/alternatives/` to `validation/experiments/`

**Status: executed**

## Context

Four terms were competing for the same concept: `alternatives`, `experiments`, `tests`, `performance`. Analysis in `.thinking/2026-05-06-step5-pluggable-services-and-naming.md` concluded that `experiments` is the canonical term:

- Describes the *activity* (controlled A/B variation), not the structure.
- Composes naturally: "threshold experiments", "normalization experiments".
- Already used in test-data names (`experiments/t1.txt`).
- Distinguishes from `validation/` (which validates the production engine).

This plan is a pure rename — no behaviour changes, no logic changes.

## Steps

### 1. Rename the directory

```bash
git mv src/test/java/edu/dedupendnote/validation/alternatives \
       src/test/java/edu/dedupendnote/validation/experiments
```

### 2. Update package declarations

In `src/test/java/edu/dedupendnote/validation/experiments/AuthorExperimentsTests.java`:
```java
package edu.dedupendnote.validation.experiments;
```

In `src/test/java/edu/dedupendnote/validation/experiments/ExperimentalAuthorsComparisonService.java`:
```java
package edu.dedupendnote.validation.experiments;
```

### 3. Check for cross-package imports

Search for any other file importing from `edu.dedupendnote.validation.alternatives`:
```bash
grep -r "validation.alternatives" src/ pom.xml CLAUDE.md
```
At the time of writing there are none, but verify before proceeding.

### 4. Verify Maven profile (no change needed)

The existing `validation-tests` profile filter is `**/validation/**/*Tests.java`, which already covers the new path. Verify by running the tests (see Verification).

### 5. Update CLAUDE.md

- Replace `validation/alternatives/` with `validation/experiments/` in the test class hierarchy section.
- Update the description to: "the `experiments` sub-package holds non-production `*ComparisonService` implementations used for controlled A/B experiments".

### 6. Add supersession note to old plan

At the top of `.plans/2026-05-06-1030-refactor-author-experiments-tests.md`, insert:

```
**Note:** The `validation/alternatives/` package referenced in this plan was renamed to `validation/experiments/` by `2026-05-06-1100-rename-alternatives-to-experiments.md`.
```

## Files modified

- `src/test/java/edu/dedupendnote/validation/experiments/AuthorExperimentsTests.java` (renamed + package updated)
- `src/test/java/edu/dedupendnote/validation/experiments/ExperimentalAuthorsComparisonService.java` (renamed + package updated)
- `CLAUDE.md`
- `.plans/2026-05-06-1030-refactor-author-experiments-tests.md` (one-line note added)

## Verification

1. `./mvnw clean test-compile` — no unresolved imports.
2. `./mvnw -Punit-tests test` — same pass/fail as before.
3. `./mvnw -Pintegration-tests test` — same pass/fail as before.
4. `./mvnw -Pvalidation-tests test` — `AuthorExperimentsTests` is still discovered and runs once; assertions hold.
5. `grep -r "validation.alternatives" src/ pom.xml CLAUDE.md` — no matches.
