#!/usr/bin/env bash
# Bring the local test data repository up to the commit pinned in testdata.ref.
#
# Usage: bin/sync-testdata.sh
# The directory is $DEDUP_TESTDATA_DIR, default ~/dedupendnote_input_files.
# It only fast-forwards: a local copy that is already at or ahead of the pin is left alone.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
data_dir="${DEDUP_TESTDATA_DIR:-$HOME/dedupendnote_input_files}"
pin="$(tr -d '[:space:]' < "$repo_root/testdata.ref")"

if [ ! -d "$data_dir/.git" ]; then
  echo "$data_dir is not a git working copy of the test data repository." >&2
  echo "Clone https://github.com/globbestael/dedupendnote-testdata there first." >&2
  exit 1
fi

git -C "$data_dir" fetch --quiet origin

if git -C "$data_dir" merge-base --is-ancestor "$pin" HEAD; then
  head="$(git -C "$data_dir" rev-parse HEAD)"
  if [ "$head" = "$pin" ]; then
    echo "Test data is at the pinned commit $pin."
  else
    echo "Test data is ahead of the pin (HEAD $head, pin $pin): nothing to do."
  fi
  exit 0
fi

if git -C "$data_dir" merge --ff-only "$pin"; then
  echo "Test data fast-forwarded to the pinned commit $pin."
else
  echo "Cannot fast-forward $data_dir to $pin (local changes or a diverged branch)." >&2
  exit 1
fi
