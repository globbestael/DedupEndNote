# DedupEndNote Security Review

**Date:** 2026-06-05 · **Branch:** design · **Reviewer:** Claude Sonnet 4.6

---

## DoS / Resource Exhaustion

**[HIGH] No upload rate limiting**
- Location: `DedupEndNoteController.java:187` — `uploadFile`
- Risk: Any anonymous client can POST 150 MB files continuously, exhausting disk space and bandwidth with no throttle.
- Fix: Add a `HandlerInterceptor` or servlet filter with per-IP request counting and cooldown (e.g. 1 upload per 10 s per IP), or configure rate limiting at the reverse proxy (nginx `limit_req`).

**[HIGH] Unbounded concurrent dedup sessions**
- Location: `DedupEndNoteController.java:132` — `startOneFile`, `startTwoFiles`
- Risk: Each call creates a new `VirtualThreadPerTaskExecutor` with no cap. An attacker can start N parallel O(n²) dedup jobs, saturating all CPU cores simultaneously.
- Fix: Use a shared bounded executor or semaphore (e.g. `Semaphore(4)`) to cap concurrent runs. Return HTTP 429 when the cap is reached.

**[MEDIUM] No per-file record-count cap**
- Location: `BibliographicItemReader.java` — `readBibliographicItems`
- Risk: A 150 MB file with many tiny records produces a huge `List<BibliographicItem>` and an O(n²) comparison loop that could run for many minutes.
- Fix: Reject inputs exceeding a configurable record count (e.g. 50 000) using the existing `countRecords()` fast pass before full parsing.

**[MEDIUM] No timeout on dedup runs**
- Location: `DedupEndNoteController.java:138` — `future.get()`
- Risk: `future.get()` blocks without a timeout. A pathological file can hold the HTTP thread indefinitely.
- Fix: `future.get(timeoutMinutes, TimeUnit.MINUTES)` with a sensible cap (e.g. 10 min); cancel the future on timeout and return an error to the client.

---

## User File Namespace Collisions

**[HIGH] Concurrent upload overwrites**
- Location: `DedupEndNoteController.java:196` — `FileChannel.open(targetPath, CREATE, WRITE, TRUNCATE_EXISTING)`
- Risk: Two users uploading a file with the same name (e.g. `export.ris`) simultaneously silently overwrite each other's content. User A's dedup then runs against User B's file.
- Fix: Prefix stored filenames with the `wssessionId` (already available), or create a per-session subdirectory under `upload-dir`.

**[HIGH] Output filename collision**
- Location: `UtilitiesService.createPath` — derives output from input filename
- Risk: Output file `export_deduplicated.txt` is in the same shared directory. Two users uploading `export.ris` overwrite each other's result file.
- Fix: Same session-scoped subdirectory fix as above resolves this simultaneously.

---

## WebSocket

**[LOW] Math.random() UUID on plain HTTP**
- Location: `index.html:65`, `twofiles.html:65` — fallback UUID generator
- Risk: `Math.random()` is not cryptographically random. On plain HTTP (where `crypto.randomUUID` is unavailable) the WebSocket session UUID is predictable. An attacker could guess it and subscribe to observe another session's progress messages. Impact is low: messages contain only percentages and record counts, no file content.
- Fix: Enforce HTTPS in deployment so `crypto.randomUUID` is always available; or generate the UUID server-side and embed it via Thymeleaf.

**[Info] Wildcard WebSocket origin** — `setAllowedOriginPatterns("*")` is documented as intentional for a public no-auth tool. Accepted.

---

## OWASP A01 — Broken Access Control ✓
`resolveInUploadDir` applied at every upload/download/start endpoint — no gaps. No privileged endpoints exist.

## OWASP A02 — Cryptographic Failures

**[Info] HTTP only**
- Risk: Uploads and results travel unencrypted. Accepted if TLS is terminated at a reverse proxy.
- Fix: Document the reverse-proxy TLS requirement in deployment docs.

## OWASP A03 — Injection ✓
No SQL, LDAP, or command injection. Regex patterns (`COMMENT_PATTERN`, `SOURCE_PATTERN`, `PHASE_PATTERN`) are compiled as static fields and used with `.matches()` — no catastrophic backtracking detected.

## OWASP A05 — Security Misconfiguration

**[MEDIUM] No HTTP security headers**
- Location: N/A — missing across all responses
- Risk: Without `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and a basic CSP the app is vulnerable to MIME sniffing and clickjacking.
- Fix: Add a `OncePerRequestFilter` or `WebMvcConfigurer` that sets these headers on every response — no Spring Security required.

**[LOW] Attacker-controlled filename in traversal warning log**
- Location: `DedupEndNoteController.java:90,143,172,220` — `log.warn("...{}", e.getMessage())`
- Risk: `e.getMessage()` includes the user-supplied filename. On Logback, no JNDI injection risk, but enables log forging if the filename contains newlines.
- Fix: Log a fixed endpoint label instead; omit user input, or sanitise with `.replaceAll("[\r\n]", "_")`.

## OWASP A06 — Vulnerable Components
No snapshot third-party dependencies. **Action:** run `./mvnw dependency:check` before each release.

## OWASP A07 — Auth / Identity (accepted) ✓
No authentication by design. CSRF non-issue without a session cookie. Accepted.

## OWASP A08 — Software & Data Integrity ✓
`InvalidRisFileException` caught in `DeduplicationService` and returned as a structured result. `GlobalExceptionHandler` catches remaining exceptions with a generic 500 body.

## OWASP A09 — Logging & Monitoring ✓
Traversal attempts logged at WARN in all four endpoints. No file content or PII written to logs. See A05 Low finding re filename sanitisation.

## OWASP A10 — SSRF ✓
No outbound HTTP triggered by user input.

---

## Summary

| Severity | # | Findings |
|---|---|---|
| **High** | 3 | Upload rate limiting, concurrent session cap, file namespace collision (upload + output) |
| **Medium** | 3 | Record-count cap, run timeout, missing HTTP security headers |
| **Low** | 2 | Math.random UUID on HTTP, attacker filename in log |
| **Info** | 2 | HTTP-only deployment, wildcard WebSocket origin |

**Accepted risks:** No authentication, CSRF, TLS (reverse-proxy responsibility), WebSocket wildcard origin.

**Top priority:** The file namespace collision (HIGH) is the most impactful practical risk today — two users with the same filename corrupt each other's runs silently. A UUID-prefixed filename or per-session subdirectory resolves all three HIGH findings at once.
