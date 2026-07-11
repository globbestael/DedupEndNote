# DedupEndNote Security Review — 2026-07-07 (Opus 4.8 rerun)

Rerun of the 2026-07-06 review (Sonnet 4.6) with fresh analysis. The main
difference is the **severity of the ReDoS finding**, which a deeper trace of
the cancellation/executor machinery raises from Medium to High — the timeout
and semaphore mitigations that made it look self-healing do not actually apply
to a thread stuck inside a regex match.

---

## Open Findings

---

**[HIGH] Non-self-healing DoS: ReDoS in journal comparison defeats the timeout AND holds the concurrency semaphore**
```
Location : DefaultJournalComparisonService.java:111–116 (and :118–126, :151–169)
           amplified by DeduplicationService.compareSet loop (O(n²))
           mitigations defeated at DedupEndNoteController.java:192–238

Mechanism (three parts compound):

1. ReDoS source. Journal-name strings taken from the uploaded RIS file are
   interpolated verbatim into regexes:
       compareJournals_FirstAsAbbreviation:  "\b" + name.replaceAll("\\s","\.*\\b") + ".*"
       compareJournals_FirstAsInitialism:    each char joined with ".*\b"
       compareJournals_FirstWithStartingInitialism: same char-join
   A journal string of k space-separated tokens yields k greedy ".*" groups
   separated by \b, matched with find() against an attacker-chosen target.
   Catastrophic backtracking is polynomial-to-exponential in target length;
   content routed into the journal field via T3 can be long and multi-token,
   so k is attacker-controlled and can be large.

2. O(n²) amplification. Journal comparison is step 5 of 5, reached only when a
   pair already matches on year, page/DOI, authors, and title — all fully
   attacker-controlled. A file of n mutually-matching records drives O(n²)
   journal comparisons, each triggering the pathological match.

3. Mitigations do not fire. Regex matching in java.util.regex is NOT
   interruptible. When future.get(timeoutMinutes) expires, future.cancel(true)
   only sets the interrupt flag; the thread stuck in Matcher.find() never
   observes it. Worse, the worker runs inside a try-with-resources
   ExecutorService (line 192): on the return path, executor.close() runs
   BEFORE the finally block and blocks (awaitTermination / shutdownNow cannot
   stop the regex), so concurrentRunsSemaphore.release() (line 237) is never
   reached while the runaway match continues. Each crafted upload therefore
   pins one CPU core AND permanently holds one of the 4 semaphore permits.

Risk     : ~4 small crafted uploads exhaust all concurrency permits and CPU
           cores indefinitely (not self-healing), producing a full denial of
           service. This is the highest-impact issue in the codebase and the
           only one exploitable remotely by an unauthenticated user.

Fix      : Primary — remove user input from regex construction: use
           Pattern.quote() on the interpolated name, or replace the
           abbreviation/initialism checks with literal token/startsWith logic
           (no backtracking). Defence in depth — (a) bound the number of tokens
           / length of any string used to build a pattern; (b) run the match
           under a hard wall-clock guard using an interruptible CharSequence
           wrapper (throw from charAt() once a deadline passes) so the timeout
           can actually abort a match; (c) move semaphore release so it is not
           gated on executor.close() completing.
```

---

**[LOW] Upload rate limit bypassed by client-chosen wssessionId**
```
Location : RateLimitInterceptor.java:85–95
Risk     : The limiter keys on the wssessionId request parameter, which the
           client supplies. A fresh UUID per POST /uploadFile yields a clean
           counter every time, so the cooldown never triggers; the IP fallback
           (lines 90–93) is unreachable while the parameter is present.
Fix      : Key primarily on remote IP (honouring a trusted X-Forwarded-For at
           the proxy), keeping burst≥2 to preserve the two-file flow. Optionally
           combine IP + session so a single page's two uploads still share a
           counter but an IP cannot mint unlimited fresh keys.
```

---

**[LOW→MEDIUM] Session directories accumulate on disk with no cleanup**
```
Location : DedupEndNoteController.java:263–290 (uploadFile); no reaper exists
Risk     : Each page load mints a new UUID and uploadFile creates
           {uploadDir}/{uuid}/ containing the upload plus derived output files.
           Nothing deletes them. Sustained (or malicious) use fills the upload
           partition; once full, all uploads and result writes fail. Rises
           toward Medium if combined with the HIGH finding, whose orphaned
           runs leave partial outputs behind.
Fix      : Add a @Scheduled reaper deleting session dirs older than N hours
           (e.g. 24h), plus a startup sweep for dirs orphaned across restarts.
```

