# DedupEndNote Security Review — 2026-07-11 (Opus 4.8)

Full, from-scratch review (not incremental). Every finding was re-derived by
reading the current source on branch `design`, HEAD `eae41b8`. Supersedes
`.scratch/security-review-2026-07-07-opus48.md`.

Scope reviewed: controllers, services (dedup + comparison + normalization),
reader/writer, web + websocket config, rate limiter, exception handling,
properties, POM dependency versions.

## What changed since 2026-07-07 (and how it affects the findings)

- **`095deea` "harden journal-name matching against regex backtracking"** —
  the abbreviation pattern is now capped at the first 10 words
  (`MAXIMUM_NUMBER_WORDS_JOURNAL_PATTERN`) and the two initialism paths are
  bounded to 2–6 chars (`MIN/MAXIMUM_LENGTH_INITIALISM`). This **reduces but
  does not close** the polynomial ReDoS that was the trigger half of the HIGH
  finding (see H1, Layer B). The timeout/cancellation half (Layer A) is
  untouched.
- **`eae41b8` CJK-title test coverage** — comments + four unit tests only. No
  behavioural or security change. `normalizeToBasicLatin` logic is unchanged.

Net: the HIGH is **partially mitigated but remains open**; no new code path
introduced a new vulnerability. One finding not called out in the prior review
is added here (H/M: unbounded static pattern caches).

---

## Open Findings

---

**[HIGH] Run-timeout is not abortive: a single slow comparison permanently holds a concurrency permit and pins a carrier thread (non-self-healing DoS)**
```
Location : DedupEndNoteController.runDedup — DedupEndNoteController.java:192 (try-with-resources executor)
                                             :200 future.get(timeout)
                                             :204 future.cancel(true)
                                             :235-238 finally { runningFutures.remove; semaphore.release() }
           trigger  : DefaultJournalComparisonService.java:135-146 (abbreviation), :152-161, :170-199 (initialisms)
           amplifier: DeduplicationService.compareSet — interrupt check only per-pivot at :91, never inside compare()

Two compounding defects — BOTH must be fixed:

A. Cancellation/timeout cannot stop a CPU-bound task (UNCHANGED since prior reviews).
   - runDedup submits the dedup to a virtual-thread-per-task executor opened in a
     try-with-resources (:192). On every return path — including the TimeoutException
     branch (:203-211) that calls future.cancel(true) — executor.close() runs BEFORE
     the finally block. close() on this executor = shutdown() + awaitTermination(MAX),
     i.e. it BLOCKS until the submitted task actually ends.
   - future.cancel(true) only sets the interrupt flag. compareSet observes it once per
     pivot (DeduplicationService.java:91), i.e. BETWEEN comparisons, never DURING one.
     A single comparison that runs for minutes never reaches the check.
   - Therefore, if one comparison hangs, executor.close() never returns, the finally
     never runs, and concurrentRunsSemaphore.release() (:237) is never reached. The
     permit is lost for the JVM lifetime and the virtual thread's carrier is pinned
     (pure CPU, no yield point). Repeat maxConcurrentRuns (4) times → total, permanent
     denial of service, no restart-free recovery.

B. Journal matching is still the cheapest way to make ONE comparison hang
   (PARTIALLY mitigated by 095deea).
   - Both operands are normalized to [a-zA-Z0-9 ] (JournalsNormalizationService
     NON_ASCII_PATTERN), so there is no attacker metacharacter — the cost comes from
     the k code-inserted ".*\b" groups (polynomial backtracking), not metachar injection.
   - The recent commit caps the abbreviation pattern at 10 groups and initialisms at 6,
     which removes the "unbounded k" case. BUT the MATCH TARGET (the other record's
     journal string, s2) is still length-unbounded, and 10 greedy ".*" groups over a
     long crafted target that fails late still backtracks polynomially (~degree 9-10).
     Example still reachable: s1 = "a a a a a a a a a Z" (poison 10th word), s2 =
     several hundred single-char "a" tokens with no "Z" → the engine explores
     ~C(|s2|,9) placements before failing. Journal step is step 5 of 5, reached only
     after year, page/DOI, authors and title already matched — all attacker-controlled
     — so a file of near-identical records with crafted journal strings drives many
     such comparisons; only ONE needs to hang to wedge the run via defect A.

Risk     : An unauthenticated user can, with a few small crafted uploads, permanently
           consume all concurrency permits and pin all carrier threads — the highest-
           impact issue in the code and the only one exploitable with zero privilege.
           095deea raised the bar (needs a long crafted target, not just many tokens)
           but did not eliminate the trigger, and left defect A fully in place.

Fix      : Close BOTH layers.
           Layer A (make timeout genuinely abortive):
             - Do not gate semaphore.release() on executor.close(). Release the permit
               in a finally that runs regardless of executor shutdown, or use a shared
               bounded executor with awaitTermination(timeout)+shutdownNow() fallback,
               so a stuck task cannot block permit release.
             - Add a deadline/interrupt check INSIDE the inner comparison loop
               (per-pair, DeduplicationService.java:105), not only per-pivot, so a long
               step can observe cancellation.
           Layer B (remove the residual pathological match):
             - Prefer non-backtracking logic: tokenize and test ordered startsWith /
               subsequence containment in one linear pass.
             - If regex is kept, ALSO bound the target length used for these matches
               (e.g. skip abbreviation/initialism matching when either journal string
               exceeds ~120 chars / ~12 tokens); the 10-word pattern cap alone is not
               sufficient while the target is unbounded.
```

