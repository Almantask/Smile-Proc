# Shared setup for scripts/emu/*. Source it.
#   SMILEPROC_SERIAL  device to drive (default: the first running emulator)
#   PKG               the app under test: the debug build unless set (M1: com.example.smileproc.spikes);
#                     never the dogfood app com.example.smileproc
#   PIN               lock-screen PIN set on the emulators
. "$(dirname "${BASH_SOURCE[0]}")/../lib/env.sh"

# Git Bash rewrites arguments that look like POSIX paths (/sdcard/x -> C:/Program Files/Git/sdcard/x).
is_windows && export MSYS_NO_PATHCONV=1

PKG=${PKG:-com.example.smileproc.debug}
DOGFOOD_PKG=com.example.smileproc
PIN=1111
DEBUG_RECEIVERS=com.example.smileproc.debug   # Kotlin package of the debug-only receivers

# AVD name -> console port (serial emulator-<port>)
avd_port() {
  case "$1" in
    sp36) echo 5554 ;;
    sp29) echo 5556 ;;
    *) return 1 ;;
  esac
}

first_emulator() {
  command adb devices 2>/dev/null | tr -d '\r' | awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" { print $1; exit }'
}

if [ -z "${SMILEPROC_SERIAL:-}" ]; then
  SMILEPROC_SERIAL=$(first_emulator)
fi
export SMILEPROC_SERIAL PKG PIN

# Every adb call goes to one device, and never touches the human's daily app. The PKG check lives
# here, not at source time: scripts without `set -e` would carry on past a failed source and call
# the real adb without -s.
adb() {
  if [ "$PKG" = "$DOGFOOD_PKG" ]; then
    echo "Refusing PKG=$DOGFOOD_PKG (the human's daily app)." >&2
    return 1
  fi
  if [ -z "${SMILEPROC_SERIAL:-}" ]; then
    echo "No emulator running and SMILEPROC_SERIAL is not set. Start one: scripts/emu/start.sh sp36" >&2
    return 1
  fi
  local arg
  for arg in "$@"; do
    case "$arg" in
      "$DOGFOOD_PKG" | "$DOGFOOD_PKG/"*)
        echo "Refusing to touch $DOGFOOD_PKG (the human's daily app)." >&2
        return 1
        ;;
    esac
  done
  command adb -s "$SMILEPROC_SERIAL" "$@"
}

# Shell output without Windows line endings.
sh_out() { adb shell "$@" | tr -d '\r'; }

wait_for_boot() { # timeout seconds (default 300)
  local deadline=$((SECONDS + ${1:-300}))
  adb wait-for-device || return 1
  until [ "$(sh_out getprop sys.boot_completed)" = "1" ]; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for $SMILEPROC_SERIAL to boot" >&2
      return 1
    fi
    sleep 2
  done
}

# true when the device is locked: TrustManagerService reports deviceLocked for the current user
# (`deviceLocked=true` up to Android 15, `deviceLocked=1` on Android 16).
device_locked() {
  sh_out dumpsys trust | grep -m 1 -oE 'deviceLocked=[a-z0-9]+' | grep -qE '=(true|1)$'
}

# Last, so the guarded adb() above exists even if a caller ignores the failure.
if [ "$PKG" = "$DOGFOOD_PKG" ]; then
  echo "Refusing PKG=$DOGFOOD_PKG (the human's daily app)." >&2
  return 1
fi
