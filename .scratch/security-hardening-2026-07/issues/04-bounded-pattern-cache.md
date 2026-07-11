# Bounded pattern cache

Status: implemented (tests green; pending commit & human review)

## Parent

`.scratch/security-hardening-2026-07/PRD.md`

## What to build

Replace the three process-global, never-evicted `ConcurrentHashMap<String, Pattern>`
caches used by journal matching with a single size-bounded, thread-safe LRU cache behind
a small interface (`get(key, mappingFunction)`). This removes the slow-burn
out-of-memory vector: today each distinct normalized journal name ever seen adds cache
entries that persist for the JVM lifetime, keyed on attacker-controllable strings.

Maximum size is configurable with a safe default (a few thousand entries — ample for the
legitimate hot set of journal names). Eviction is least-recently-used. Correctness of
matching is unchanged; only the storage behind it becomes bounded.

## Acceptance criteria

- [x] The three unbounded static maps are replaced by a bounded, thread-safe LRU cache
      with a small `get(key, mappingFunction)` interface.
- [x] The cache never exceeds its configured maximum size under any input.
- [x] When the cap is exceeded, the least-recently-used entry is evicted.
- [~] Maximum size is externally configurable with a safe default. **Deviation (Option B,
      maintainer's call):** it is a named constant `MAX_CACHE_SIZE = 5000`, NOT externally
      configurable. Rationale: the caches are `static` and the service is not Spring-managed,
      so a clean `@Value` is not available; and the cache is a pure memo, so the exact size
      never affects correctness — a smaller cap only recompiles more often.
- [x] Journal-matching decisions for existing fixtures are unchanged (cache is
      behaviour-transparent).
- [x] Isolated unit tests cover hit/miss behaviour, the size ceiling, and LRU eviction
      (plain JUnit, no Spring context).

## Blocked by

None - can start immediately.

## Comments

**Implemented.** New `BoundedPatternCache` (in `services.comparison`): LRU via a private
`LinkedHashMap(accessOrder=true)` subclass with `removeEldestEntry`, all access
`synchronized` so compute-if-absent + access-order update + eviction are atomic. Interface:
`get(key, mappingFunction)` (hot path), plus `get(key)` / `keySet()` / `size()`.

`DefaultJournalComparisonService`'s three caches stay `public static final` (so
`ValidationTests`' pattern export is untouched) but are retyped from unbounded
`ConcurrentHashMap` to `BoundedPatternCache(5000)`. Call sites: `computeIfAbsent`→`get`;
the starting-initialism site refactored to capture `final String firstWord` for the
`get(s1, k -> …)` lambda (removing the old get/null-check/put dance).

Verification:
- `BoundedPatternCacheTest` (3, no Spring): hit computes once + returns the same instance;
  size never exceeds cap (1000 inserts into cap-100 → size 100); LRU eviction (cap 2; A, B,
  touch A, insert C ⇒ B evicted).
- Parity: full unit suite 604, integration 25 (incl. `DeduplicationServiceTests` /
  `MissedDuplicatesTests` output guards) — unchanged. The test-compile phase also confirms
  `ValidationTests` still compiles against the new cache type.

Side effect (recorded): with a bounded cache the `ValidationTests` pattern export captures
only the resident set (≤ 5000) rather than every journal ever seen — acceptable for a
diagnostic export.
