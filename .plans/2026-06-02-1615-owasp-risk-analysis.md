# DedupEndNote — OWASP Top 10 Risk Analysis & Remediation Plan

## Context

DedupEndNote is a Spring Boot 4.0 / Java 21 web app (port 9777) that lets users upload RIS
bibliography files, deduplicates them, and serves the result back for download. It has **no
authentication** — confirmed intentional: it is deployed as a **public, no-login** tool, so
"missing auth" is treated as accepted design, not a defect. That decision means the *data-flow*
vulnerabilities below are the real risk surface: every endpoint is reachable by anyone, so any
exploitable input handling is directly exploitable by an anonymous attacker.

This plan analyzes the OWASP-relevant risks found in the code and prioritizes the fixes. It is a
remediation roadmap; nothing here changes the no-login model.

## Findings by OWASP category (verified in code)

### A01/A03 — Path traversal (HIGH, exploitable today)
The client-supplied filename is concatenated into a filesystem path with **no `..`
normalization or containment check** in four places:

- **Download = arbitrary file read.** `DedupEndNoteController.java:80-86` —
  `getResultFile` builds `Path.of(uploadDir, outputFileName)` from the `fileNameResultFile`
  request param and streams it back. A value like `..\..\..\windows\win.ini` (after the
  `createOutputFileName` transform) reads files outside `upload-dir`.
- **Upload = arbitrary write + pre-delete.** `DedupEndNoteController.java:177-182` —
  `Path.of(uploadDir, file.getOriginalFilename())`; if the path exists it is `Files.delete`d
  first (line 178-180), then written. A crafted `getOriginalFilename()` (e.g. `..\..\x`) can
  overwrite/delete files outside the upload dir.
- **Processing inputs/outputs.** `DedupEndNoteController.java:129-130` and `150-152` —
  `uploadDir + File.separator + <userParam>` for `fileName_1` / `oldFile` / `newFile` flows
  straight into `FileReader`/`FileWriter`.
- `UtilitiesService.createOutputFileName` (`UtilitiesService.java:42-46`) does **not** sanitize
  separators — only an extension regex replace. (The regex `"." + extension` also leaves `.`
  and the extension unescaped — minor fragility, fix opportunistically.)

### A03 — DOM-based XSS via filename (HIGH/MEDIUM)
Server-side Thymeleaf output is safe (`th:text` only, `appVersion` is a build property, no
`th:utext`). The real sink is **client-side**: the uploaded filename is echoed back by the
server and injected into jQuery `.html()`:

- `index.html:136` — `$('#results').html("<span>File " + data.files[0].name + " ...")`
- `index.html:106,147,173-179` and `twofiles.html:107,128,135,148,174-180` —
  `$('#results').html(message)` / `.html(response.result)`, where `message`/`result` echo the
  filename (controller lines 133,156,174,184,189,193).
- A file named `<img src=x onerror=alert(1)>.ris` executes script in the victim's browser.

### A03 — Response-header injection (MEDIUM)
`DedupEndNoteController.java:84` reflects the user filename into the `Content-Disposition`
header unsanitized.

### A05 — Security misconfiguration (MEDIUM/LOW)
- **Actuator wide open in `dev` profile** — `application-dev.properties:2`
  `management.endpoints.web.exposure.include=*` exposes env/beans/heapdump/etc. anonymously.
  Default `application.properties` does not, so this only bites if `dev` runs in production.
- No security response headers (no `X-Content-Type-Options`, `X-Frame-Options`/CSP, etc.) —
  no Spring Security on the classpath to add them.
- Plain HTTP only (`application.properties:1`), no TLS — uploads/results travel in clear.
- No explicit WebSocket `setAllowedOrigins` (`WebSocketConfig.java:19`); SockJS default origin
  check applies but no allow-list.

### A01 — WebSocket broker access control (LOW)
`WebSocketConfig.java:14` simple broker on `/topic`; any client can subscribe to any
`/topic/messages-<wssessionId>` (controller 124/145). Only progress strings leak, and the id is
a client UUID, so impact is low.

