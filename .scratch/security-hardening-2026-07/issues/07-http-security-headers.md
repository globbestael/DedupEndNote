# HTTP security response headers

Status: ready-for-agent

## Parent

`.scratch/security-hardening-2026-07/PRD.md`

## What to build

Set standard security response headers on every response so the app resists clickjacking
and MIME-sniffing. Spring Security is not on the classpath, so add a lightweight
interceptor/filter that applies the headers globally.

At minimum, on every response:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: SAMEORIGIN` (or CSP `frame-ancestors 'self'`)
- `Referrer-Policy: strict-origin-when-cross-origin`

## Acceptance criteria

- [ ] Every response (pages, uploads, result downloads, error responses) carries the
      three headers above.
- [ ] The result-file download still functions correctly with the headers present.
- [ ] A test asserts the headers are present on a representative endpoint.
- [ ] No dependency on Spring Security is introduced.

## Blocked by

None - can start immediately.
