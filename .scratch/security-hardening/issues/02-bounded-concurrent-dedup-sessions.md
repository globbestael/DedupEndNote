# Bounded concurrent dedup sessions

Status: done

## What to build

Cap the number of simultaneous dedup runs using a shared `Semaphore` (configurable, defaulting to 4). When the cap is reached, the controller returns HTTP 429 with a user-readable message instead of spawning another O(n²) job. This prevents an attacker (or burst of legitimate users) from saturating all CPU cores with unbounded parallel jobs.

The semaphore is shared across both `startOneFile` and `startTwoFiles` endpoints. The limit should be configurable via `application.properties` so it can be tuned per deployment without a code change.

## Acceptance criteria

- [ ] A shared `Semaphore` (or equivalent) caps concurrent dedup runs at a configurable limit
- [ ] Both `startOneFile` and `startTwoFiles` acquire the semaphore before launching a dedup job
- [ ] When the cap is reached, the endpoint returns HTTP 429 and the semaphore is not held
- [ ] The semaphore is released when the dedup job completes (normally or exceptionally)
- [ ] The cap is configurable via `application.properties` (e.g. `dedup.max-concurrent-runs=4`)
- [ ] Existing integration tests still pass

## Blocked by

None — can start immediately.