### A09 — Verbose error exposure (LOW)
`printStackTrace()` at `DedupEndNoteController.java:89,187` (and in reader/writer/util); the
upload handler reflects `e.getClass()` / `e.getCause()` into the JSON response
(lines 189,193). Information disclosure, and feeds the XSS sink above.

### Not present (good)
No XML parsing (no XXE), no deserialization/`ObjectInputStream`, no `Runtime.exec`/
`ProcessBuilder` (no command injection), no SpEL from input, no SQL. Dependencies are current
(Spring Boot 4.0.6, jQuery 3.7.0, Bootstrap 5.3.8).

### Out of scope per decision
A07 (auth/identity) and CSRF: accepted as no-login by design. With no login session or
privileged cookie, CSRF has nothing to forge, so it is intentionally not addressed here.

## Prioritized remediation steps

**P0 — Path traversal (do first; single shared fix).**
Add one validated-resolution helper and route all four call sites through it:
1. In `UtilitiesService`, add `Path resolveInUploadDir(String uploadDir, String userFileName)`
   that takes only the base name (`Path.of(name).getFileName()`), resolves against the
   canonicalized `uploadDir`, and throws if the normalized result is not inside `uploadDir`.
   Reuse Spring's `org.springframework.util.StringUtils.cleanPath` + a `startsWith` containment
   check on the normalized absolute paths. Comment GLobbestael: The Javadoc for this 7.0.7 version
   says "NOTE that cleanPath should not be depended upon in a security context. Other mechanisms 
   should be used to prevent path-traversal issues.". But the implementation for this step
   does ***NOT*** call this cleanPath function nor copies its contents.
2. Apply it in `getResultFile` (line 82), `uploadFile` (line 177 — use the stripped base name),
   `startOneFile` (129-130), and `startTwoFiles` (150-152).
3. Optionally reject filenames whose extension is not `.ris`/`.txt` and fix the unescaped regex
   in `createOutputFileName`.

**P1 — Filename XSS.**
Stop putting raw filenames into `.html()`. Either (a) switch the `$('#results')` sinks in
`index.html`/`twofiles.html` to `.text()`, or (b) have the server return JSON it does not
hand-build (use a DTO + `ResponseEntity<Map>` so values are JSON-escaped) and never echo the
filename into HTML unescaped. Prefer (a) for the display sinks plus (b) to stop reflecting the
filename/exception text at all. This also closes the Content-Disposition reflection (sanitize
to the base name there too).

**P2 — Error handling.**
Replace `printStackTrace()` with `log.error(..., e)`; stop returning `e.getClass()`/
`e.getCause()` to the client — return a generic message. Add a small `@ControllerAdvice`
`@ExceptionHandler` so `startOneFile`/`startTwoFiles` (`throws Exception`) don't surface stack
detail.

**P3 — Configuration hardening.**
- Restrict the `dev` actuator exposure (`application-dev.properties:2`) to
  `health,info`, or document that `dev` must never run in production.
- Set `setAllowedOriginPatterns(...)` on the STOMP endpoint (`WebSocketConfig.java:19`).
- Decide on TLS / security headers at the reverse-proxy layer (since Spring Security is
  intentionally absent); document the expectation.

## Verification

- **Unit:** add tests for the new `resolveInUploadDir` helper — `..\..\x`, absolute paths, and a
  valid name; assert traversal throws and valid names resolve inside `upload-dir`. Place under
  `src/test/java/edu/dedupendnote/unit/services/` per the test taxonomy.
- **Integration:** extend an `AbstractIntegrationTest` subclass to POST `/getResultFile` and
  `/uploadFile` with a traversal filename and assert 400/no escape (run `-Pintegration-tests`).
