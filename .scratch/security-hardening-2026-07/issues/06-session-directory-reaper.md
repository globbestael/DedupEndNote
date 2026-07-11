# Scheduled stale session-directory reaper

Status: implemented (tests green; pending commit & human review)

## Parent

`.scratch/security-hardening-2026-07/PRD.md`

## What to build

Reclaim abandoned per-session upload directories so they cannot fill the upload
partition. Every page load mints a new session UUID and its own upload directory holding
the upload plus derived output files, and today nothing ever deletes them.

Add a scheduled task that deletes session directories older than a configurable age
(default ~24h), plus a one-shot sweep at application startup for directories orphaned
across restarts. A directory belonging to an in-progress run must never be deleted — use
the directory's last-modified time as the age signal so the reaper stays decoupled from
the run orchestration. The age determination must be driven by an injectable time source
so it is deterministically testable.

Requires enabling scheduling in the application.

## Acceptance criteria

- [x] A scheduled reaper deletes session directories whose last-modified age exceeds the
      configured threshold; younger directories are left intact.
- [~] A startup sweep removes pre-existing stale directories orphaned across restarts.
      **Deferred (maintainer's call):** `DedupEndNoteApplication.init()` already does
      `PathUtils.deleteDirectory(uploadDir)` + recreate on every startup, so restart is a
      full wipe regardless of uploadDir location — a startup sweep would run after `init()`
      has already emptied the dir and find nothing. Worth adding only if `init()` is later
      changed to preserve uploadDir across restarts (e.g. when uploadDir moves outside the
      project). Omitted to avoid dead code.
- [x] A directory that has been touched within the threshold (an active/recent run) is
      spared.
- [x] The maximum age is externally configurable with a safe default (~24h).
- [x] Age is determined via an injectable time source; tests drive it deterministically
      against a temporary directory (young survives, old removed). (Startup-sweep test
      omitted with the sweep — see above.)
- [x] CLAUDE.md updated for the new scheduled component and any new configuration key.

## Blocked by

None - can start immediately.

## Comments

**Implemented (opt-in, disabled by default).** `SessionDirectoryReaper` (POJO in `services`)
deletes uploadDir subdirectories whose last-modified age exceeds `maxAge`
(`PathUtils.deleteDirectory`, recursive), ignores loose files, and guards a missing
uploadDir. Staleness uses the directory's last-modified time (bumped by upload/output
writes), which spares in-flight/recent runs without coupling to the run registry. An
injectable `Clock` makes the logic deterministically testable.

Toggle: `SessionReaperConfiguration` (`@Configuration @EnableScheduling
@ConditionalOnProperty(dedup.session-reaper.enabled=true)`) `@Bean`-wires and schedules the
reaper only when enabled — when off, the whole config is skipped (no bean, no scheduling),
so the cron stop/restart cleanup stays in charge. Evaluated once at startup; changeable per
jar (application.properties) or per launch (`-D` / env var), never at runtime. Default
**false**.

Config keys: `dedup.session-reaper.enabled` (false), `.max-age-hours` (24), `.interval-ms`
(3_600_000).

Verification:
- `SessionDirectoryReaperTest` (4, no Spring, fixed `Clock`): old deleted / young spared /
  recursive delete of contents / loose files ignored + boundary-young kept / missing dir
  does not throw.
- `SessionReaperWiringTests`: with the property enabled the reaper bean is present and the
  context starts with scheduling active. Disabled-by-default is covered implicitly by every
  other integration test loading without it.
- Regression: unit 608, integration 31 — unchanged.

Docs: CLAUDE.md `services/` + Configuration section. No `architecture.html` change — the
reaper is orthogonal opt-in maintenance, not a dedup-pipeline participant.