---

**[LOW] HTTP security response headers absent**
```
Location : WebConfig.java (no header interceptor); no Spring Security on classpath
Risk     : No X-Content-Type-Options, X-Frame-Options / CSP frame-ancestors, or
           Referrer-Policy. Enables clickjacking via foreign iframe and MIME
           sniffing of the text result download on older browsers.
Fix      : Add a HandlerInterceptor/Filter setting at minimum
           X-Content-Type-Options: nosniff, X-Frame-Options: SAMEORIGIN,
           Referrer-Policy: strict-origin-when-cross-origin. A CSP would also
           harden the existing $.text() XSS fixes at the transport layer.
```

---

**[INFO] TLS deployment expectation undocumented**
```
Location : application.properties (plain HTTP on :9777, no TLS config)
Risk     : Uploads and downloads travel unencrypted unless a reverse proxy
           terminates TLS; only a risk if exposed beyond loopback without a proxy.
Fix      : Document the expected topology (proxy terminates TLS; app loopback-only)
           in application.properties or README.
```

---

## Verified NON-findings (checked this pass, no action needed)

- **Memory / OOM from large upload** — `checkRecordCap` runs *before*
  `readBibliographicItems` (DeduplicationService.java:221 before :222) and
  `countRecords` streams the file with O(1) memory, so a file exceeding
  `dedup.max-records` (100 000) is rejected before any `List<BibliographicItem>`
  is built. The reversed-title/allAuthors expansion is bounded by that cap.
- **File namespace collisions** — per-`UUID.randomUUID()` session subdirectory
  isolates concurrent uploads of identically-named files.
- **WebSocket eavesdropping** — the simple broker lets any client SUBSCRIBE to
  any `/topic/**`, but the per-session UUID (122-bit CSPRNG) is unguessable and
  is not exposed in any URL/Referer (hidden form field + POST param only).
- **Path traversal** — `resolveInSessionDir` normalises and re-checks
  `startsWith(sessionDir)` at every controller entry point; also rejects
  CR/LF (log-forging) and absolute/multi-segment names.
- **Injection** — no SQL, no `ProcessBuilder`, no LDAP; confirmed.
- **SSRF** — no outbound HTTP client in the codebase; confirmed.
- **Error/stack-trace exposure** — `GlobalExceptionHandler` returns generic text
  and logs server-side only; `NoResourceFoundException` handled quietly.

---

## Accepted-by-Design Risks

| Risk | Status |
|---|---|
| No authentication / CSRF | Intentional public tool; no privileged session to forge |
| `setAllowedOriginPatterns("*")` on STOMP endpoint | Intentional; documented in WebSocketConfig |
| Actuator exposure | `health,info` only (dev profile) |
| Concurrent-run cap | `Semaphore(maxConcurrentRuns=4)` — but see HIGH finding for how a stuck regex holds a permit |
| Record-count cap | `checkRecordCap(maxRecords=100 000)` before read |
| Run timeout | `future.get(timeoutMinutes=20)` — but see HIGH finding: a non-interruptible regex defeats it |

---

## Summary Table

| Severity | Count | Titles |
|---|---|---|
| Critical | 0 | — |
| High | 1 | ReDoS in journal comparison → non-self-healing DoS (timeout + semaphore mitigations defeated) |
| Medium | 0 | — |
| Low | 3 | Rate-limit bypass; session-dir accumulation (→Medium in context); missing security headers |
| Info | 1 | TLS deployment undocumented |

### Delta vs the 2026-07-06 (Sonnet) review
- **ReDoS raised Medium → High.** The earlier review treated the 20-minute
  timeout and the semaphore as bounding the impact ("self-healing"). Tracing the
  code shows neither mitigation stops a thread inside `Matcher.find()`:
  `cancel(true)` is ignored by the regex engine, and the try-with-resources
  `executor.close()` blocks the return path *before* the semaphore is released,
  so a crafted upload pins a CPU core and a concurrency permit indefinitely.
- Added a **Verified NON-findings** section (notably confirming the memory
  bound is adequately handled — the record cap precedes the in-memory load).
- Session-dir accumulation flagged as escalating toward Medium because the HIGH
  finding leaves orphaned partial outputs behind.
