#!/usr/bin/env bash
# List files in the test data's unit/ and integration/ folders whose file name is not mentioned in
# src/test or src/main. Report only: nothing is moved or deleted.
#
# Files ignored by the test data repository's .gitignore (test outputs such as *_deduplicated.txt,
# validation/, other/) are skipped. The match is on the bare file name, so a test that builds a
# file name from parts or loops over a directory is not recognized: check each hit before moving it.
#
# Usage: bin/unused-testdata.sh
# The directory is $DEDUP_TESTDATA_DIR, default ~/dedupendnote_input_files.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
data_dir="${DEDUP_TESTDATA_DIR:-$HOME/dedupendnote_input_files}"

if [ ! -d "$data_dir/.git" ]; then
  echo "$data_dir is not a git working copy of the test data repository." >&2
  exit 1
fi

unused=0
total=0
while IFS= read -r file; do
  total=$((total + 1))
  name="$(basename "$file")"
  if ! grep -rqF -- "$name" "$repo_root/src/test" "$repo_root/src/main"; then
    size="$(wc -c < "$data_dir/$file" | tr -d ' ')"
    printf '%10s  %s\n' "$size" "$file"
    unused=$((unused + 1))
  fi
done < <(git -C "$data_dir" ls-files --cached --others --exclude-standard -- unit integration)

echo "$unused of $total files in unit/ and integration/ are not referenced." >&2
