#!/usr/bin/env bash
# start.sh [sp36|sp29]: boots the AVD headless if it isn't running, waits for boot, turns
# animations off and sets the lock-screen PIN once. sp36 is emulator-5554, sp29 emulator-5556.
set -uo pipefail
. "$(dirname "$0")/env.sh" || exit 1

avd=${1:-sp36}
port=$(avd_port "$avd") || { echo "usage: start.sh [sp36|sp29]" >&2; exit 2; }
SMILEPROC_SERIAL=emulator-$port

if ! command adb devices 2>/dev/null | tr -d '\r' | grep -q "^$SMILEPROC_SERIAL[[:space:]]device"; then
  mkdir -p "$ROOT/build/emu-logs"
  log="$ROOT/build/emu-logs/$avd.log"
  args=(-avd "$avd" -port "$port" -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect)
  if is_windows; then
    # A detached process, so the emulator outlives this script. PowerShell's own stdio goes to
    # /dev/null: the emulator inherits those handles, and inheriting a caller's pipe would keep
    # that pipe open for as long as the emulator runs.
    powershell.exe -NoProfile -NonInteractive -Command \
      "Start-Process -WindowStyle Hidden -FilePath '$(cygpath -w "$ANDROID_HOME/emulator/emulator.exe")' -ArgumentList '${args[*]}' -RedirectStandardOutput '$(cygpath -w "$log")' -RedirectStandardError '$(cygpath -w "$log.err")'" \
      </dev/null >/dev/null 2>&1
  else
    nohup "$ANDROID_HOME/emulator/emulator" "${args[@]}" >"$log" 2>&1 &
  fi
  echo "booting $avd as $SMILEPROC_SERIAL (log: build/emu-logs/$avd.log)"
fi

wait_for_boot 420 || exit 1

for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$setting" 0
done
adb shell settings put secure lock_screen_lock_after_timeout 0   # locked as soon as the screen goes off
adb shell settings put system screen_off_timeout 1800000          # 30 min, so scenarios aren't cut short

if adb shell locksettings set-pin "$PIN" >/dev/null 2>&1; then
  echo "lock-screen PIN set"
elif ! sh_out locksettings verify --old "$PIN" | grep -qi "verified"; then
  echo "$SMILEPROC_SERIAL has a lock-screen PIN other than $PIN" >&2
  exit 1
fi

echo "$SMILEPROC_SERIAL ready: $avd, Android $(sh_out getprop ro.build.version.release), API $(sh_out getprop ro.build.version.sdk)"
