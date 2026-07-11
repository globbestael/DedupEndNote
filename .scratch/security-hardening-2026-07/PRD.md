# PRD: Security hardening — remediation of the 2026-07-11 review

Status: ready-for-agent

Source: `.scratch/security-review-2026-07-11-opus48.md` (full review, branch `design`, HEAD `eae41b8`).

## Problem Statement

DedupEndNote is an unauthenticated, public deduplication tool: anyone can upload
RIS files and start a deduplication run. The 2026-07-11 security review found that
a user — with no privilege and only a few small crafted uploads — can permanently
deny service to everyone else:

- A single slow field comparison inside a deduplication run cannot be aborted. The
  run-timeout and the user-facing Cancel both fail to stop a CPU-bound step, so the
  run holds its concurrency permit and pins a carrier thread for the JVM lifetime.
  Repeat four times (the concurrent-run cap) and the whole service is wedged with no
  restart-free recovery.
- The cheapest way to make one comparison hang is polynomial regex backtracking in
  journal-name matching. A recent commit narrowed this (10-word pattern cap) but did
  not close it, because the match target is still length-unbounded.
- Separately, the compiled-pattern caches used by journal matching grow without
  bound for the JVM lifetime, keyed on attacker-controlled journal strings — a
  slow-burn out-of-memory vector.
- Supporting weaknesses raise the reachability of the above: the upload rate limiter
  is keyed on a client-chosen value (trivially reset per request), abandoned
  per-session upload directories are never reclaimed and can fill the disk, and no
  HTTP security response headers are set.

From the maintainer's perspective: the tool must survive hostile input without a
human having to restart the process, and must degrade gracefully (reject / time out)
rather than seize up.

## Solution

Make every deduplication run bounded and genuinely abortive, remove the pathological
regex cost, bound all process-global memory growth, and add the standard perimeter
hardening. Concretely:

1. Introduce a deep module that owns the concurrency permit, the worker lifecycle,
   the per-run timeout and the cancellation registry, and **guarantees** the permit
   is released on every outcome (success, timeout, user-cancel, exception, or a task
   that refuses to stop). The controller delegates to it.
2. Make the comparison loop cooperatively cancellable at per-pair granularity so a
   timeout or Cancel is observed within a bounded number of comparisons rather than
   only between pivots.
3. Replace the backtracking journal abbreviation/initialism matching with linear,
   non-backtracking matching (or a strict length/token bound), extracted into a
   testable module.
4. Replace the three unbounded static pattern caches with a single size-bounded cache.
5. Key the upload rate limiter on client IP (honouring a trusted proxy header), add a
   scheduled reaper for stale session directories, and set HTTP security headers on
   every response.

The end state: a hostile upload results in a rejected or timed-out run that frees its
permit and cleans up after itself, and sustained hostile volume cannot exhaust heap or
disk. Legitimate users are unaffected.

## User Stories

1. As a maintainer, I want a deduplication run's concurrency permit to be released on
   every possible outcome, so that a single bad run cannot permanently reduce the
   available run slots.
2. As a maintainer, I want a run that exceeds the configured timeout to actually stop
   consuming CPU, so that it does not pin a carrier thread indefinitely.
3. As a user, I want the Cancel action to stop my running deduplication promptly, so
   that I am not left waiting on a run I no longer need.
4. As a maintainer, I want cancellation and timeout to be observed inside the
   comparison loop (per pair), so that a long-running comparison phase can be
   interrupted within a bounded time rather than only between pivots.
5. As a maintainer, I want a task that refuses to stop after interruption to still not
   block release of its permit, so that the service self-heals even against a
   non-cooperative step.
6. As a maintainer, I want journal-name matching to run in time linear in the input
   length regardless of the journal strings supplied, so that a crafted Bibliographic
   Item cannot make one comparison hang.
7. As a maintainer, I want journal matching to produce the same duplicate/non-duplicate
   decisions as today for all existing validated datasets, so that deduplication
   accuracy (sensitivity/specificity) is unchanged by the security fix.
8. As a maintainer, I want the compiled-pattern cache to have a bounded maximum size,
   so that processing many distinct journal names cannot grow the heap without limit.
9. As a maintainer, I want the pattern cache to evict least-recently-used entries when
   full, so that the legitimate hot set of journal names stays cached while adversarial
   one-off strings are discarded.
10. As a maintainer, I want the upload rate limiter to be keyed on the client's network
    identity rather than a client-supplied value, so that an automated client cannot
    reset its own quota by minting a fresh identifier per request.
11. As a user running the two-file flow, I want my two rapid uploads from one page load
    to share a single rate-limit budget, so that legitimate two-file use is not blocked.
12. As a maintainer, I want stale per-session upload directories to be deleted
    automatically after a configurable age, so that abandoned uploads and derived
    outputs do not fill the upload partition.
13. As a maintainer, I want a sweep of orphaned session directories at application
    startup, so that directories left behind by a previous crash or restart are
    reclaimed.
14. As a maintainer, I want the reaper to never delete a directory belonging to an
    in-progress run, so that active deduplications are not disrupted.
