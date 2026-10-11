#!/usr/bin/env bash
set -uo pipefail
cd "$CLAUDE_PROJECT_DIR" || exit 0
[ -x scripts/privacy-guard.sh ] || exit 0

if ! out=$(scripts/privacy-guard.sh 2>&1); then
  echo "privacy-guard failed. Fix this before stopping:" >&2
  echo "$out" | tail -n 30 >&2
  exit 2
fi

if git status --porcelain -- core/ content/ fixtures/ 2>/dev/null | grep -q .; then
  if ! out=$(./gradlew -q :core:test 2>&1); then
    echo ":core tests fail. Fix them before stopping (don't weaken tests or goldens):" >&2
    echo "$out" | tail -n 40 >&2
    exit 2
  fi
fi
exit 0
