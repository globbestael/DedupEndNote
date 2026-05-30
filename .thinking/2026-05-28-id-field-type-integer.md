# `BibliographicItem.id`: change type from `String` to `Integer`?

_2026-05-28_

---

## Question

In `BibliographicItem` the `id` field is a `String`. EndNote input files always have a numeric ID field, Zotero input files never have an ID field, and if an input file has another type of ID it should not be accepted. So why not change the type of the `id` field to a Java `Integer`?

---

## Answer

The change is sound. The mechanics work out cleanly everywhere except one spot that becomes cleaner, not harder.

### What changes simply

- **Fallback ID generation** (Zotero / no-ID files): `setId(Integer.toString(missingId++))` → `setId(missingId++)`
- **Reading from RIS**: `setId(fieldContent)` → `setId(Integer.parseInt(fieldContent))` — this is exactly where a `NumberFormatException` should be thrown to reject non-numeric IDs
- **IOService output filters**: `!p.getId().startsWith("-")` → `p.getId() > 0` — more honest
- **Old-file negation** (two-file dedup): `r.setId("-" + r.getId())` → `r.setId(-r.getId())` — cleaner

### The one non-obvious point — the `label` field

`label` stays `@Nullable String` because it is written to the RIS `LB` field. It currently receives `pivot.getId()` directly (String → String). With `Integer` it becomes `pivot.getId().toString()` — but crucially the label values for old-file items will still start with `"-"` (negative integer serialised as string), so the filter at `DeduplicationService` line 329 (`!r.getLabel().startsWith("-")`) continues to work unchanged.

### The `"-"` prefix serves two purposes, both covered by Integer negation

1. **Uniqueness across both files** — negative IDs cannot collide with positive new-file IDs
2. **Label propagation** — a new-file item labelled with a negative ID is a duplicate of an old-file item → excluded from output

### One edge case to be aware of

If an EndNote database ever assigned ID 0 to a record, negation would be a no-op (`-0 == 0`). In practice EndNote IDs are sequential starting at 1, so this is not a real concern.

### Verdict

Go ahead. The change tightens the type, makes the intent explicit at the domain level, and turns a string-manipulation hack into a proper numeric operation. The only touch points requiring care are:

- The two `setLabel(pivot.getId())` calls in `DeduplicationService` that need `.toString()`
- `Integer.parseInt()` at read time in `IOService` for validation
