# Bounded pattern cache

Status: ready-for-agent

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

- [ ] The three unbounded static maps are replaced by a bounded, thread-safe LRU cache
      with a small `get(key, mappingFunction)` interface.
- [ ] The cache never exceeds its configured maximum size under any input.
- [ ] When the cap is exceeded, the least-recently-used entry is evicted.
- [ ] Maximum size is externally configurable with a safe default.
- [ ] Journal-matching decisions for existing fixtures are unchanged (cache is
      behaviour-transparent).
- [ ] Isolated unit tests cover hit/miss behaviour, the size ceiling, and LRU eviction
      (plain JUnit, no Spring context).

## Blocked by

None - can start immediately.
