# Cap journal-name length at 150 characters

Status: ready-for-agent

## Parent

`.scratch/security-hardening-2026-07/PRD.md`

## What to build

Bound the length of normalized journal names used in comparison so the ReDoS *target*
can no longer be scaled by attacker input. The existing backtracking regex approach and
the already-shipped 10-word pattern cap are kept as-is; this slice only adds a hard
150-character maximum on the journal string.

When a normalized journal name exceeds 150 characters, **truncate it to 150 characters**
(the maintainer's chosen approach). Apply the cap during journal normalization so both
the pattern source and the match target are bounded.

Note (for the implementer, not a standalone guarantee): 150 chars combined with the
10-word cap removes the attacker-*scalable* dimension — a crafted Bibliographic Item can
no longer grow one comparison's cost with input size — but it is a fixed ceiling, not a
promise that every comparison is fast. The recoverability guarantees come from slices 01
(permit release) and 02 (prompt cancellation); this slice is the third leg of that
defense, not a complete fix on its own.

## Acceptance criteria

- [ ] Normalized journal names longer than 150 characters are truncated to 150 characters
      during normalization.
- [ ] The truncation applies to the strings used both to build the match pattern and as
      the match target.
- [ ] Deduplication decisions for existing validated journal-pair fixtures are unchanged
      (real journal names are well under 150 chars, so parity is expected).
- [ ] A test feeds an over-length (crafted, multi-token) journal name and asserts the
      comparison completes quickly and the stored journal name is capped at 150 chars.
- [ ] The 150 limit is a named constant (configurable value optional).
- [ ] `docs/algorithm.md` note added if the length cap affects the documented journal step.

## Blocked by

None - can start immediately.
