#!/usr/bin/env bash
# Dumps the decrypted error logs JSON from the debug app.
set -euo pipefail
. "$(dirname "$0")/env.sh" || exit 1

adb shell am broadcast --include-stopped-packages \
  -n "$PKG/$DEBUG_RECEIVERS.DebugEventsReceiver" -a "com.example.smileproc.debug.EXPORT_ERROR_LOGS" >/dev/null
sleep 1
adb exec-out run-as "$PKG" cat files/error_logs_decrypted.json
echo