---

**[MEDIUM] Unbounded static pattern caches grow for the JVM lifetime, keyed on attacker-controlled journal strings**
```
Location : DefaultJournalComparisonService.java:30-32
             public static final Map<String,Pattern> ABBREVIATION_CACHE / INITIALISM_CACHE / STARTING_INITIALISM_CACHE
           populated at :136 (computeIfAbsent), :153 (computeIfAbsent), :183-188 (get/put)
Risk     : All three maps are process-global, never evicted or cleared (verified: no
           .clear()/eviction in main code). Each distinct normalized journal string
           ever seen adds 1-3 entries (String key + compiled Pattern), and the key is
           the FULL journal string, so even journals sharing their first 10 words
           produce distinct entries. Records are capped per run (100 000) but the
           caches persist ACROSS unlimited runs, so sustained or adversarial uploads of
           many unique journal names grow the heap without bound → eventual OutOfMemory,
           degrading all users. Fits this project's stated DoS/resource-exhaustion focus.
Fix      : Replace with a size-bounded cache (e.g. Caffeine/LinkedHashMap LRU with a
           max size), or make the caches request-scoped/local to a run so they are
           garbage-collected when the run ends. A cap of a few thousand entries is
           ample for the legitimate hot set of journal names.
```

---

**[LOW] Upload rate limiter is bypassable via a client-chosen key**
```
Location : RateLimitInterceptor.extractKey — RateLimitInterceptor.java:85-95
Risk     : The limiter keys primarily on the wssessionId request parameter, which the
           client supplies (:86-89). An automated uploader that mints a fresh UUID per
           POST /uploadFile gets a clean burst counter every time; the IP fallback
           (:90-94) is unreachable while the parameter is present. This is the front-line
           throttle against the H1 upload flood, so its bypass raises H1's effective
           reachability. (In-context severity: Medium.)
Fix      : Key on remote IP (honouring a trusted X-Forwarded-For terminated at the proxy)
           as the primary dimension, optionally combined with wssessionId so a single
           page's two-file flow shares one counter while one IP cannot mint unlimited
           fresh keys. Keep burst >= 2 for the two-file flow.
```

---

