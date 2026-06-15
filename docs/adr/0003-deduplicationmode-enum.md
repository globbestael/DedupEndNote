# ADR-0003 Replace boolean markMode flag with DeduplicationMode enum

**Status:** Decided — implemented in commit 937f7aa  
**Date:** 2026-06-04 (commit date)  
**Context:** The deduplication pipeline used a `boolean markMode` parameter threaded through `DeduplicationService`, `BibliographicItemWriter`, and callers; the parameter name was ambiguous about which mode was "true".

## Decision

Replace the `boolean markMode` parameter end-to-end with a `DeduplicationMode` enum having two values: `REMOVE` (default) and `MARK`.

- `DeduplicationMode` lives in `edu.dedupendnote.domain`.
- All call sites that previously passed `true`/`false` now pass `DeduplicationMode.MARK` / `DeduplicationMode.REMOVE`.
- `DeduplicationMode.filenameSuffix()` returns `"_mark"` or `"_deduplicated"` — the suffix used by `UtilitiesService.createPath()` when naming output files.
- HTTP layer: the mode is submitted as a form field value (`"MARK"` / `"REMOVE"`) and deserialized via `DeduplicationMode.valueOf(...)`.
- Tests: `@SpringBootTest` integration tests pass the enum directly; validation tests use `DeduplicationMode.MARK` for mark-mode runs.

## Alternatives considered

### 1. Keep the boolean, document the convention

Lowest change. The convention "`true` = mark mode" would be documented in Javadoc.

**Rejected** because: the boolean gives no compile-time hint about which direction is which. Every call site requires a mental lookup. The enum makes the intent self-documenting at every call site and eliminates the possibility of accidentally inverting the flag.

### 2. Two separate code paths (separate methods for each mode)

`deduplicateAndRemove(...)` and `deduplicateAndMark(...)` as separate entry points.

**Rejected** because: the two modes share the comparison and normalization phases completely; only the post-processing (enrichment, writer call) differs. Separate entry points would duplicate the shared phases or require an internal private method, recreating the boolean split at a different level.

## What to watch for (conditions that would reopen this)

- A third operating mode is needed (e.g. "review mode" returning structured diff). If a third value makes the enum's `filenameSuffix()` / `valueOf()` pattern awkward, revisit the abstraction.
