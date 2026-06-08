# Dedup run timeout

Status: done

## What to build

Apply a configurable timeout to `future.get()` in both `startOneFile` and `startTwoFiles`. When the timeout expires, cancel the future, clean up the session's working files, and return a structured error to the client. This prevents a pathological input from holding the HTTP thread indefinitely.

The timeout should be configurable via `application.properties`. A sensible default is 10 minutes.

## Acceptance criteria

- [ ] `future.get(timeout, TimeUnit.MINUTES)` replaces the unbounded `future.get()` in both endpoints
- [ ] On `TimeoutException` the future is cancelled and the client receives a clear error response
- [ ] Any partially-written output file is removed on timeout
- [ ] The timeout duration is configurable via `application.properties` (e.g. `dedup.timeout-minutes=10`)
- [ ] Normal dedup runs that complete within the timeout are unaffected
- [ ] Existing integration tests still pass

## Blocked by

None — can start immediately.
