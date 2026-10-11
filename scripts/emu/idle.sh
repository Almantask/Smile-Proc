#!/usr/bin/env bash
# idle.sh on|off: forces Doze (unplugged, screen off) or leaves it. Emulators ship with Doze
# disabled, so `on` enables it first and checks that the device reached deep idle.
set -euo pipefail
. "$(dirname "$0")/env.sh"

case "${1:-}" in
  on)
    adb shell dumpsys battery unplug
    adb shell input keyevent KEYCODE_SLEEP
    sh_out dumpsys deviceidle enable >/dev/null
    sh_out dumpsys deviceidle force-idle
    state=$(sh_out dumpsys deviceidle get deep)
    if [ "$state" != "IDLE" ]; then
      echo "deep idle state is $state, not IDLE" >&2
      exit 1
    fi
    ;;
  off)
    sh_out dumpsys deviceidle unforce
    adb shell dumpsys battery reset
    echo "deep idle state: $(sh_out dumpsys deviceidle get deep)"
    ;;
  *) echo "usage: idle.sh on|off" >&2; exit 2 ;;
esac