15. As a user, I want every response to carry standard security headers
    (nosniff, frame-ancestors/X-Frame-Options, Referrer-Policy), so that the app
    resists clickjacking and MIME-sniffing.
16. As a maintainer, I want the timeout, cache size, reaper age and rate-limit settings
    to be externally configurable with safe defaults, so that I can tune them per
    deployment without a code change.
17. As a maintainer, I want a run that is rejected because the server is busy to return
    a clear "try again" response, so that the existing busy-signal behaviour is
    preserved after refactoring.
18. As a maintainer, I want cancelled and timed-out runs to continue deleting their
    partial output files, so that a failed run cannot leave a misleading result on disk.
19. As a maintainer, I want the record-count cap and load-time memory bound to remain
    in force, so that the large-file protections verified as adequate are not regressed.
20. As a maintainer, I want the new concurrency module to be unit-testable without
    standing up an HTTP server, so that its correctness (the highest-risk logic) is
    covered by fast tests.
21. As a maintainer, I want each new module to have a small, stable interface, so that
    future changes to internals do not ripple into the controller or the comparison
    engine.
22. As a security reviewer, I want the residual journal-ReDoS and the non-abortive
    timeout both closed, so that the review's single High finding can be marked
    resolved rather than merely reduced.

## Implementation Decisions

**Scope:** all findings from the 2026-07-11 review — H1 (timeout + cancellation),
H1 Layer B (journal ReDoS), M (bounded caches), and the Low hardening trio
(rate-limit key, session-dir reaper, security headers).

### Module: bounded, abortive run execution (new, deep)

- Extract the concurrency orchestration currently inline in the controller
  (`runDedup`) into a dedicated module that owns: the run-slot semaphore, the worker
  execution, the per-run timeout, and the map of in-flight runs used by Cancel.
- Interface, roughly: `runWithLimit(sessionId, task, outputPathForCleanup, timeout)`
  returning a small outcome value (COMPLETED+result / BUSY / TIMED_OUT / CANCELLED /
  FAILED+message), plus `cancel(sessionId)`.
- **Correctness contract:** the permit MUST be released exactly once on every outcome,
  including the case where the worker does not stop after interruption. Do **not** gate
  permit release on a blocking executor shutdown. Achieve this by acquiring/releasing
  the permit around the future's lifecycle independently of any executor `close()`, or
  by using a shared bounded executor with an `awaitTermination(timeout)` + forceful
  fallback so a stuck task cannot hold the release.
- Preserve existing observable behaviour: BUSY → HTTP 429 with the current message;
  TIMED_OUT → 503 with the timeout message and output-file cleanup; user CANCELLED and
  domain errors → 200 with the existing messages and cleanup; the request-scope
  propagation to the worker thread must be retained.

### Module: cooperative cancellation in the comparison engine (modify)

- Add an interruption/deadline check at per-pair granularity inside the inner
  comparison loop of `compareSet` (today the check is only per-pivot). The existing
  `CancelledException` contract is reused; only the check frequency changes.
- The check must be cheap (no allocation on the hot path) so it does not measurably
  slow normal runs.

### Module: journal pattern matching (extract, deep)

- Extract the abbreviation / initialism / starting-initialism matching out of
  `DefaultJournalComparisonService` into a standalone matcher with a pure interface
  (inputs: two normalized journal strings; output: boolean match).
- Replace the backtracking `\bw1.*\bw2.*…` regex approach with **linear** logic:
  tokenize and test ordered subsequence / `startsWith` containment in a single pass,
  so cost is linear in the combined input length and independent of token count.
- If any regex is retained, it must additionally be guarded by a strict bound on the
  target string length/token count (the shipped 10-word *pattern* cap is insufficient
  while the *target* is unbounded).
- **Behaviour parity is a hard requirement:** the matcher must reproduce today's
  duplicate/non-duplicate decisions across the existing validated journal-pair fixtures.
  The special-case handling (e.g. `Samj`→`SAMJ`, `AJNR`→`AJN`) must be preserved.

### Module: bounded pattern cache (new, deep)

- Replace the three `public static` unbounded `ConcurrentHashMap<String,Pattern>`
  caches with a single size-bounded, thread-safe LRU cache behind a small interface
  (`get(key, mappingFunction)`).
- Maximum size is configurable with a safe default (a few thousand entries covers the
  legitimate hot set). Eviction is least-recently-used.
- If the journal matcher becomes fully regex-free, the cache may instead memoize the
  tokenized form; either way no process-global structure may grow without bound.

### Module: rate-limit keying (modify)

- Change `RateLimitInterceptor` to key primarily on client IP, derived from a trusted
  `X-Forwarded-For` (first hop) when present, falling back to the remote address.
- Retain the two-file-flow allowance: two rapid uploads from one page load must share
  a single counter (burst ≥ 2). The client-supplied `wssessionId` may still be combined
  as a secondary dimension but must not be the sole key, so a fresh id cannot reset the
  quota.

### Module: session-directory reaper (new, @Scheduled, deep)

