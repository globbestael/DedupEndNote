# Cap journal-name length at 150 characters

Status: implemented (tests green; pending commit & human review)

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

- [x] Normalized journal names longer than 150 characters are truncated to 150 characters
      during normalization.
- [x] The truncation applies to the strings used both to build the match pattern and as
      the match target.
- [x] Deduplication decisions for existing validated journal-pair fixtures are unchanged
      (real journal names are well under 150 chars, so parity is expected).
- [x] A test feeds an over-length (crafted, multi-token) journal name and asserts the
      comparison completes quickly and the stored journal name is capped at 150 chars.
- [x] The 150 limit is a named constant (configurable value optional).
- [x] `docs/algorithm.md` note added if the length cap affects the documented journal step.

## Blocked by

None - can start immediately.

## Comments

**Implemented.** `JournalsNormalizationService.normalizeJournal` now ends with
`(r.length() > MAX_JOURNAL_LENGTH ? r.substring(0, MAX_JOURNAL_LENGTH) : r).strip()`
with `MAX_JOURNAL_LENGTH = 150`. Every normalized journal — the raw string and all split
variants — flows through this method, so both the abbreviation/initialism pattern source
and the match target are capped. **Truncate-before-strip** (reviewer-requested): a cut on
a space boundary cannot leave trailing whitespace, preserving the "all normalized journals
are stripped" invariant. The early `http` return (clinicaltrials.gov URLs, ~40 chars,
compared only via equality/JWS) is deliberately left uncapped.

Verification:
- `JournalsNormalizationServiceTest.normalizeJournal_capsLengthAt150_andStaysStripped`:
  feeds `"ab ".repeat(100)` (300 chars, cut lands exactly on a space) and asserts result
  ≤150 **and** `== result.strip()` — the second assertion specifically guards the
  strip/truncate ordering.
- `DefaultJournalComparisonServiceTest.compare_overlengthJournals_isCappedAndCompletesQuickly`:
  `@Timeout(5s)`, asserts stored journals ≤150 and `compare` returns (finished in ms).
- Parity: full unit suite (601), plus `DeduplicationServiceTests` / `MissedDuplicatesTests`
  output-parity guards — all unchanged (real names <150).

Note (recorded in the PRD too): 150 + the shipped 10-word pattern cap bounds the
*attacker-scalable* dimension; it is a fixed ceiling, not a guarantee every comparison is
fast. Recoverability of any residual cost is covered by slices 01 + 02.
