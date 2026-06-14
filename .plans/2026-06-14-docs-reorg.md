# Documentation reorganization — reduce duplication across four authoritative files

## Context

Four files carried authoritative technical information: `CLAUDE.md`, `CONTEXT.md`,
`docs/architecture.html`, `docs/Claude_analysis.md`. The same facts were restated across files;
`docs/Claude_analysis.md` was additionally stale (pre-`BibliographicItem` rename, wrong class names,
Spring Boot 4.0.1). Goal: one authoritative owner per fact type; restatements replaced by links.

## Deliverables (all written to .scratch/ as drafts for user review)

| Draft | Preferred destination | Action |
|---|---|---|
| `.scratch/docs-reorg-algorithm.md` | `docs/algorithm.md` | create — ported from Claude_analysis.md with corrections |
| `.scratch/docs-reorg-CLAUDE.md` | `CLAUDE.md` | replace — architecture section trimmed, documentation map added |
| `.scratch/docs-reorg-architecture.html` | `docs/architecture.html` | replace — Spring Boot version bump, file-path para trimmed, algorithm link added |
| `.scratch/docs-reorg-adr-0002-documentation-layout.md` | `docs/adr/0002-documentation-layout.md` | create |
| `.scratch/docs-reorg-adr-0003-deduplicationmode-enum.md` | `docs/adr/0003-deduplicationmode-enum.md` | create |
| `.scratch/docs-reorg-adr-0004-single-source-version-numbering.md` | `docs/adr/0004-single-source-version-numbering.md` | create |
| `.scratch/docs-reorg-adr-0005-three-folder-test-taxonomy.md` | `docs/adr/0005-three-folder-test-taxonomy.md` | create |
| `.scratch/docs-reorg-DELETE-Claude_analysis.md` | (marker) | delete `docs/Claude_analysis.md` after algorithm.md accepted |

`CONTEXT.md` was not changed (content is already clean).

## Single-ownership matrix achieved

| Fact | Owner |
|---|---|
| Domain term definitions | `CONTEXT.md` |
| Service map, pipeline / sequence diagrams | `docs/architecture.html` |
| Algorithm steps, thresholds, INSUFFICIENT_DATA, special types, enrichment | `docs/algorithm.md` |
| Coding rules, commands, test structure, config, release | `CLAUDE.md` |
| Decision rationale (why X, rejected alternatives) | `docs/adr/` |

## Key changes per file

**docs/algorithm.md (new):** All algorithm detail from Claude_analysis.md, corrected:
- `Publication` → `BibliographicItem`
- `ComparisonService` → four `Default*ComparisonService` + `FieldComparators`
- `IOService` → `BibliographicItemReader` / `BibliographicItemWriter`
- Hard-coded `:line-number` references removed
- Step numbering aligned with CLAUDE.md (year = step 1)
- Terminology aligned with CONTEXT.md (Reply, Cochrane Review, Phase Publication, etc.)

**CLAUDE.md (trimmed):** Architecture section reduced from ~80 lines to ~25 lines:
- Detailed service responsibility table → link to architecture.html service map
- Data-flow ascii block → link to architecture.html diagrams
- Exact threshold numbers → link to algorithm.md
- Mode prose → link to CONTEXT.md
- Retained: package index (one line each), one-line-per-step algorithm, Modes one-liner, full file-path naming convention rule
- Added: Documentation map table, updated "Keeping this file current" triggers

**docs/architecture.html:** Three targeted changes:
- Header: "Spring Boot 4.0" → "Spring Boot 4.1"
- File-path naming paragraph (§3): 13-line prose → 2-line note + link to CLAUDE.md
- After pipeline note: added link to docs/algorithm.md
- Footer date: updated to 2026-06-14

**docs/adr/ (four new ADRs):** 0002 (doc layout), 0003 (DeduplicationMode enum), 0004 (version numbering), 0005 (test taxonomy). Each records: what was decided, what was rejected, and why.

## Verification

- `git status` shows only `.scratch/` and `.plans/` modified — the four original files untouched.
- Every threshold value from Claude_analysis.md appears in docs-reorg-algorithm.md.
- No `Publication\b` (as class name), `IOService`, `ComparisonService\b`, or `4\.0\.1` in any draft except where intentionally historical (ADRs may reference the superseded state).
- Every "see X" link in the drafts points to a real file/section.
