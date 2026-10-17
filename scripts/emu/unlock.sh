#!/usr/bin/env bash
# Wakes the emulator, opens the PIN pad and types the PIN. Emulators only: never type the
# human's PIN on a real phone; ask them to unlock it.
set -uo pipefail
. "$(dirname "$0")/env.sh" || exit 1

case "$SMILEPROC_SERIAL" in
  emulator-*) ;;
  *) echo "unlock.sh only drives emulators; ask the human to unlock $SMILEPROC_SERIAL" >&2; exit 1 ;;
esac

adb shell input keyevent KEYCODE_WAKEUP
sleep 1
if device_locked; then
  adb shell wm dismiss-keyguard
  sleep 1
  adb shell input text "$PIN"
  adb shell input keyevent KEYCODE_ENTER
  for _ in 1 2 3 4 5; do
    sleep 1
    device_locked || break
  done
fi
if device_locked; then echo "still locked" >&2; exit 1; fi
echo "unlocked"
