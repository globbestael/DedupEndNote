# IP-primary upload rate limiting

Status: ready-for-agent

## Parent

`.scratch/security-hardening-2026-07/PRD.md`

## What to build

Change the upload rate limiter so it can no longer be bypassed by a client-chosen value.
Today it keys primarily on the client-supplied `wssessionId` request parameter, so an
automated uploader that mints a fresh identifier per request gets a clean burst counter
every time and the IP fallback is never reached.

Key primarily on the client's network identity: the first hop of a trusted
`X-Forwarded-For` header when present (proxy-terminated deployment), falling back to the
remote address. The two-file flow's allowance must be preserved — two rapid uploads from
a single page load still share one counter (burst ≥ 2). The `wssessionId` may remain as a
secondary dimension but must not be the sole key.

## Acceptance criteria

- [ ] The rate-limit key is derived from client IP (trusted `X-Forwarded-For` first hop,
      else remote address), not solely from a client-supplied value.
- [ ] Minting a fresh `wssessionId` per request no longer resets the quota.
- [ ] The two-file flow (two rapid uploads from one page load) is not blocked (burst ≥ 2).
- [ ] Cooldown and burst values remain externally configurable.
- [ ] Only successful uploads consume the cooldown slot (existing behaviour preserved).
- [ ] Tests cover: quota enforced per IP across differing session ids; two-file burst
      allowed; failed uploads do not consume a slot.

## Blocked by

None - can start immediately.