**[LOW] Per-session upload directories accumulate on disk with no reaper**
```
Location : DedupEndNoteController.uploadFile — DedupEndNoteController.java:263-290
           (creates {uploadDir}/{uuid}/ at :269-271); no @Scheduled cleanup anywhere
           (verified: no @Scheduled/@EnableScheduling in the codebase)
Risk     : Every page load mints a new UUID; uploadFile creates a session dir holding the
           upload plus derived output files, and nothing ever deletes them. Sustained or
           malicious use fills the upload partition; once full, all uploads and result
           writes fail. Compounds with H1, whose wedged runs leave partial outputs behind.
           (In-context severity: Medium.)
Fix      : Add a @Scheduled reaper deleting session dirs older than N hours (e.g. 24h),
           plus a startup sweep for dirs orphaned across restarts. Consider a per-session
           or global byte cap.
```

---

**[LOW] HTTP security response headers absent**
```
Location : WebConfig.java (no header filter/interceptor; Spring Security not on the classpath)
Risk     : No X-Content-Type-Options, X-Frame-Options / CSP frame-ancestors, or
           Referrer-Policy on any response. Permits clickjacking via a foreign iframe and
           MIME-sniffing of the text result download on older browsers.
Fix      : Register a HandlerInterceptor/Filter (or add Spring Security's header support)
           setting at minimum X-Content-Type-Options: nosniff, X-Frame-Options: SAMEORIGIN
           (or CSP frame-ancestors 'self'), and Referrer-Policy:
           strict-origin-when-cross-origin. A CSP would also harden the existing $.text()
           XSS fixes at the transport layer.
```

---

**[INFO] TLS deployment expectation undocumented**
```
Location : application.properties (plain HTTP on :9777; no TLS/proxy note)
Risk     : Uploads and downloads travel unencrypted unless a reverse proxy terminates TLS.
           Only a real risk if the port is exposed beyond loopback without such a proxy.
Fix      : Document the expected topology (proxy terminates TLS; app binds loopback) in
           application.properties or the README.
```

---

**[INFO] Parse-time field regexes have mild backtracking shape (not currently exploitable)**
```
Location : BibliographicItemReader.java:119-125 (REPLY_PATTERN, ERRATUM_PATTERN,
           SOURCE_PATTERN, COMMENT_PATTERN) with leading/trailing .+/.*
Risk     : These run once per field (O(n) over records, NOT the O(n^2) comparison path)
           against single field strings, so amplification is limited. No nested quantifier
           over overlapping input; current risk is negligible.
Fix      : None required now. If the record cap or field-length assumptions change,
           re-audit COMMENT_PATTERN for polynomial behaviour on long field values.
```

---

## Verified NON-findings (checked this pass against current source)

- **Memory / OOM from a large upload** — `checkRecordCap` (DeduplicationService.java:221)
  runs BEFORE `readBibliographicItems` (:222); `countRecords` streams the file with
  `Files.lines` (Reader:135-139, O(1) memory). Two-file mode sums both counts first
  (:259-264). A file above `dedup.max-records` (100 000) is rejected before any
  `List<BibliographicItem>` is built. Adequate. (Note: the *pattern caches* above are a
  separate, still-open memory vector.)
- **Path traversal** — `resolveInSessionDir` (UtilitiesService.java:69-92) rejects
  null/empty, CR/LF (log forging), absolute paths, multi-segment names, `.`/`..`, and
  re-verifies `resolved.startsWith(sessionDir)` after normalize. Applied at every
  controller entry (upload :269, start :147/:168, result :103). Solid.
- **File namespace collisions** — each session gets a `UUID.randomUUID()` subdirectory
  (`getSessionDir`), so identically-named concurrent uploads do not collide; outputs
  derive from the input path inside that same dir.
- **WebSocket eavesdropping** — the simple broker allows any client to SUBSCRIBE to any
  `/topic/**`, but the per-page UUID (122-bit CSPRNG) is unguessable and is carried only
  in POST params / hidden fields, never in a URL or Referer. `setAllowedOriginPatterns("*")`
  is intentional and documented (WebSocketConfig.java:20).
