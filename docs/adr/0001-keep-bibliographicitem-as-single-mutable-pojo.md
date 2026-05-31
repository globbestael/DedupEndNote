# ADR-0001 Keep BibliographicItem as a single mutable POJO

**Status:** Decided — do not split  
**Date:** 2026-05-31  
**Context:** Architecture review 2026-05-30, candidate #3

## Proposal that was considered

Split `BibliographicItem` into two types:

- An **immutable input-only** `BibliographicItem` carrying parsed field values (authors, titles,
  journals, DOIs, pages, publication year) plus type characteristics.
- A mutable **`DeduplicationEntry`** wrapper carrying the deduplication state (`label`,
  `isKeptBibliographicItem`, `isPresentInOldFile`) and Enrichment output (`pagesOutput`).

Additionally, replace the four boolean type flags (`isReply`, `isCochrane`, `isPhase`,
`isClinicalTrialGov`) with a `BibliographicItemType` enum.

## Decision

**Do not split. Do not introduce the enum.** Keep `BibliographicItem` as a single `@Data` POJO.

## Reasons

### 1. Bibliographic Item types can combine — the enum gives no type-safety win

A Bibliographic Item can simultaneously be a Reply, a Phase Publication, and/or a
ClinicalTrials.gov Publication. A plain `enum` cannot represent combinations; a
`Set<BibliographicItemType>` (or `EnumSet`) would be needed instead.  
That flips every call site from `r.isReply()` to `r.getTypes().contains(REPLY)`, which is more
verbose with no correctness gain. If helper methods are kept to preserve the current call sites,
the result is indirection added on top of indirection — not a deepened module.

### 2. No bug has ever resulted from the mixed POJO

The comparison services (`DefaultAuthorsComparisonService`, `DefaultTitleComparisonService`,
`DefaultJournalComparisonService`, `DefaultPagesComparisonService`) read only input fields and type
flags; none reads `label`, `isKeptBibliographicItem`, or `isPresentInOldFile`. The "dedup state
leaks into the domain model" concern is potential, not actual. There is no correctness problem to
fix.

### 3. The DeduplicationEntry wrapper fails the deletion test

Introducing `DeduplicationEntry` does not move complexity away — it adds a wrapping layer that
`DeduplicationService`, `EnrichmentService`, and `BibliographicItemWriter` must all thread through,
with a conversion step after reading. The comparison services would still receive `BibliographicItem`
directly. Net change: more code, same semantics, no bug prevented.

### 4. Some dedup state is mode-specific — the split obscures rather than clarifies

`isKeptBibliographicItem` and `pagesOutput` (Enrichment output) are only meaningful in Remove Mode.
`isPresentInOldFile` is only relevant in Two-file runs. Moving these to a shared `DeduplicationEntry`
mixes concerns from different modes without making either mode's logic clearer.

## What to watch for (conditions that would reopen this)

- A comparison service is found to read or mutate `label` or `isKeptBibliographicItem` — that
  would be a real seam violation.
- A test becomes hard to write because constructing a `BibliographicItem` for a comparison test
  forces setting dedup state.
- The number of boolean type flags grows past ~6, making the POJO interface meaningfully harder
  to reason about.