- Add a scheduled task that deletes per-session upload directories older than a
  configurable age (default ~24h), plus a one-shot sweep at application startup for
  directories orphaned across restarts.
- Must not delete a directory whose run is currently in-flight (consult the run
  registry / a last-touched timestamp). Requires enabling scheduling.
- Age determination must be driven by an injectable time source so it is testable
  deterministically.

### Module: security headers (new, shallow)

- Add an interceptor/filter that sets, on every response, at minimum:
  `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN` (or CSP
  `frame-ancestors 'self'`), and `Referrer-Policy: strict-origin-when-cross-origin`.

### Configuration

- New/kept externalized settings with safe defaults: run timeout (existing),
  concurrent-run cap (existing), pattern-cache max size (new), session-dir max age
  (new), rate-limit cooldown/burst (existing). No secrets introduced.

### Non-regression guardrails

- The record-count cap and cap-before-load ordering (verified adequate in the review)
  must remain. Path-traversal validation, per-session UUID isolation, generic error
  responses, and the WebSocket UUID scheme are out of scope and must not be weakened.

## Testing Decisions

**What makes a good test here:** assert on externally observable behaviour and
contracts, not internals. For the concurrency module that means observing permit
availability and returned outcomes, not inspecting private fields; for the matcher and
cache it means input→output behaviour. Prefer fast tests with no Spring context where
the module allows it (matcher, cache, reaper-with-injected-clock); reserve
`@SpringBootTest` for the wiring that genuinely needs it.

**Modules to be tested (all four selected):**

1. **BoundedDedupRunner** — the highest-risk logic. Cover: permit is released and
   becomes re-acquirable after COMPLETED, TIMED_OUT, user-CANCELLED, and FAILED
   outcomes; a task that ignores interruption still does not leak a permit (a
   subsequent run can acquire a slot within a bounded time); BUSY is returned when all
   permits are held; Cancel of an unknown session is handled. Prior art:
   `integration/ConcurrentRunsTests` (429/semaphore), `CancellationTests`,
   `DeduplicationTimeoutTests` — extend/mirror these, and add fast isolated tests for
   the release contract that do not need HTTP.
2. **BoundedPatternCache** — cover: cache hit returns the same instance; miss computes
   via the mapping function; size never exceeds the configured cap; least-recently-used
   entry is evicted when the cap is exceeded. Plain JUnit, no Spring. Prior art: the
   normalization/comparison unit tests under `unit/services/`.
3. **SessionDirectoryReaper** — cover: directories older than the threshold are deleted,
   younger ones survive, and an in-flight/last-touched directory is spared; startup
   sweep removes a pre-existing stale directory. Drive age with an injected clock
   against a temp directory. Prior art: file-writing integration tests that use
   `testDir`/`deleteDerivedOutputs`.
4. **JournalPatternMatcher** — cover: decision parity with the current implementation
   across the existing validated journal-pair fixtures (abbreviation, initialism,
   starting-initialism, special cases), and bounded/linear cost on adversarial input
   (a many-token journal string with a non-matching poison token completes quickly).
   Prior art: `DefaultJournalComparisonServiceTest` (115 cases),
   `DefaultJournalComparisonServiceIssnTest`, `JWSimilarityJournalTest` — the parity
   suite should reuse these fixtures.

Follow the project test taxonomy and profiles (unit vs integration split, `-Punit-tests`
for the fast isolated modules). Tests with expected errors use the
`EXPECTED_NUMBER_OF_ERRORS` constant convention rather than being disabled.

## Out of Scope

- Adding authentication, sessions, or CSRF protection — the tool is intentionally
  public and unauthenticated (accepted-by-design).
- Restricting the STOMP `setAllowedOriginPatterns("*")` — intentional and documented.
- TLS termination in-app — remains a reverse-proxy responsibility; only documenting the
  expectation (an Info item) is optional here.
- Reworking the O(n²) comparison algorithm itself; the record-count cap already bounds it.
- The parse-time field regexes (COMMENT/ERRATUM/SOURCE) — Info-level, no change unless
  the record-cap assumptions change.
- Any change to path-traversal validation, per-session isolation, error handling, or the
  WebSocket UUID scheme (verified sound; must not regress).

## Further Notes

- The residual journal-ReDoS (H1 Layer B) and the non-abortive timeout (H1 Layer A) are
  two halves of one High finding; **Layer A is the higher-value fix** because it converts
  every permanent wedge into a recoverable timeout regardless of trigger. If the work is
  split into issues, sequence Layer A first.
- The already-shipped 10-word pattern cap (commit `095deea`) narrowed but did not close
  Layer B; the linear-matcher work supersedes it and should remove the interim cap logic
  once parity is proven.
- Keep the review file (`.scratch/security-review-2026-07-11-opus48.md`) as the
  authoritative finding list; update its summary table when items are closed.
- Per project conventions, update CLAUDE.md / `docs/architecture.html` /
  `docs/test-hierarchy.md`/`docs/testing-guide.md` in the same commits when services,
  test classes, or algorithm steps change.
