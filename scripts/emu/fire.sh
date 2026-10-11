#!/usr/bin/env bash
# fire.sh reminder|restore|seed-demo|clear-demo: debug broadcasts that run the same code paths
# as the real triggers (wired up in M5 and M8).
set -euo pipefail
. "$(dirname "$0")/env.sh"

case "${1:-}" in
  reminder) action=FIRE_REMINDER ;;
  restore) action=FIRE_RESTORE ;;
  seed-demo) action=SEED_DEMO ;;
  clear-demo) action=CLEAR_DEMO ;;
  *) echo "usage: fire.sh reminder|restore|seed-demo|clear-demo" >&2; exit 2 ;;
esac

sh_out am broadcast --include-stopped-packages \
  -n "$PKG/$DEBUG_RECEIVERS.DebugEventsReceiver" -a "com.example.smileproc.debug.$action"
