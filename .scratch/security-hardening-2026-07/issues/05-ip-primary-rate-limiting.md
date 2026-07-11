# IP-primary upload rate limiting

Status: implemented (tests green; pending commit & human review)

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

- [x] The rate-limit key is derived from client IP (trusted `X-Forwarded-For` first hop,
      else remote address), not solely from a client-supplied value.
- [x] Minting a fresh `wssessionId` per request no longer resets the quota.
- [x] The two-file flow (two rapid uploads from one page load) is not blocked (burst ≥ 2).
- [x] Cooldown and burst values remain externally configurable.
- [x] Only successful uploads consume the cooldown slot (existing behaviour preserved).
- [x] Tests cover: quota enforced per IP across differing session ids; two-file burst
      allowed; failed uploads do not consume a slot.

## Blocked by

None - can start immediately.

## Comments

**Implemented.** `RateLimitInterceptor.extractKey` now keys on the client IP —
`X-Forwarded-For` first hop when present (trusted-proxy deployment), else `remoteAddr` —
and no longer reads `wssessionId`. `wssessionId` was dropped from the key entirely rather
than "combined": combining IP+session would reintroduce the bypass (fresh session ⇒ fresh
key). The two-file flow still shares one counter because both uploads come from the same
IP (burst ≥ 2). The `afterCompletion` "record only on HTTP 200" logic and the
cooldown/burst `@Value`s are unchanged. A code comment documents that XFF is trustworthy
only behind a proxy that sets it.

Verification (`RateLimitTests`, rewritten to identify clients via `X-Forwarded-For` — which
both exercises the XFF path and isolates each test from the shared localhost `remoteAddr`):
- `differentSessionIdsFromSameIp_shareQuota` — three uploads from one IP with three
  different session ids ⇒ 3rd is 429 (the bypass is closed; previously this was 200).
- `differentIps_areIsolated` — burst exhausted on one IP, a different IP still allowed.
- `failedUploadsDoNotConsumeQuota` — two empty-file 400s then a valid upload ⇒ 200.
- burst-allowed and 3rd-rejected cases preserved.
- Regression: full integration suite 30, unchanged.

Docs: `architecture.html` sequence note corrected (per-session → per-IP); CLAUDE.md
`controllers/` responsibilities updated.
