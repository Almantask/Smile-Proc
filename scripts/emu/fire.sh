#!/usr/bin/env bash
# fire.sh reminder|restore|seed-demo|clear-demo|seal-test: debug broadcasts that run the same code
# paths as the real triggers (seal-test in M3; reminder/restore in M5; demo in M8).
set -euo pipefail
. "$(dirname "$0")/env.sh" || exit 1

case "${1:-}" in
  reminder) action=FIRE_REMINDER ;;
  restore) action=FIRE_RESTORE ;;
  seed-demo) action=SEED_DEMO ;;
  clear-demo) action=CLEAR_DEMO ;;
  seal-test) action=SEAL_TEST_ENTRY ;;
  export-logs) action=EXPORT_ERROR_LOGS ;;
  *) echo "usage: fire.sh reminder|restore|seed-demo|clear-demo|seal-test|export-logs" >&2; exit 2 ;;
esac

sh_out am broadcast --include-stopped-packages \
  -n "$PKG/$DEBUG_RECEIVERS.DebugEventsReceiver" -a "com.example.smileproc.debug.$action"
