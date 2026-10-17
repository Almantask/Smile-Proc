#!/usr/bin/env bash
# spikes.sh: the M1 spike app (com.example.smileproc.spikes, docs/spikes.md) on $SMILEPROC_SERIAL.
#   install                      build :spikes, install, grant location (and notifications on 13+)
#   probe                        print the probe (device state and the last 40 records, opened);
#                                the plaintext files/probe.json is deleted right after reading
#   ctl ACTION [am extras...]    POST|CANCEL|SET|SCHEDULE_REMINDER|CLEAR|PROBE (see ControlReceiver)
#                                e.g. ctl POST --es variant alerting · ctl SET --es mode fgs
#                                     ctl SCHEDULE_REMINDER --el seconds 180
#   app [locateNow|startFgs]     open the readout in the foreground, optionally running a command
# notifs.py and tap.py follow PKG: PKG=com.example.smileproc.spikes scripts/py scripts/emu/notifs.py
set -euo pipefail
export PKG=com.example.smileproc.spikes
. "$(dirname "$0")/env.sh" || exit 1
cd "$ROOT"

ctl() { # ACTION [extras...]
  local action=$1
  shift
  sh_out am broadcast --include-stopped-packages -n "$PKG/.ControlReceiver" -a "$PKG.$action" "$@" |
    grep -q 'Broadcast completed' || { echo "broadcast $action failed" >&2; return 1; }
}

case "${1:-}" in
  install)
    ./gradlew -q :spikes:assembleDebug
    adb install -r spikes/build/outputs/apk/debug/spikes-debug.apk
    adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION
    adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION
    if [ "$(sh_out getprop ro.build.version.sdk)" -ge 33 ]; then
      adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS
    fi
    echo "installed $PKG on $SMILEPROC_SERIAL"
    ;;
  probe)
    ctl PROBE
    sleep 1
    adb exec-out run-as "$PKG" cat files/probe.json
    adb shell run-as "$PKG" rm -f files/probe.json
    echo
    ;;
  ctl)
    [ $# -ge 2 ] || { echo "usage: spikes.sh ctl ACTION [extras...]" >&2; exit 2; }
    shift
    ctl "$@"
    echo "ok"
    ;;
  app)
    # CLEAR_TOP + SINGLE_TOP delivers the command to a running readout through onNewIntent.
    adb shell am start --activity-clear-top --activity-single-top -n "$PKG/.SpikeActivity" \
      ${2:+--es cmd "$2"} >/dev/null
    echo "ok"
    ;;
  *)
    sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
    ;;
esac
