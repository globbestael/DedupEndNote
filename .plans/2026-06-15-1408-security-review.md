# DedupEndNote Security Review — 2026-06-15

## Context

Full OWASP-aligned security review of the current state of the codebase.
Baseline: the previous analysis and remediation in `.plans/2026-06-02-1615-owasp-risk-analysis.md`
(P0–P3 all closed). This review focuses on what remains open after those fixes.

---

## Checklist disposition

### DoS and resource exhaustion

| Item | Status |
|---|---|
| Upload rate limiting | **PARTIAL** — `RateLimitInterceptor` applies to `/uploadFile`, but the rate key is the client-supplied `wssessionId` parameter, making it bypassable (see finding #1). |
| Concurrent session cap | **OK** — `Semaphore(maxConcurrentRuns)` (default 4) enforces a hard cap; returns 429 instantly when full. `DedupEndNoteController.java:178-181`. |
| Record-count cap | **OK** — `dedup.max-records=100000` enforced in `DeduplicationService.checkRecordCap()` before any list allocation. `DeduplicationService.java:234-242`. |
| Run timeout | **OK** — `future.get(timeoutMinutes, ...)` (default 20 min); on `TimeoutException` the future is cancelled and the output file deleted. `DedupEndNoteController.java:189-200`. |
| Memory bound | **OK** — record cap bounds the in-memory list; 150 MB upload limit bounds the file size. |
| Output file growth | **OK** — output is a filtered/enriched copy of input; bounded by 150 MB. |

### User file namespace collisions

| Item | Status |
|---|---|
| Two users uploading `results.ris` simultaneously | **OK** — each page load gets a `UUID.randomUUID()` injected into the Thymeleaf model; all uploads resolve into `<uploadDir>/<uuid>/filename`. `UtilitiesService.getSessionDir()`. |
| Output filename collision | **OK** — same UUID-scoped directory for both input and output. |
| Session-directory isolation | **OK** — per `wssessionId` UUID subdirectory created at upload time. |
| **Session directory cleanup** | **OPEN** — directories are never deleted after use (see finding #2). |

### WebSocket

| Item | Status |
|---|---|
| Cross-user topic subscription | **LOW / ACCEPTED** — UUID is `UUID.randomUUID()` (server-generated, cryptographically random, 122 bits of entropy); guessing it is infeasible in practice. |
| UUID unguessable | **OK** — `UUID.randomUUID()` in `home()` and `twofiles()`. |
| `setAllowedOriginPatterns("*")` | **DOCUMENTED** — explicit `"*"` with explanatory comment in `WebSocketConfig.java:21`. Accepted for a public no-auth tool. |

### OWASP A01 — Broken Access Control

- All endpoints are public by design — no admin or privileged endpoint found.
- `resolveInSessionDir` applied at every filename-receiving call site (`getResultFile`, `startOneFile`, `startTwoFiles`, `uploadFile`). **OK**.

### OWASP A02 — Cryptographic Failures

- **LOW / DOCUMENTED** — plain HTTP; TLS expected at reverse proxy. Expectation is not written down anywhere in code or docs (see finding #4).

### OWASP A03 — Injection

- No SQL, LDAP, `ProcessBuilder`, SpEL from input. **OK**.
- RIS content: DOI field is guarded (`doi.length() > 200` early return). **OK**.
- `BALANCED_BRACES_PATTERN` in `TitlesNormalizationService.java:19-20` uses deeply nested lookaheads — potential ReDoS on pathological nested-parenthesis titles (see finding #5).

### OWASP A05 — Security Misconfiguration

- `application-dev.properties`: `management.endpoints.web.exposure.include=health,info`. **OK** (was `*`, now fixed).
- HTTP security headers: none (`X-Content-Type-Options`, `X-Frame-Options`, CSP). No Spring Security on classpath to add them (see finding #3).
- `GlobalExceptionHandler` returns generic messages on unhandled exceptions. **OK**.

### OWASP A06 — Vulnerable Components

Not assessed in code review. Recommend running `./mvnw dependency-check:check` before next release.

### OWASP A08 — Software & Data Integrity

- `InvalidRisFileException` thrown for zero records and non-numeric ID fields; caught in `DeduplicationService`, which returns the error message and notifies via WebSocket. `DeduplicationService.java:262-264`. **OK**.
- The reader catches `IOException`, `NumberFormatException`, and the generic `Exception` fallback in the parsing loop. **OK**.

### OWASP A09 — Logging

- Path traversal attempts logged at `log.warn` with the rejected filename. **OK**.
- Uploaded file content is not logged. **OK**.
- `log.error("In field {} with content {}: ...")` in `BibliographicItemReader.java:503` logs raw RIS field content on parse error. Bibliographic records are not PII, but worth noting.

### OWASP A10 — SSRF

No outbound HTTP initiated from user input. **OK**.

---

## Open findings

```
[MEDIUM] Rate-limit bypass via wssessionId rotation
  Location : RateLimitInterceptor.java:85-94
  Risk     : The per-IP fallback is never reached when an attacker supplies a unique UUID
             per upload request; each UUID gets its own independent cooldown slot, so the
             rate limit can be bypassed without any IP rotation.
  Fix      : Use IP (or X-Forwarded-For) as the primary throttle key; use wssessionId only
             as a secondary burst token within the same IP's slot. Alternatively, validate
             wssessionId against a server-side set of recently issued UUIDs (from home() /
             twofiles()) before trusting it as a key.
```

```
[MEDIUM] Session directories are never cleaned up → slow disk exhaustion
  Location : DedupEndNoteController.java:226 (createDirectories), no cleanup path
  Risk     : Every page load creates a UUID-named subdirectory. With no deletion on
             download or expiry, an attacker (or even normal traffic) accumulates
             directories and their files indefinitely. On a shared host this fills the
             upload partition; at 150 MB per session that is ~6 sessions per GB.
  Fix      : Delete the session directory tree after result download in getResultFile
             (Files.walk + deleteIfExists, or commons-io FileUtils.deleteDirectory).
             As a backstop, add a @Scheduled task that prunes directories older than,
             e.g., 24 hours, to handle abandoned sessions where the user never
             downloaded the result.
```

```
[LOW] HTTP security headers absent
  Location : N/A — design-level; WebConfig.java has no header filter
  Risk     : Without X-Content-Type-Options, X-Frame-Options, and a Content-Security-Policy
             the browser's built-in defences against type confusion and clickjacking are not
             engaged. Low impact with no credentials or login session.
  Fix      : Add a HandlerInterceptor or OncePerRequestFilter that sets
             X-Content-Type-Options: nosniff, X-Frame-Options: SAMEORIGIN, and
             Referrer-Policy: strict-origin on every response. No Spring Security
             dependency required.
```

```
[LOW] TLS expectation not documented
  Location : application.properties (no TLS config, no comment)
  Risk     : Operators may deploy without a TLS-terminating reverse proxy, exposing
             150 MB bibliography uploads in clear text.
  Fix      : Add a comment to application.properties noting that TLS must be
             terminated upstream. Optionally add to docs/architecture.html.
```

```
[LOW] BALANCED_BRACES_PATTERN — potential ReDoS on pathological titles
  Location : TitlesNormalizationService.java:19-20
  Risk     : The nested-lookahead pattern for matching balanced parentheses can exhibit
             catastrophic backtracking for titles with many unbalanced '(' characters
             (e.g., 200 consecutive open parens). Partially mitigated by the 20-minute
             timeout and 4-concurrent-run semaphore.
  Fix      : Test with "(" * 30 + "x" as title input; if matching hangs, add a
             character-count guard (skip the pattern if '(' count exceeds, e.g., 20)
             or replace the regex with a linear balanced-paren parser.
```

---

## Summary

| Severity | Count | Findings |
|---|---|---|
| Critical | 0 | — |
| High | 0 | — |
| Medium | 2 | Rate-limit bypass, session directory accumulation |
| Low | 3 | Missing security headers, undocumented TLS expectation, ReDoS risk |

**Risks accepted by design:** no authentication (A07), CSRF (no session to forge), WebSocket
wildcard origin, actuator limited to `health,info`.

**Closed since the previous review** (`.plans/2026-06-02-1615-owasp-risk-analysis.md`):
path traversal, DOM XSS via filename, error/stack-trace exposure, actuator over-exposure in
dev profile, file namespace collisions between users.

## Implementation status

Not yet started. This document is the analysis only.
