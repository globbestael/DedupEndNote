#!/usr/bin/env bash
#
# Merges a PR once the review gate in CLAUDE.md ("Workflow") holds, then brings
# the local clone back to an up-to-date main.
#
# Refuses to merge unless:
#   - the PR is open;
#   - its description or a comment has a `Reviewed: <full SHA>` line, and the
#     newest one (the description first, then the comments in order) names a
#     real commit that is the PR's head;
#   - its CI checks have all passed; it waits for checks that are still running.
# Then merges with a merge commit, deletes the branch on GitHub and locally,
# switches to main, prunes remote-tracking refs and pulls.
#
# Agents merge only through this script: .claude/settings.json denies a direct
# `gh pr merge`.
#
# Usage: bin/merge-pr.sh <PR number>

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

pr=${1:?Usage: bin/merge-pr.sh <PR number>}

refuse() {
	echo "Not merged: $*" >&2
	exit 1
}

pr_info=$(gh pr view "$pr" --json state,headRefOid,headRefName --jq '[.state, .headRefOid, .headRefName] | @tsv') ||
	refuse "could not read PR #$pr (\`gh pr view $pr\`)."
IFS=$'\t' read -r state head branch <<<"$pr_info"
[[ "$state" == OPEN ]] || refuse "PR #$pr is $state, not OPEN; there is nothing to merge."
texts=$(gh pr view "$pr" --json body,comments --jq '.body, .comments[].body') ||
	refuse "could not read the description and comments of PR #$pr."
# grep finds nothing when there is no Reviewed: line; that is checked just below.
reviewed=$(grep -oE '^Reviewed: [0-9a-f]{40}\b' <<<"$texts" | tail -1 | cut -d' ' -f2) || true

[[ -n "$reviewed" ]] || refuse "PR #$pr has no \`Reviewed: <full SHA>\` line. Run /mattpocock-skills:code-review on its head ($head) and add its findings to the PR."
git fetch -q origin
git cat-file -e "$reviewed^{commit}" 2>/dev/null || refuse "the newest Reviewed: SHA $reviewed is not a commit in this repo."
[[ "$reviewed" == "$head" ]] || refuse "the newest review saw $reviewed, but the PR's head is $head. Review again first."

if ! gh pr checks "$pr" --watch --fail-fast; then
	refuse "PR #$pr's CI checks failed or none were reported. If the PR was just opened, wait a few seconds for the workflow to start and run this again."
fi

# --match-head-commit: a push after the checks above makes GitHub refuse the
# merge, so a commit no review saw can't slip in.
gh pr merge "$pr" --merge --delete-branch --match-head-commit "$head"
git switch -q main
git fetch -q --prune origin
git pull -q --ff-only
git branch -d "$branch" 2>/dev/null || true
echo "Merged PR #$pr; on main at $(git log --oneline -1)."
