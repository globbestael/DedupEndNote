---
name: security-review
description: Runs a full security review of DedupEndNote covering OWASP Top 10 plus DoS and resource-exhaustion risks. Use when the user asks for a security review, security audit, vulnerability check, or mentions OWASP. Already-fixed risks (path traversal, DOM XSS, error exposure, actuator hardening) are noted as closed so the review focuses on remaining concerns: DoS via large uploads and unbounded concurrent runs, user file namespace collisions in the shared upload directory, WebSocket topic access, missing HTTP security headers, and RIS input content validation. Outputs a prioritised finding list with severity, location, and recommended fix.
---

# DedupEndNote Security Review

## Application context (read before reviewing)

- Spring Boot 4.0 / Java 21, port 9777, fat JAR, no authentication — intentional public tool
- 150 MB upload limit; virtual threads for concurrent dedup runs; WebSocket progress reporting
- All uploads land in a single shared directory — no per-user or per-session isolation
- O(n²) pair comparison bucketed by year; large files are CPU- and memory-intensive

**Already fixed — do not re-report unless regression found:**

| Risk | Fix |
|---|---|
| Path traversal (upload, download, start endpoints) | `UtilitiesService.resolveInUploadDir` validates all user-supplied filenames |
| DOM-based XSS via filename | All `.html(userValue)` sinks replaced with `.text()` / `$('<span>').text()` |
| Error / stack-trace exposure | `GlobalExceptionHandler` returns generic messages; `log.error` only |
| Actuator over-exposure | `management.endpoints.web.exposure.include=health,info` in dev profile |

---

## Review checklist

Report each finding as: **[SEVERITY]** `file:line` — risk — fix.  
Severity scale: **Critical** / **High** / **Medium** / **Low** / **Info**.

### DoS and resource exhaustion ← primary focus for this project

- [ ] **Upload rate limiting** — any per-IP or per-session limit on the 150 MB endpoint?
- [ ] **Concurrent session cap** — can an attacker spawn unbounded parallel dedup threads?
- [ ] **Record-count cap** — is there a maximum number of RIS records per file to bound O(n²) CPU?
- [ ] **Run timeout** — can a single dedup session run indefinitely and starve other users?
- [ ] **Memory bound** — could a crafted large file cause OOM loading `List<BibliographicItem>`?
- [ ] **Output file growth** — can output files grow without bound, filling the upload disk?

### User file namespace collisions

- [ ] Two users uploading `results.ris` simultaneously — does one silently overwrite the other?
- [ ] Output filename derived from input filename — same collision risk for result files?
- [ ] Is there any session-scoped or UUID-scoped subdirectory isolation?

### WebSocket

- [ ] Can any client subscribe to `/topic/messages-<uuid>` and observe another user's progress?
- [ ] Is the UUID unguessable (client-generated `crypto.randomUUID` or equivalent)?
- [ ] `setAllowedOriginPatterns("*")` — is an explicit allow-list appropriate for the deployment?

### OWASP A01 — Broken Access Control

- [ ] All endpoints are public by design — confirm no admin or privileged operation is exposed
- [ ] Verify `resolveInUploadDir` is applied consistently at every controller entry point

### OWASP A02 — Cryptographic Failures

- [ ] Uploads and results travel over HTTP — is TLS terminated at a reverse proxy?
- [ ] Document the TLS expectation explicitly (or flag as accepted risk)

### OWASP A03 — Injection

- [ ] No SQL, LDAP, command-injection vectors (confirmed: no DB, no `ProcessBuilder`)
- [ ] RIS field content: could maliciously crafted field values cause regex catastrophic backtracking?

### OWASP A05 — Security Misconfiguration

- [ ] HTTP security headers absent (`X-Content-Type-Options`, `X-Frame-Options`, CSP) — risk level given no Spring Security?
- [ ] Error responses in all profiles: confirm no exception detail leaks

### OWASP A06 — Vulnerable Components

- [ ] Check POM dependencies for known CVEs (`./mvnw dependency:check` or equivalent)
- [ ] No snapshot or unverified dependencies

### OWASP A07 — Auth / Identity failures (accepted risk)

- [ ] No auth by design; CSRF has nothing to forge without a privileged session — document as accepted

### OWASP A08 — Software & Data Integrity

- [ ] Malformed RIS file: does `BibliographicItemReader` fail gracefully without 500 or resource leak?
- [ ] `InvalidRisFileException` path: confirm error is returned to caller cleanly

### OWASP A09 — Logging & Monitoring

- [ ] Traversal attempts and invalid filenames logged at WARN with `log.warn`?
- [ ] No uploaded file content, user filenames, or PII written to logs

### OWASP A10 — SSRF

- [ ] No outbound HTTP triggered by user input (confirmed: no HTTP client in codebase)

---

## Output format

```
[SEVERITY] Short title
  Location : file:line (or "N/A — design-level")
  Risk     : one sentence
  Fix      : concrete recommendation
```

End with a summary table: open findings by severity, and a list of risks accepted by design.

---

## Reference

`.plans/2026-06-02-1615-owasp-risk-analysis.md` — full original analysis and what each priority tier fixed.
