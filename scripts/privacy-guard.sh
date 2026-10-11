#!/usr/bin/env bash
# Fast guard, no Gradle: the rules of .claude/hooks/guard_edit.py applied to the files in the
# tree (edits made through Bash bypass the hook). Prints each hit as `file:line rule` and exits 1
# on any hit. Scans core/, app/src/main/, content/, the Gradle files and the version catalog;
# skips spikes/, build/ and app/src/debug/. The rules live in scripts/lib/privacy_guard.py,
# which uses the hook's own regexes on whole files, so multi-line matches count too.
set -uo pipefail
cd "$(dirname "$0")/.."
exec scripts/py scripts/lib/privacy_guard.py
