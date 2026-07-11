# DedupEndNote Security Review — 2026-07-07 (Opus 4.8)

Independent rerun performed with the Opus 4.8 model. This supersedes the earlier
`security-review-2026-07-07-opus.md` in this folder, which was mislabelled (not
produced by Opus). Every finding below was re-derived by reading the current
source, not carried over on trust.

**Net result:** the earlier file's finding *set* holds up, but two things needed
correction:
1. The ReDoS is **not** metacharacter injection — journal strings are normalized
   to `[a-zA-Z0-9 ]` before any pattern is built, so it is a **polynomial**
   ReDoS driven by *code-inserted* `.*\b` groups, not the classic exponential
   "verbatim interpolation" the earlier file described.
2. The HIGH severity is justified, but the true root cause is a **broken
   timeout/cancellation contract** (blocking `ExecutorService.close()` +
   non-interruptible CPU task), which is a defect independent of ReDoS — ReDoS
   is merely the cheapest way for an attacker to trigger it.

---

## Open Findings

---

**[HIGH] Broken run-timeout lets a single slow comparison pin a CPU core and hold a concurrency permit forever (triggerable by polynomial ReDoS in journal matching)**
```
Location : DedupEndNoteController.runDedup — DedupEndNoteController.java:192 (try-with-resources)
                                             :200 future.get(timeout)
                                             :204 future.cancel(true)
                                             :235–238 finally { ... semaphore.release() }
           trigger: DefaultJournalComparisonService.java:111–116, :118–126, :151–169
           amplifier: DeduplicationService.compareSet O(n²) loop (interrupt check only at :91)

Two compounding defects:

A. The cancellation/timeout contract cannot stop a CPU-bound task.
   - runDedup runs the dedup on a virtual thread inside a try-with-resources
     ExecutorService (line 192). On any return path (including the TimeoutException
     branch at 203–211) executor.close() runs BEFORE the finally block. close()
     on a virtual-thread-per-task executor calls shutdown()+awaitTermination and
     BLOCKS until the submitted task actually finishes.
   - future.cancel(true) (line 204) only sets the interrupt flag. compareSet
     checks that flag once per pivot (DeduplicationService.java:91) — i.e. BETWEEN
     comparisons, never DURING one. A comparison that itself runs for minutes
     never reaches the check.
   - Consequently, when a single comparison hangs, executor.close() never returns,
     the finally never runs, and concurrentRunsSemaphore.release() (line 237) is
     never reached. The permit is held indefinitely and the virtual thread's
     carrier platform thread is pinned (pure CPU work, no yield point).

B. Journal matching provides the cheap way to make one comparison hang.
   - compareJournals_FirstAsAbbreviation builds "\b" + name.replaceAll("\\s",
     ".*\\b") + ".*" — one greedy ".*" per whitespace-separated token — and runs
     find() against the other record's journal string. The initialism variants
     (:118, :151) build "\bc1.*\bc2.*…" (one ".*" per character).
   - Both operands are normalized to [a-zA-Z0-9 ] (JournalsNormalizationService
     NON_ASCII_PATTERN), so there is NO attacker metacharacter — but the k
     code-inserted ".*\b" groups over an attacker-chosen multi-token target give
     polynomial backtracking (quadratic-to-worse in target length, growing with
     token count k, which the attacker controls via T3/journal content).
   - This is step 5 of 5, reached only after year, page/DOI, authors, and title
     already matched — all attacker-controlled. A file of n records identical on
     those fields but with crafted journal strings drives O(n²) pathological
     matches; only ONE needs to be slow enough to wedge the run per defect A.

Risk     : An unauthenticated user can, with ~4 small crafted uploads, hold all
           4 semaphore permits and pin 4 carrier threads permanently — a
           non-self-healing denial of service. Highest-impact issue in the code
           and the only one remotely exploitable without any privilege.

Fix      : Address BOTH layers; either alone is insufficient.
           Layer B (remove the pathological match):
             - Wrap the interpolated token/char in Pattern.quote() is NOT enough
               (the ".*" groups are the problem, not the tokens). Prefer replacing
               the abbreviation/initialism checks with non-backtracking logic:
               tokenize and test ordered startsWith / subsequence containment in a
               single linear pass. If regex is kept, bound token count and total
               length of any string used to build a pattern (e.g. reject > ~12
               tokens / > ~120 chars from pattern construction).
           Layer A (make the timeout actually abortive):
             - Do not gate semaphore.release() on executor.close(). Release the
               permit in a finally that runs regardless of executor shutdown
               (e.g. acquire/release around future lifecycle without wrapping the
               executor's close in the same try), or use a shared bounded executor
               with an explicit awaitTermination timeout + shutdownNow fallback.
             - Add periodic interrupt/deadline checks INSIDE the inner comparison
               loops (per-pair, not only per-pivot) so a long step can observe
               cancellation, or run each match under a deadline-guarded
               CharSequence that throws once a wall-clock budget passes.
```

