# ADR-0010 Controller-level integration tests preferred over browser tests

**Status:** Decided — 2026-07-04  
**Context:** The integration test folder contains two structurally different base classes that serve
different purposes. A gap in controller-level test coverage was discovered when a
`java.util.concurrent.CancellationException` (thrown by `future.get()` after `future.cancel(true)`)
went unhandled in `DedupEndNoteController.runDedup` and was only found by running the browser — not
by any automated test. This raised two questions: (1) should the two base classes be made explicit,
and (2) should browser-level tests be added to prevent this class of gap?

## Decision

### Two integration test base classes

The integration folder contains two base classes with distinct purposes:

| Base class | Web environment | HTTP | When to use |
|---|---|---|---|
| `AbstractIntegrationTest` | `MOCK` (default) | None — calls service methods directly via `@Autowired` | Service-layer contract tests; the common case |
| `AbstractRandomPortIntegrationTest` | `RANDOM_PORT` + real `RestTemplate` | Real HTTP to `http://localhost:{port}` | When the test needs real HTTP routing, controller error handling, or a property override that changes Spring Boot behaviour at startup |

`AbstractIntegrationTest` subclasses (`DeduplicationServiceTests`, `MissedDuplicatesTests`,
`RecordCountCapTests`) bypass the controller entirely; they call `deduplicateOneFile` /
`deduplicateTwoFiles` directly. These are the service-layer regression guards.

`AbstractRandomPortIntegrationTest` subclasses (`ConcurrentRunsTests`, `PathTraversalTests`,
`DeduplicationTimeoutTests`, `RateLimitTests`) make real HTTP calls and exercise the controller's
routing, parameter validation, HTTP status codes, and error branches. These are the controller
regression guards.

**Default: prefer `AbstractIntegrationTest`.** Switch to `AbstractRandomPortIntegrationTest` only
when the test specifically needs real HTTP (e.g. to assert on a 4xx status code, to test a request
interceptor, or to override a property that affects Spring Boot startup).

### Browser tests are not used; controller tests fill the gap

| What to verify | Controller test (`AbstractRandomPortIntegrationTest`) | Browser test (Playwright / Selenium) |
|---|---|---|
| HTTP routing, params, status codes | ✓ | ✓ but slow and requires a browser driver |
| Controller error branches (cancel, timeout, rate limit) | ✓ — call the endpoint programmatically | ✓ but hard to force specific server-side exceptions from the browser |
| `runDedup` exception handling (`CancellationException`, `DeduplicationException`) | ✓ | Impractical — timing-sensitive and opaque |
| `dedupFinished` JS terminal-message latch | ✗ | ✓ |
| Cancel button visibility / hide-on-complete | ✗ | ✓ |
| STOMP WebSocket message display | ✗ | ✓ with careful timing |
| File download via browser link | ✗ | ✓ |

The JavaScript in `index.html`, `twofiles.html`, and `dedup-utils.js` is thin UI glue: a cancel
click posts to one endpoint, the `dedupFinished` latch is a dozen lines. The risk of a defect in
that code is much lower than the risk of an unhandled server-side exception path. Browser tests
bring significant setup cost (browser driver, running server, timing fragility, CI configuration)
for low yield given the small amount of logic in the JS layer.

**Controller tests are the higher-value addition.** A test that uploads a file, starts
deduplication, immediately calls `POST /cancelDedup`, and asserts the response contains `"ERROR:"`
would have caught the `CancellationException` gap before opening the browser.

Browser tests are deferred, not rejected outright — see "What to watch for" below.

## Alternatives considered

### 1. Add Playwright or Selenium browser tests

Full end-to-end tests exercise the real browser, JavaScript, WebSocket, and the server in one shot.

**Rejected (for now) because:**
- The JavaScript layer is intentionally thin; the risk/cost ratio does not justify browser test
  infrastructure at this stage.
- Controller tests cover the server-side exception paths more reliably and with less setup.
- Browser tests are timing-sensitive and flaky on CI without additional tooling.
- The project currently has no CI pipeline; adding browser test infrastructure before CI exists
  would create maintenance overhead with no automated safety net.

### 2. Use `MockMvc` (Spring's mock HTTP layer) instead of `AbstractRandomPortIntegrationTest`

`MockMvc` exercises Spring MVC routing through a servlet mock without starting a real server.
Simpler to write than real HTTP tests and slightly faster.

**Rejected because:** several of the existing controller tests specifically need a real server —
`RateLimitTests` needs the `RateLimitInterceptor` wired into the real servlet container,
`ConcurrentRunsTests` needs `dedup.max-concurrent-runs=0` applied at startup, and
`DeduplicationTimeoutTests` needs `dedup.timeout-minutes=0` to trigger the real timeout path.
`AbstractRandomPortIntegrationTest` is already established for these cases; adding a parallel
`MockMvc` base class would be a second pattern for limited gain.

## What to watch for

- **JS complexity grows.** If significant logic is added to the JS layer (e.g. client-side
  validation, complex state machines, conditional UI flows), the cost/benefit of browser tests
  shifts and they should be reconsidered.
- **CI pipeline is added.** Browser tests become much more valuable — and maintainable — once
  a CI pipeline exists to run them on every commit. This is a natural trigger to revisit the
  deferral.
- **A new controller endpoint is added.** Add a corresponding test in an
  `AbstractRandomPortIntegrationTest` subclass for its HTTP contract (status code, error response
  format, auth/rate-limit behaviour). Do not rely on manual browser testing for controller error
  branches.
- **A new `runDedup` exception catch block is added.** It should have a controller test that
  actually triggers the path (see the `CancellationException` incident above).
