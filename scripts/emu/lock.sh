#!/usr/bin/env bash
# Turns the screen off; with the PIN set, the emulator is locked at once.
set -uo pipefail
. "$(dirname "$0")/env.sh"

adb shell input keyevent KEYCODE_SLEEP
for _ in 1 2 3 4 5; do
  if device_locked; then echo "locked"; exit 0; fi
  sleep 1
done
echo "screen off, but the device doesn't report locked" >&2
exit 1