---

**[LOW→MEDIUM] Upload rate limiter is trivially bypassed by a client-chosen key**
```
Location : RateLimitInterceptor.extractKey — RateLimitInterceptor.java:85–95
Risk     : The limiter keys primarily on the wssessionId request parameter, which
           the client supplies. An automated uploader that mints a fresh UUID per
           POST /uploadFile gets a clean burst counter every time; the IP fallback
           (90–93) is unreachable while the parameter is present. This is the
           front-line control against the HIGH finding's upload flood, so its
           bypass raises the effective impact.
Fix      : Key on remote IP (honouring a trusted X-Forwarded-For terminated at the
           proxy) as the primary dimension, optionally combined with wssessionId so
           a page's two-file flow still shares one counter but a single IP cannot
           mint unlimited fresh keys. Keep burst ≥ 2 for the two-file flow.
```

---

**[LOW→MEDIUM] Per-session upload directories accumulate on disk with no reaper**
```
Location : DedupEndNoteController.uploadFile — DedupEndNoteController.java:263–290
           (creates {uploadDir}/{uuid}/); no @Scheduled cleanup anywhere in the app
Risk     : Every page load mints a new UUID; uploadFile creates a session dir
           holding the upload plus derived output files, and nothing ever deletes
           them. Sustained or malicious use fills the upload partition; once full,
           all uploads and result writes fail. Escalates in combination with the
           HIGH finding, whose wedged runs leave partial outputs behind.
Fix      : Add a @Scheduled reaper deleting session dirs older than N hours (e.g.
           24h) plus a startup sweep for dirs orphaned across restarts. Consider a
           per-session or global cap on total bytes.
```

---

**[LOW] HTTP security response headers absent**
```
Location : WebConfig.java (no header interceptor/filter; Spring Security not on classpath)
Risk     : No X-Content-Type-Options, X-Frame-Options / CSP frame-ancestors, or
           Referrer-Policy on any response. Permits clickjacking via a foreign
           iframe and MIME-sniffing of the text result download on older browsers.
Fix      : Register a HandlerInterceptor/Filter setting at minimum
           X-Content-Type-Options: nosniff, X-Frame-Options: SAMEORIGIN (or CSP
           frame-ancestors 'self'), and Referrer-Policy: strict-origin-when-cross-origin.
           A CSP would also harden the existing $.text() XSS fixes at the transport layer.
```

---

**[INFO] TLS deployment expectation undocumented**
```
Location : application.properties (plain HTTP on :9777; no TLS/proxy config or note)
Risk     : Uploads and downloads travel unencrypted unless a reverse proxy
           terminates TLS. Only a real risk if the port is exposed beyond loopback
           without such a proxy.
Fix      : Document the expected topology (proxy terminates TLS; app binds loopback)
           in application.properties or the README.
```

---

**[INFO] Parse-time field regexes have mild backtracking shape (not currently exploitable)**
```
Location : BibliographicItemReader.java:121–125 (ERRATUM_PATTERN, COMMENT_PATTERN,
           SOURCE_PATTERN) with leading/trailing .+/.*
Risk     : These run once per field (O(n) over records, NOT the O(n²) comparison
           path) against single field strings, so amplification is limited. No
           nested quantifier over overlapping input; current risk is negligible.
Fix      : None required now. If record-cap or field-length assumptions change,
           re-audit COMMENT_PATTERN for polynomial behaviour on long field values.
```

