# ADR-0011 A separate, lighter track for Dependabot PRs

**Status:** Decided — 2026-11-14 (issue #106)  
**Context:** Dependabot PRs have no issue, so "issue first, branch per issue, `Closes #n`" does not
apply. Running them through the two-axis `code-review` (Standards, Spec) found nothing about the bump
itself on #92. On #93 the dangerous finding (the new version made the OpenRewrite recipe delete all
261 files) came from `rewrite:dryRun`, a mechanical check, not from the review. Until #85 CI ran no
tests, so every PR also needed a local test run. A human looks at every PR before it merges; the
question is what the agent prepares for that decision.

## Decision

A PR whose author is Dependabot (`app/dependabot` in `gh`) takes a lighter track:

1. No issue, no branch per issue, no `code-review`. The PR is the spec.
2. The agent follows the `dependabot-pr` skill (`.claude/skills/dependabot-pr/SKILL.md`) and posts a
   **verification report** as one PR comment: version changes with release-note links, whether the
   Spring Boot BOM already manages the artifact, and the one check that exercises what changed.
3. The comment starts with a line `Verified: <full 40-character SHA>`.
4. `bin/merge-pr.sh` requires that line instead of `Reviewed:` when the author is Dependabot. The rest
   of the gate is unchanged: the newest marker must name the PR's head, CI must pass, and merging
   happens only when the user asks. A `Verified:` line never satisfies the gate for any other author,
   and a `Reviewed:` line does not satisfy it for Dependabot.
5. `.github/dependabot.yml` groups all minor and patch updates of the Maven ecosystem into one PR;
   majors stay separate. The `spring` and `openrewrite` groups remain. Fewer PRs means fewer
   verification reports, and a major bump needs its own release-notes read, so it must not hide in a
   group.

The marker is a separate name (`Verified:`) with the author check beside it, so the gate shows which
track a PR took. The author is the only criterion.

## Alternatives considered

- **Review Dependabot PRs like any other PR.** Rejected: the review axes have nothing to say about a
  version bump, and the real risks are found by running tools.
- **Accept `Reviewed:` or `Verified:` for every PR (one pattern).** Rejected: any agent could then
  claim the light track for a code change.
- **Select the track by a `dependencies` label.** Rejected: anyone can add a label; the author cannot
  be changed.
- **Auto-merge Dependabot PRs when CI is green.** Rejected: CI covers neither the validation tests nor
  tool behaviour such as the `rewrite:dryRun` regression of #93; a human decides on every PR.

## Consequences

- Since tests run in CI (#85), step 2 of the skill shrinks to reading the CI result for library bumps;
  build plugins still need their own command.
