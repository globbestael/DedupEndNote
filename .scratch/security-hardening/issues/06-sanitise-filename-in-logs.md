# Sanitise attacker-controlled filename in logs

Status: ready-for-human

## What to build

Decide how to handle user-supplied filenames in the four `log.warn(...)` calls in `DedupEndNoteController` that log traversal-attempt exceptions. The current `e.getMessage()` includes the raw filename, enabling log forging if the name contains newline characters.

Two options:

**Option A — Omit the filename:** Log a fixed endpoint label (e.g. `"Path traversal attempt on upload endpoint"`) and drop the user-supplied value entirely.

**Option B — Sanitise:** Strip `\r` and `\n` from the logged string before writing (e.g. `e.getMessage().replaceAll("[\r\n]", "_")`).

A human decision is needed because Option A loses context that may be useful for incident investigation, while Option B retains it with minimal risk.

## Acceptance criteria

- [ ] Approach is decided
- [ ] All four `log.warn` call sites in `DedupEndNoteController` are updated consistently
- [ ] A filename containing `\n` no longer produces a multi-line log entry
- [ ] Traversal attempts are still logged at WARN level

## Blocked by

None — can start immediately.
