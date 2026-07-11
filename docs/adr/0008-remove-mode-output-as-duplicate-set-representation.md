# ADR-0008 REMOVE mode output is a representation of each duplicate set

**Status:** Decided  
**Date:** 2026-06-29  
**Context:** Architecture review during enrichment-service placement refactor

## Question that prompted this decision

`EnrichmentService` has two public methods called from two different services:

- `enrich(List<BibliographicItem>)` — called from `DeduplicationService`
- `enrichMap(Map<String,String>, BibliographicItem)` — called from `BibliographicItemWriter`

The question was: do both calls belong in one service, and if so, which one?

## Framing: what does REMOVE mode output?

The pipeline has three phases:

1. **Read** — `BibliographicItemReader` parses RIS input into `List<BibliographicItem>`.
2. **Compare** — `compareSet` groups items into duplicate sets by assigning labels. Once
   labels are assigned the comparison work is done.
3. **Output** — produce output from the grouped structure.

The key insight: in REMOVE mode the output is not "the preferred `BibliographicItem`" — it
is **a representation of each duplicate set**. The program is not obliged to emit one of the
original items; it emits the best synthesis it can construct from the set's members. A
singleton set (unique item) is a degenerate case where the representation equals the item.

## Decision

All `EnrichmentService` calls belong in `BibliographicItemWriter`. The comparison phase
(label assignment) is complete when `searchYear*` returns; everything that follows is
output-phase work.

## Consequences

### `isKeptBibliographicItem` is output-phase bookkeeping

This flag was sometimes described as a "domain decision" (which item is canonical). Under
the new framing it is output-phase bookkeeping: it marks which item carries the synthesised
representation during the current write pass. It is set by `enrich()` inside the writer and
consumed by the writer loop immediately. It has no meaning outside a REMOVE-mode write pass.

The flag is still useful as a pre-computed marker within the write pass (avoiding repeated
grouping by label). But it is not a domain invariant maintained by the comparison phase.

### `DeduplicationService` becomes a pure orchestrator

Its responsibility is: read → compare → write. It no longer calls `EnrichmentService`
directly. `EnrichmentService` is injected only into `BibliographicItemWriter`.

### `enrich()` scope is extended slightly for two-file mode

In a two-file comparison, new-file items that are duplicates of old-file items have
`label < 0`. These are not processed by the main label-grouping loop (which correctly
handles only `label >= 0` sets). After that loop, `enrich()` makes one additional pass to
set `isKeptBibliographicItem = false` for these items, replacing the explicit
`filteredBibliographicItems` stream that previously lived in `DeduplicationService`.

### MARK mode is unaffected

`enrich()` and `enrichCochrane()` are guarded by `mode == REMOVE`. In MARK mode all items
keep `isKeptBibliographicItem = true` and the writer loop writes all records found in the
input file — unchanged behaviour.

### `enrichCochrane()` extracted

The Cochrane pages-uppercase logic was split across two sites in `enrich()` (one inside the
duplicate-set loop, one for singletons). It is extracted to a separate `enrichCochrane()`
method called after `enrich()`, using `isKeptBibliographicItem` as its filter, which
unifies both cases.

## Relationship to ADR-0001

ADR-0001 listed `isKeptBibliographicItem` and `isPresentInOldFile` as "dedup state" fields
on `BibliographicItem`. This ADR clarifies that:

- `isPresentInOldFile` was redundant with `id < 0` and was removed (2026-06-29; see ADR-0001
  amendment note).
- `isKeptBibliographicItem` is output-phase bookkeeping, not comparison-phase state. The
  comparison services never read or write it (the correctness condition noted in ADR-0001
  remains satisfied).
