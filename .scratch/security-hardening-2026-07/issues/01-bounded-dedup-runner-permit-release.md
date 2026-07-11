# Guaranteed permit release via BoundedDedupRunner

Status: implemented (tests green; pending commit & human review)

## Parent

`.scratch/security-hardening-2026-07/PRD.md`

## What to build

Extract the concurrency orchestration that currently lives inline in the controller
(run-slot semaphore, worker execution, per-run timeout, and the in-flight-run registry
used by Cancel) into a dedicated deep module that owns the full lifecycle of a
deduplication run.

The module's single hard contract: **the concurrency permit is released exactly once on
every outcome** — normal completion, busy rejection, timeout, user cancellation, domain
error, and the pathological case where the worker task does not stop after being
interrupted. Permit release must NOT be gated on a blocking executor shutdown; a stuck
task must be unable to hold the release. The controller becomes a thin caller that maps
the module's outcome to the existing HTTP responses.

Observable behaviour must be preserved: server-busy → 429 with the current message;
timeout → 503 with the timeout message and deletion of the partial output file; user
cancel and domain errors → 200 with the existing messages and output cleanup; the
request-scope propagation to the worker thread is retained.

This slice delivers the critical self-heal on its own: even if the worker keeps running
in the background, a run slot is always freed, so the service cannot be permanently
wedged. (Prompt stopping of the worker is a separate slice.)

## Acceptance criteria

- [x] Concurrency orchestration is extracted into a standalone module with a small
      interface (`runWithLimit(...)` returning an outcome value, plus `cancel(session)`).
- [x] The permit is released and becomes re-acquirable after each outcome: completed,
      timed-out, user-cancelled, domain-error, and a worker that ignores interruption.
- [x] A worker that never stops after interruption does not leak its permit — a
      subsequent run can acquire a slot within a bounded time.
- [x] Server-busy still returns HTTP 429 with the current message; timeout still returns
      503; cancel/domain-error still return 200 — all with the existing messages.
- [x] Timed-out and cancelled runs still delete their partial output file.
- [x] Request-scope context is still propagated to the worker thread.
- [x] The record-count cap and cap-before-load ordering are unchanged (no regression).
- [x] Isolated unit tests cover the permit-release contract without standing up an HTTP
      server; existing `ConcurrentRunsTests` / `CancellationTests` /
      `DeduplicationTimeoutTests` still pass (extended as needed).
- [x] CLAUDE.md / `docs/architecture.html` updated in the same commit for the new service.

## Blocked by

None - can start immediately.

## Comments

**Implemented.** `BoundedDedupRunner` (in `edu.dedupendnote.services`) holds a shared,
app-lifetime virtual-thread executor + `Semaphore` + cancel-by-session registry.
`runWithLimit` releases the permit in a `finally` that does **not** await worker
termination — on timeout/cancel it calls `future.cancel(true)` and returns immediately,
so an uncooperative worker keeps running orphaned but holds no permit. Nested
`RunStatus` enum + `RunOutcome` record; the controller now maps the outcome to HTTP
status, output-file cleanup, and progress messages.

Verification:
- Unit: 598 pass (incl. 8 new `BoundedDedupRunnerTest` cases; the key one asserts a
  spin-waiting task that ignores interruption still frees its permit on timeout).
- Integration guards: `ConcurrentRunsTests`, `CancellationTests`,
  `DeduplicationTimeoutTests` — 5 pass.
- Browser (real WebSocket): `BrowserCancellationTests` — 2 pass.

Note for slice 02: with the shared executor, a timed-out/cancelled worker stops only at
the next cooperative check (currently per-pivot). Slice 02 tightens that to per-pair.
