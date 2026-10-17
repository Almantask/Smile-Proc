#!/usr/bin/env bash
# Turns the screen on without unlocking: the lock screen shows.
set -uo pipefail
. "$(dirname "$0")/env.sh" || exit 1

adb shell input keyevent KEYCODE_WAKEUP
sleep 1
if device_locked; then echo "awake, locked"; else echo "awake, unlocked"; fi