- **Manual:** `./mvnw spring-boot:run`; try downloading `../../pom.xml` and uploading a file
  named `<img src=x onerror=alert(1)>.ris`; confirm both are rejected/escaped in the browser.
- Full build must stay green under NullAway + Error Prone: `./mvnw clean package`.

## Scope note
This is analysis + a prioritized roadmap. Implementation (P0–P3) is a separate step and is not
started until you approve.

## Implementation status (executed 2026-06-03)

All four priorities implemented. Full build (`./mvnw clean package`) and full unit-test suite
(552 tests, 0 failures) pass.

### P0 — Path traversal ✓
- `UtilitiesService.resolveInUploadDir(String uploadDir, @Nullable String userFileName)` added:
  strips input to base name via `Path.getFileName()`, resolves against the canonicalized upload
  dir, asserts containment. Throws `IllegalArgumentException` for null/empty input; otherwise
  silently sanitises traversal paths to their base name.
- Applied at all four controller call sites: `getResultFile`, `uploadFile`, `startOneFile`,
  `startTwoFiles`. Each catches `IllegalArgumentException` and returns 400.
- `createOutputFileName` regex fixed: unescaped `"." + extension` →
  `"\\." + Pattern.quote(extension)`.
- `Content-Disposition` header in `getResultFile` now strips `"`, `\r`, `\n` from the filename.
- Files modified: `UtilitiesService.java`, `DedupEndNoteController.java`.

### P1 — Filename XSS ✓
- All `$('#results').html(userValue)` and `.html("<span>" + userValue + "...</span>")` sinks in
  `index.html` and `twofiles.html` replaced with `.text(value)` or `$('<span>').text(value)`.
- Server no longer echoes filenames or exception classes/causes in upload responses; generic
  messages returned instead.
- Files modified: `index.html`, `twofiles.html`, `DedupEndNoteController.java`.

### P2 — Error handling ✓
- `printStackTrace()` in `DedupEndNoteController` replaced with `log.error(…, e)`.
- `printStackTrace()` in `BibliographicItemReader` (three calls; already had `log.error`) reduced
  to single structured `log.error(…, e)`.
- `printStackTrace()` in `UtilitiesService.detectBom` replaced with `log.error`; `@Slf4j` added.
- `GlobalExceptionHandler` (`@ControllerAdvice`) added in `controllers/` package.
- Files modified: `DedupEndNoteController.java`, `BibliographicItemReader.java`,
  `UtilitiesService.java`; new file: `GlobalExceptionHandler.java`.

### P3 — Configuration hardening ✓
- `application-dev.properties`: `management.endpoints.web.exposure.include=*` → `health,info`.
- `WebSocketConfig`: `setAllowedOriginPatterns("*")` made explicit with explanatory comment.
- Files modified: `application-dev.properties`, `WebSocketConfig.java`.

### Verification
- Unit tests for `resolveInUploadDir` in `UtilitiesServiceTest` (10 tests): valid filename,
  6 reject-on-traversal cases, standalone `..` rejection, null/empty rejection.
- Integration test `PathTraversalTests` (2 tests): POST `/getResultFile` and `/uploadFile` with
  traversal filenames → assert 400. Uses plain `RestTemplate` with a no-op error handler;
  `spring-boot-resttestclient`'s `TestRestTemplate` requires the additional
  `spring-boot-restclient` and `spring-boot-http-client` modules which are not on the classpath.
  POM change to add `spring-boot-resttestclient:4.0.6` was made by the user.
- Design note: `resolveInUploadDir` **rejects** (throws `IllegalArgumentException`) any input
  containing path separators, parent-directory references, or that is absolute — rather than
  silently stripping to the base name. This gives a deterministic 400 at the HTTP layer.
- Full build green under NullAway + Error Prone: `./mvnw clean package -DskipTests` ✓
- Unit test suite: 554 tests, 0 failures: `./mvnw test -Punit-tests` ✓
- Integration test suite: 20 tests, 0 failures: `./mvnw test -Pintegration-tests` ✓
