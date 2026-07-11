# Scheduled stale session-directory reaper

Status: ready-for-agent

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

- [ ] A scheduled reaper deletes session directories whose last-modified age exceeds the
      configured threshold; younger directories are left intact.
- [ ] A startup sweep removes pre-existing stale directories orphaned across restarts.
- [ ] A directory that has been touched within the threshold (an active/recent run) is
      spared.
- [ ] The maximum age is externally configurable with a safe default (~24h).
- [ ] Age is determined via an injectable time source; tests drive it deterministically
      against a temporary directory (young survives, old removed, startup sweep works).
- [ ] CLAUDE.md updated for the new scheduled component and any new configuration key.

## Blocked by

None - can start immediately.