---

## Verified NON-findings (checked this pass)

- **Memory / OOM from large upload** — `countRecords` streams the file with
  `Files.lines` (O(1) memory, DeduplicationService.java:204 → Reader:135–139) and
  `checkRecordCap` runs *before* `readBibliographicItems`
  (DeduplicationService.java:221 precedes :222). Two-file mode sums both counts
  before reading (:259–264). A file above `dedup.max-records` (100 000) is rejected
  before any `List<BibliographicItem>` is built. Confirmed adequate.
- **File namespace collisions** — each session gets a `UUID.randomUUID()`
  subdirectory (`getSessionDir`), so identically-named concurrent uploads do not
  collide; output files derive from the input path inside that same dir.
- **Path traversal** — `resolveInSessionDir` rejects null/empty, CR/LF (log
  forging), absolute paths, multi-segment names, `.`/`..`, and re-verifies
  `resolved.startsWith(sessionDir)` after normalize. Applied at every controller
  entry (upload/start/result). Solid.
- **WebSocket eavesdropping** — the simple broker allows any client to SUBSCRIBE
  to any `/topic/**`, but the per-page UUID (122-bit CSPRNG) is unguessable and is
  only carried in hidden form fields / POST params, never in a URL or Referer.
- **Injection** — no SQL, no `ProcessBuilder`/command exec, no LDAP. Confirmed.
- **SSRF** — no outbound HTTP client in the codebase. Confirmed.
- **Error / stack-trace exposure** — download/start/upload handlers return generic
  bodies and log server-side only; `Content-Disposition` filename is sanitised
  (`replaceAll("[\"\\r\\n]", "_")`, controller:105).

---

## Accepted-by-Design Risks

| Risk | Status |
|---|---|
| No authentication / CSRF | Intentional public tool; no privileged session to forge |
| `setAllowedOriginPatterns("*")` on STOMP endpoint | Intentional; documented in WebSocketConfig:20 |
| Actuator exposure | `health,info` only (dev profile) |
| Concurrent-run cap | `Semaphore(maxConcurrentRuns=4)` — **but see HIGH: a wedged run never releases its permit** |
| Record-count cap | `checkRecordCap(maxRecords=100000)` before read — effective for memory |
| Run timeout | `future.get(timeoutMinutes=20)` — **but see HIGH: not abortive against a CPU-bound step** |

---

## Summary Table

| Severity | Count | Titles |
|---|---|---|
| Critical | 0 | — |
| High | 1 | Broken run-timeout → non-self-healing DoS, triggerable via polynomial ReDoS in journal matching |
| Medium | 0 | — |
| Low | 3 | Rate-limit bypass (→Medium in context); session-dir accumulation (→Medium in context); missing security headers |
| Info | 2 | TLS deployment undocumented; parse-time field regex shape |

### Delta vs the earlier (mislabelled) 2026-07-07 file
- **Same finding set, same HIGH severity — but corrected mechanism.** The earlier
  file claimed journal strings are interpolated "verbatim," implying attacker
  metacharacter injection. They are not: `normalizeJournal` reduces both operands
  to `[a-zA-Z0-9 ]` first. The backtracking comes from the k code-inserted `.*\b`
  groups → **polynomial**, not exponential, ReDoS. The fix therefore must target
  the `.*` groups (linear tokenized matching), not just `Pattern.quote()`.
- **Reframed the HIGH around the timeout defect.** The non-releasing semaphore is a
  standalone contract bug: `executor.close()` (try-with-resources) blocks before
  the `finally`, and `compareSet` checks interruption only between pivots
  (Reader/Service confirmed). ReDoS is the trigger, not the root cause — any
  single multi-minute comparison exposes it.
- **Confirmed the memory non-finding by reading the code** (`countRecords`
  streaming + cap-before-load), rather than asserting it.
```
