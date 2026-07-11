# HTTP security response headers

Status: implemented (tests green; pending commit & human review)

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

- [x] Every response (pages, uploads, result downloads, error responses) carries the
      three headers above.
- [x] The result-file download still functions correctly with the headers present.
- [x] A test asserts the headers are present on a representative endpoint.
- [x] No dependency on Spring Security is introduced.

## Blocked by

None - can start immediately.

## Comments

**Implemented.** `SecurityHeadersFilter` (`@Component`, `OncePerRequestFilter` in the
`controllers` package) sets `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`
and `Referrer-Policy: strict-origin-when-cross-origin` before `chain.doFilter`, so they are
present on every response before commit — dynamic pages, uploads, the streamed result
download, static resources and error dispatches. Spring Boot auto-registers the filter bean
for all URLs; no Spring Security.

**CSP deliberately omitted:** the issue lists CSP only as an alternative to
`X-Frame-Options`. A real Content-Security-Policy risks breaking the Thymeleaf UI (inline
scripts/styles, webjars/CDN) and needs dedicated UI testing — noted as possible future
hardening.

Verification:
- `SecurityHeadersTests` (RandomPort): a page (`GET /` → 200) and an error response
  (`GET /no-such-resource` → 404) both carry all three headers — the 404 case proves error
  responses are covered.
- Regression: full integration suite 27 (incl. the `PathTraversalTests` result-download
  happy path) — download still functions with headers present.
