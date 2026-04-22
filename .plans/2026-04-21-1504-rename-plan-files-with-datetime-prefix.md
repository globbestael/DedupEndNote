# Plan: Make `.plans/` file order visible by filename

**Status: executed**

## Context

The `.plans/` folder contains 10 executed plan files. Most share the same filesystem mtime (`Apr 13 15:24`) — probably from a bulk touch/restore — so mtime is not a reliable order signal. The user wants the execution order to be visible directly from the filename, and wants the convention documented so future plans follow it automatically.

## Answers to the three questions

### 1. Can the original order be retrieved?

Yes — `git log --diff-filter=A` on `.plans/` gives the commit that first added each plan. That is the authoritative chronology. Reconstructed order (oldest → newest, commit time):

| # | Commit time | Original filename |
|---|---|---|
| 1 | 2026-04-08 19:07 | `rename-testdir-to-baseDir.md` |
| 2 | 2026-04-09 13:53 | `reduce-test-duplication-abstract-integration-test.md` |
| 3 | 2026-04-09 17:18 | `tag-and-group-tests-unit-vs-integration.md` |
| 4 | 2026-04-09 18:05 | `move-file-only-tests-to-unit-group.md` |
| 5 | 2026-04-09 23:10 | `refactor-websocket-decouple-consumer.md` |
| 6 | 2026-04-10 17:32 | `add-progress-during-file-parsing.md` |
| 7 | 2026-04-10 17:55 | `cache-patterns-and-jws-in-comparison-service.md` |
| 8 | 2026-04-13 14:46 | `redesign-ui-stepper.md` |
| 9 | 2026-04-19 17:14 | `reorganize-tests-by-category-normalization-similarity-jwsimilarity.md` |
| 10 | 2026-04-20 21:00 | `migrate-tests-to-folder-based-grouping.md` |

### 2. Chosen filename format

**`YYYY-MM-DD-HHMM-<slug>.md`** — e.g. `2026-04-08-1907-rename-testdir-to-baseDir.md`.

- Sorts chronologically in any file explorer without git.
- Intra-day order is visible from the filename alone — no `git log` needed.
- Date/time = commit time of execution, not drafting time.
- HHMM is sufficient resolution; bump to HHMMSS only if a same-minute collision occurs.
- Rejected: `YYYY-MM-DD-<slug>` (forces git for intra-day ties); `NNN-<slug>` (opaque, renumbering churn).

### 3. Convention location

Extended `## Plans` section in `CLAUDE.md` and added a trigger to the "Keeping this file current" checklist.

## Changes made

### A. Renamed 10 existing plan files via `git mv`

```
rename-testdir-to-baseDir.md                                    → 2026-04-08-1907-rename-testdir-to-baseDir.md
reduce-test-duplication-abstract-integration-test.md            → 2026-04-09-1353-reduce-test-duplication-abstract-integration-test.md
tag-and-group-tests-unit-vs-integration.md                      → 2026-04-09-1718-tag-and-group-tests-unit-vs-integration.md
move-file-only-tests-to-unit-group.md                           → 2026-04-09-1805-move-file-only-tests-to-unit-group.md
refactor-websocket-decouple-consumer.md                         → 2026-04-09-2310-refactor-websocket-decouple-consumer.md
add-progress-during-file-parsing.md                             → 2026-04-10-1732-add-progress-during-file-parsing.md
cache-patterns-and-jws-in-comparison-service.md                 → 2026-04-10-1755-cache-patterns-and-jws-in-comparison-service.md
redesign-ui-stepper.md                                          → 2026-04-13-1446-redesign-ui-stepper.md
reorganize-tests-by-category-normalization-similarity-jwsimilarity.md → 2026-04-19-1714-reorganize-tests-by-category-normalization-similarity-jwsimilarity.md
migrate-tests-to-folder-based-grouping.md                       → 2026-04-20-2100-migrate-tests-to-folder-based-grouping.md
```

### B. Updated `CLAUDE.md`

- Added `## Plans` filename format rule.
- Added trigger: "Plan-file naming convention changed (plans section)".
