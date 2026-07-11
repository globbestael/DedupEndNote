# Per-pair cooperative cancellation in the comparison loop

Status: ready-for-agent

## Parent

`.scratch/security-hardening-2026-07/PRD.md`

## What to build

Make the comparison phase of a deduplication run cooperatively cancellable at per-pair
granularity. Today the interruption check happens only once per pivot (between pivots),
so a long-running comparison phase can only be interrupted between pivots, not during
one. Add a cheap interruption/deadline check inside the inner comparison loop so a
timeout or a user Cancel is observed within a bounded number of pair comparisons.

The existing cancellation contract (throwing `CancelledException`) is reused; only the
frequency of the check changes. The check must be allocation-free on the hot path so it
does not measurably slow normal runs.

Combined with the guaranteed permit release (slice 01), this bounds the wasted CPU of a
cancelled/timed-out run: the slot is already freed by 01, and this slice makes the worker
actually stop promptly.

## Acceptance criteria

- [ ] The inner comparison loop observes interruption/deadline per pair, not only per pivot.
- [ ] A mid-run cancel stops the comparison phase within a bounded number of comparisons
      (demonstrated by a test), rather than only at the next pivot boundary.
- [ ] The check adds no allocation on the hot path; normal-run behaviour and results are
      unchanged.
- [ ] Deduplication output for existing integration fixtures is byte-for-byte unchanged
      (no accidental behaviour change from the added check).
- [ ] A test asserts prompt cancellation during the comparison phase (extend
      `CancellationTests` / `MissedDuplicatesTests` prior art).

## Blocked by

None - can start immediately. (Best demonstrated together with slice 01, but independent.)
