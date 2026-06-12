# Upload rate limiting

Status: done

## What to build

Decide where upload rate limiting lives and implement the chosen approach. Two options:

**Option A — Server-side:** Add a Spring `HandlerInterceptor` with per-IP request counting and a cooldown window (e.g. 1 upload per 10 s per IP). No reverse-proxy changes needed; works in all deployment configurations.

**Option B — Reverse proxy:** Configure `limit_req` (nginx) or equivalent at the deployment boundary. No application code changes; relies on a correctly configured reverse proxy being present.

A human decision is needed because the right answer depends on the deployment topology. Once the approach is chosen, implementation is mechanical and can be handed to an agent.

## Acceptance criteria

- [ ] Approach (server-side or reverse-proxy) is decided and documented
- [ ] Chosen approach is implemented and tested
- [ ] A burst of rapid uploads from the same IP is throttled, not silently accepted
- [ ] Legitimate single uploads are not affected

## Blocked by

None — can start immediately.
