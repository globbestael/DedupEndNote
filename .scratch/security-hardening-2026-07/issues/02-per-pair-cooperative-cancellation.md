# Per-pair cooperative cancellation in the comparison loop

Status: implemented (tests green; pending commit & human review)

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

- [x] The inner comparison loop observes interruption/deadline per pair, not only per pivot.
- [x] A mid-run cancel stops the comparison phase within a bounded number of comparisons
      (demonstrated by a test), rather than only at the next pivot boundary.
- [x] The check adds no allocation on the hot path; normal-run behaviour and results are
      unchanged.
- [x] Deduplication output for existing integration fixtures is byte-for-byte unchanged
      (no accidental behaviour change from the added check).
- [x] A test asserts prompt cancellation during the comparison phase (extend
      `CancellationTests` / `MissedDuplicatesTests` prior art).

## Blocked by

None - can start immediately. (Best demonstrated together with slice 01, but independent.)

## Comments

**Implemented.** In `DeduplicationService.compareSet`, cached the worker thread once
(`Thread current = Thread.currentThread()`) and added a per-pair check at the top of the
inner loop: `if (current.isInterrupted()) throw new CancelledException(...)`. The existing
per-pivot check now reuses `current`. Interrupt conveys **both** triggers — user Cancel
and run timeout both interrupt via `future.cancel(true)` in `BoundedDedupRunner` — so no
separate deadline is passed down and the comparison engine stays decoupled from the runner.

Verification:
- New unit test `DeduplicationServiceCancellationTest` proves per-pair granularity: a fake
  `FieldComparators` interrupts the thread on the first `pages()` call; with 1 pivot + 3
  inner items the loop throws `CancelledException` after **exactly one** comparison
  (per-pivot-only would have run all three → count 3). No Spring context.
- Output parity: `DeduplicationServiceTests` + `MissedDuplicatesTests` (assert exact result
  strings) unchanged → the check is inert on normal runs.
- Phase-level / e2e cancellation still green: `CancellationTests` (2),
  `BrowserCancellationTests` (2, real WebSocket).

No `docs/algorithm.md` change: per-pair cancellation is runtime plumbing, not an algorithm
step/threshold/special-type change, and results are identical. The cancel path is already
documented at the right altitude in `architecture.html` (issue 01).
