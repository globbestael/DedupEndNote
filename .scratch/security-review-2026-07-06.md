# DedupEndNote Security Review — 2026-07-06

---

## Open Findings

---

**[MEDIUM] ReDoS via user-supplied journal name in regex construction**
```
Location : DefaultJournalComparisonService.java:112–113, 119–122, 161–163
Risk     : Journal name strings from user-uploaded RIS files are interpolated
           into regex patterns with multiple .*\b chains (e.g. "a b c" →
           \ba.*\bb.*\bc.*) and matched with find(), not matches(). A crafted
           journal name with many single-letter words forces exponential
           backtracking on a mismatching target string, hanging the worker
           thread until the 20-minute timeout fires and consuming a semaphore slot.
Fix      : Escape all user content with Pattern.quote() before interpolation,
           or use a literal string split/startsWith approach instead of regex
           for the abbreviation/initialism checks.
```

The same class of issue affects all three private comparison helpers:
- `compareJournals_FirstAsAbbreviation` (spaces replaced with `.*\\b`)
- `compareJournals_FirstAsInitialism` (each character joined with `.*\\b`)
- `compareJournals_FirstWithStartingInitialism` (same joining pattern)

The `ABBREVIATION_CACHE` prevents recompilation, but the catastrophic matching cost occurs on every pair call, not only on compile.

---

**[LOW] Rate limit bypassed by client-chosen wssessionId**
```
Location : RateLimitInterceptor.java:87–88
Risk     : The upload rate limiter keys on the wssessionId query parameter
           supplied by the client. An attacker sends a fresh UUID on every
           POST /uploadFile request, so every request gets its own clean
           RateLimitEntry and the cooldown window is never triggered.
           The IP fallback path (lines 90–93) is never reached as long as
           the parameter is present, which it always is.
Fix      : Key on the remote IP (or X-Forwarded-For) instead of, or in
           addition to, wssessionId. The two-file flow concern (two rapid
           uploads per page load) can be handled by allowing burst=2 per IP
           within the cooldown window, same as the current burst logic.
```

---

**[LOW] Session directories accumulate indefinitely on disk**
```
Location : N/A — design-level; DedupEndNoteController.java:270–271
Risk     : Every page load issues a new UUID; uploadFile creates
           {uploadDir}/{uuid}/ and copies files there. No TTL, eviction
           job, or cleanup endpoint exists. Under sustained use, orphaned
           session directories fill the upload partition, eventually causing
           all writes (uploads, output files) to fail with IOException.
Fix      : Schedule a periodic cleanup task (e.g. @Scheduled) that deletes
           session directories older than N hours (e.g. 24 h). Alternatively,
           track active UUIDs in a TTL map and delete on expiry or on page
           reload (which already issues a new UUID). A startup sweep of
           directories older than the threshold also helps after restarts.
```

---

**[LOW] HTTP security headers absent**
```
Location : N/A — no Spring Security; WebConfig.java
Risk     : Responses carry no X-Content-Type-Options, X-Frame-Options, or
           Content-Security-Policy headers. Without X-Frame-Options / CSP
           frame-ancestors the page can be embedded in a foreign iframe.
           Without X-Content-Type-Options browsers may MIME-sniff the
           plain-text result file as HTML (unlikely but possible on old
           browsers).
Fix      : Add a HandlerInterceptor (or a Filter) that sets at minimum:
           X-Content-Type-Options: nosniff
           X-Frame-Options: SAMEORIGIN
           Referrer-Policy: strict-origin-when-cross-origin
           A strict CSP is optional but would harden the existing $.text()
           XSS fixes at the transport level.
```

---

**[INFO] TLS deployment expectation undocumented**
```
Location : application.properties — no TLS config
Risk     : The app serves plain HTTP on port 9777. Uploaded bibliographic
           files and result downloads travel unencrypted unless a reverse
           proxy terminates TLS. This is only a risk if deployed on a
           non-loopback network without a proxy.
Fix      : Add a comment in application.properties (or a README section)
           documenting the expected deployment topology: "reverse proxy
           handles TLS; app is loopback-only."
```

---

## Accepted-by-Design Risks (confirmed, no regression)

| Risk | Status |
|---|---|
| No authentication / CSRF | Intentional public tool; no privileged session to protect |
| `setAllowedOriginPatterns("*")` | Intentional; documented in WebSocketConfig comment |
| Path traversal | Closed — `resolveInSessionDir` validates at every controller entry point |
| DOM-based XSS | Closed — all message sinks use `.text()` / `$('<span>').text()` |
| File namespace collision | Closed — per-`UUID.randomUUID()` session subdirectories |
| WebSocket topic eavesdropping | Mitigated — UUID has 122-bit CSPRNG entropy; unguessable |
| Concurrent run abuse | Closed — `Semaphore(maxConcurrentRuns=4)` enforced |
| Record-count O(n²) explosion | Closed — `checkRecordCap(maxRecords=100 000)` before read |
| Run timeout | Closed — `Future.get(timeoutMinutes=20, MINUTES)` with partial-output cleanup |
| Error / stack-trace exposure | Closed — `GlobalExceptionHandler` returns generic message; `log.error` only |
| Actuator over-exposure | Closed — `health,info` only in dev profile |
| SQL / command injection | N/A — no database, no `ProcessBuilder` |
| SSRF | N/A — no outbound HTTP client |

---

## Summary Table

| Severity | Count | Titles |
|---|---|---|
| Critical | 0 | — |
| High | 0 | — |
| Medium | 1 | ReDoS via crafted journal name regex |
| Low | 3 | Rate limit bypass; session dir accumulation; missing security headers |
| Info | 1 | TLS deployment undocumented |

The highest-priority actionable item is the **ReDoS in `DefaultJournalComparisonService`** — it is the only finding reachable without physical access to the server and that can consume a full semaphore slot (20 minutes) from a single crafted upload. The **session directory cleanup** is a slow-burn availability risk that should be addressed before heavy production use.
