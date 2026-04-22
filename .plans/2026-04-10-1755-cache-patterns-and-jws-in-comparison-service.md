# Plan: Cache compiled patterns and JaroWinklerSimilarity in ComparisonService

**Status: executed — compile passes, same pre-existing test failures as before**

## Context

Profiling the deduplication pipeline identified two avoidable per-call allocations in `ComparisonService`:

1. Three helper methods (`compareJournals_FirstAsAbbreviation`, `compareJournals_FirstAsInitialism`, `compareJournals_FirstWithStartingInitialism`) called `Pattern.compile()` on every invocation. The pattern depends only on the input journal string, which is already normalized — the same pattern for the same journal string was being recompiled on every O(N²) comparison.

2. `JaroWinklerSimilarity` was instantiated per call in both `compareJournals` and `compareTitles`. The class is stateless and a single instance suffices.

## Changes made

**File:** `src/main/java/edu/dedupendnote/services/ComparisonService.java`

### Static `JaroWinklerSimilarity` field ✅

Replaced two `new JaroWinklerSimilarity()` local variables with a single static field:
```java
private static final JaroWinklerSimilarity JWS = new JaroWinklerSimilarity();
```

### Three static pattern caches ✅

```java
private static final Map<String, Pattern> ABBREVIATION_CACHE = new ConcurrentHashMap<>();
private static final Map<String, Pattern> INITIALISM_CACHE = new ConcurrentHashMap<>();
private static final Map<String, Pattern> STARTING_INITIALISM_CACHE = new ConcurrentHashMap<>();
```

Each helper method now uses `computeIfAbsent` so each unique journal string compiles its pattern only once.

## Files modified

1. `src/main/java/edu/dedupendnote/services/ComparisonService.java`

## Verification

```bash
./mvnw clean compile test-compile    # BUILD SUCCESS
./mvnw test -Punit-tests             # 456 tests, 4 pre-existing failures unchanged
```