- **Injection** — no SQL, no `ProcessBuilder`/command exec, no LDAP. Confirmed.
- **SSRF** — no outbound HTTP client in the codebase. Confirmed.
- **Error / stack-trace exposure** — `GlobalExceptionHandler` returns generic bodies and
  logs server-side only; `NoResourceFoundException` handled quietly (no 500 for favicon
  probes); `Content-Disposition` filename sanitised (`replaceAll("[\"\\r\\n]", "_")`,
  controller:105).
- **Vulnerable components (A06)** — Spring Boot 4.1.0 / Java 21; commons-text 1.14.0
  (well past the 1.10.0 fix for CVE-2022-42889 "Text4Shell"). No snapshot deps observed.
  A full `./mvnw dependency-check:check` is still the authoritative gate before release.
- **Actuator (A05)** — default web exposure is `health` only; dev profile pins
  `health,info`. No management endpoint over-exposed.

---

## Accepted-by-Design Risks

| Risk | Status |
|---|---|
| No authentication / CSRF | Intentional public tool; no privileged session to forge |
| `setAllowedOriginPatterns("*")` on the STOMP endpoint | Intentional; documented WebSocketConfig.java:20 |
| Actuator exposure | `health,info` (dev); `health` by default otherwise |
| Concurrent-run cap | `Semaphore(maxConcurrentRuns=4)` — permit release now guaranteed on every outcome by `BoundedDedupRunner` (H1 fixed, issue 01) |
| Record-count cap | `checkRecordCap(maxRecords=100000)` before read — effective for the load-time memory bound |
| Run timeout | `future.get(timeoutMinutes=20)` — now abortive: permit released without awaiting the worker, and `compareSet` checks interruption per-pair (H1 fixed, issues 01+02) |

---

## Summary Table

**Remediation complete.** All actionable findings were fixed on branch `design`; see
`.scratch/security-hardening-2026-07/` (PRD + issues 01–07).

| Severity | Finding | Status |
|---|---|---|
| Critical | — | — |
| High | Non-abortive run-timeout → non-self-healing DoS (+ journal ReDoS trigger) | ✅ Resolved — issue 01 permit release (`697446c`), 02 per-pair cancel (`a0fe190`), 03 journal length cap (`87e12c6`) |
| Medium | Unbounded static pattern caches (per-JVM memory-exhaustion vector) | ✅ Resolved — issue 04 bounded LRU cache (`cdda431`) |
| Low | Rate-limit bypass via client-chosen key | ✅ Resolved — issue 05 IP-primary keying (`0a0a72a`) |
| Low | Session-dir accumulation with no reaper | ✅ Resolved — issue 06 opt-in `@Scheduled` reaper (`59048b1`) |
| Low | Missing HTTP security response headers | ✅ Resolved — issue 07 `SecurityHeadersFilter` (`cd102b1`) |
| Info | TLS deployment expectation undocumented | Open — documentation only |
| Info | Parse-time field regex shape | Open — accepted; re-audit only if the record-cap assumptions change |

### Remediation notes
- **H1 fully closed by 01 + 02 + 03.** Issue 01 guarantees the concurrency permit is
  released on every outcome (no permanent wedge); 02 makes a run observe cancellation within
  one comparison; 03 caps the journal-match ReDoS target at 150 chars so a single comparison
  cannot be scaled by crafted input. The three together turn any slow/hung comparison into a
  bounded, recoverable timeout that frees its slot.
- **M closed by 04** — the three unbounded static maps are now a size-bounded (5000) LRU.
- **Low items** closed by 05 (IP-primary rate-limit key), 06 (opt-in reaper, disabled by
  default; complements/replaces the cron restart), 07 (nosniff / X-Frame-Options /
  Referrer-Policy on every response).
- **Two Info items remain open by choice:** the TLS note is documentation-only, and the
  parse-time regexes run single-pass over one field (negligible amplification).
```
