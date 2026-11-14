---
name: dependabot-pr
description: Verify a Dependabot pull request and post a verification report as a PR comment. Use when asked to handle, check, verify or prepare a Dependabot PR (author app/dependabot) for merging. Not for PRs written by people or agents; those go through issue, branch and code-review.
---

# Verify a Dependabot PR

A Dependabot PR is its own spec: no issue, no `code-review`. You prepare a
verification report; the human decides on merging, and `bin/merge-pr.sh` is the only way to merge
(it needs the `Verified:` line below, see ADR-0011). Never commit or push to the PR branch, and
never merge unless the user asks.

## Steps

1. **Read the PR.** `gh pr view <n> --json author,headRefOid,files,title,body`. If the author is not
   `app/dependabot`, stop: this skill does not apply.
2. **Check out the head**, so the checks run against the PR's content. Start from a clean working tree
   (`git status`; if it is dirty, stop and ask the user), then `gh pr checkout <n>`.
3. **List the version changes** (`gh pr diff <n>`): artifact, old → new version, and for each a link
   to its release notes (usually in the PR body). Read them for breaking changes, removed APIs and
   changed defaults.
4. **BOM check** (Maven only): is the artifact already managed by the Spring Boot BOM
   (`./mvnw help:effective-pom` or `./mvnw dependency:tree`)? If so, an explicit `<version>` in
   `pom.xml` is redundant; say so.
5. **Run the one check that exercises what changed:**
   - library or Spring bump: `./mvnw test -Punit-tests` and `./mvnw test -Pintegration-tests`.
     If CI already ran them on the head commit (`gh pr checks <n>`), read the result instead.
   - compile-time plugin (Error Prone, NullAway, SpotBugs): `./mvnw verify -DskipTests`.
   - build plugin with its own command: run it and compare with `main`. For `rewrite-maven-plugin`
     run `./mvnw rewrite:dryRun` on both and compare the proposed changes (a mismatched
     plugin/recipe pair once proposed deleting every file; see ADR-0011).
   - GitHub Action: read the action's release notes for changed inputs, runner or Node versions.
6. **Post one PR comment** (`gh pr comment <n> --body-file ...`), in this shape. The `Verified:` line
   must be at the start of a line, with the full 40-character head SHA
   (`gh pr view <n> --json headRefOid --jq .headRefOid`):

   ```
   Verified: <full 40-character SHA>

   ## Version changes
   - <artifact> <old> → <new> ([release notes](url)): <breaking changes? none found / list>

   ## BOM
   <managed by Spring Boot BOM or not; redundant <version>?>

   ## Check
   <command run, result; for plugins the comparison with main>

   ## Verdict
   <safe to merge / needs a human look because ...>
   ```

   If a check failed or the verdict is "do not merge", still post the report, but say so plainly.
7. Return to `main` (`git switch main`) and tell the user the verdict and the comment link.

If the PR head changes afterwards (Dependabot rebase), the old `Verified:` line no longer matches
the head: run this skill again.
