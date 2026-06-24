# Replace the `enhance` boolean with `DeduplicationMode` and merge the two writer methods

## Context

`BibliographicItemWriter` still carries a `boolean enhance` flag on its private
`writeBibliographicItem(...)` method (line 219). ADR-0003 replaced the public
`boolean markMode` parameter with the `DeduplicationMode` enum end-to-end, but this
private flag was left behind as a vestige. `writeDeduplicatedBibliographicItems`
always passes `true`, `writeMarkedBibliographicItems` always passes `false` — so the
flag is just a re-encoding of the mode (`REMOVE` → `true`, `MARK` → `false`).

Two problems to fix:

1. **Terminology.** "enhance" reads as a synonym for "enrich", but enrichment has
   already happened upstream (`enrichmentService.enrich(...)` runs in
   `DeduplicationService` *before* the writer is called, only in REMOVE mode). The
   writer doesn't enrich — it just needs to know the mode. Branch on `mode` directly.

2. **Duplication.** `writeDeduplicatedBibliographicItems` (lines 43–129) and
   `writeMarkedBibliographicItems` (lines 131–212) are ~85% identical. The boolean
   is what prevents merging them; passing `DeduplicationMode` lets them collapse into
   one method whose few real differences become explicit mode branches.

Outcome: one mode-aware writer method, no "enhance" term, the dead `startsWith`
branch removed, and MARK no longer silently swallows IO errors.

## Confirmed difference analysis (REMOVE vs MARK)

All differences are either harmless or cleanly mode-conditional:

- **`lineNumber` tracking** — REMOVE tracks it for the error message; MARK doesn't.
  Keep tracking in the merged method (MARK loses nothing).
- **ER `map.put("ID", …)`** — REMOVE nests it inside `if (item != null)`; MARK places
  it outside. Both safe (`map` is cleared per record; non-written records never reach
  the writer). Use MARK's placement.
- **ER label** — MARK adds `LB` from `item.getLabel()`; REMOVE does not. Make
  mode-conditional (`mode == MARK`).
- **`LB` switch case** — MARK has `case "LB": break;` to drop a stale label before
  re-adding its own; REMOVE has no case, so an input `LB` passes through. Preserve
  existing behavior: only drop `LB` when `mode == MARK`.
- **`default` case** — REMOVE has a redundant `if (line.startsWith(fieldName))` whose
  two branches are identical (dead branching). Collapse to MARK's single-branch form.
- **`catch`** — REMOVE builds a message, logs, and throws `RuntimeException`; MARK only
  `e.printStackTrace()` (swallows). Unify on REMOVE's throwing version — strictly
  better; MARK swallowing IO errors is a latent bug.

## Changes

### 1. `src/main/java/edu/dedupendnote/services/BibliographicItemWriter.java`

Merged the two public methods into one mode-aware method:

```java
public int writeBibliographicItems(List<BibliographicItem> bibliographicItems,
        Path inputPath, Path outputPath, DeduplicationMode mode) {
    ...
}
```

- Kept `lineNumber` tracking and the throwing `catch` block from the REMOVE version.
- LB handling: `case "LB"` breaks in MARK mode (drops stale label; computed label added
  at ER); in REMOVE mode falls through to `default` so existing LB is preserved.
- `default` case: collapsed to single-branch form (dead `startsWith` branch removed).
- Private `writeBibliographicItem` signature changed from `boolean enhance` to
  `DeduplicationMode mode`; branches on `boolean removeMode = mode == DeduplicationMode.REMOVE`.
- Comment updated: "in REMOVE mode C7 is skipped; in MARK mode C7 is kept".

### 2. `src/main/java/edu/dedupendnote/services/DeduplicationService.java`

Four call sites updated to `writeBibliographicItems(items, inputPath, outputPath, mode)`.
Surrounding `switch (mode)` blocks retained (enrich/filter/progress logic differs).

### 3. `src/test/java/edu/dedupendnote/validation/services/ValidationIOService.java`

Comment updated from "non-enhance path" to "MARK-mode path".

### 4. Docs / housekeeping

- `docs/architecture.html` sequence diagram: `writeBibliographicItems(mode)`.
- `TODO.md`: resolved TODO about boolean mirroring `DeduplicationMode` removed.
- `docs/architecture-review-20260530.html`: dated snapshot — left as-is.

## Verification

```bash
./mvnw test -Punit-tests        # BUILD SUCCESS — 584 tests, 9 skipped
./mvnw test -Pintegration-tests # BUILD SUCCESS — 23 tests, 1 skipped
```
