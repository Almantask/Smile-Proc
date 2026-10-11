#!/usr/bin/env bash
# Prints the debug probe JSON (counts only, never content): asks ProbeReceiver to write
# files/probe.json, then reads it with run-as.
set -euo pipefail
. "$(dirname "$0")/env.sh"

adb shell am broadcast --include-stopped-packages \
  -n "$PKG/$DEBUG_RECEIVERS.ProbeReceiver" -a com.example.smileproc.debug.PROBE >/dev/null
sleep 1
adb exec-out run-as "$PKG" cat files/probe.json
echo
