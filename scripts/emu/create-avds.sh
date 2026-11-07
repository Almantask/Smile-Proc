#!/usr/bin/env bash
# Creates the three SmileProc AVDs once (plan B.8 + Android 17 addition). Leaves every other AVD alone.
#   sp36  API 36, AOSP image without Google Play (default target for Play-free verification).
#   sp29  API 29, minSdk behaviour.
#   sp37  API 37.0 (Android 17), Play-free default image matching compileSdk/targetSdk 37.
set -euo pipefail
. "$(dirname "$0")/env.sh" || exit 1

abi=x86_64
case "$(uname -m)" in arm64 | aarch64) abi=arm64-v8a ;; esac

existing=$(avdmanager list avd -c 2>/dev/null | tr -d '\r')

create() { # name api
  local name=$1 api=$2
  if grep -qx "$name" <<<"$existing"; then
    echo "$name exists"
    return 0
  fi
  local image="system-images;android-$api;default;$abi"
  if [ ! -d "$ANDROID_HOME/system-images/android-$api/default/$abi" ]; then
    echo "Missing $image. Install it first (sdkmanager or 'android sdk install system-images/android-$api/default/$abi')." >&2
    return 1
  fi
  echo no | avdmanager create avd -n "$name" -k "$image" -d pixel_7
  echo "created $name ($image)"
}

create sp36 36
create sp29 29
create sp37 37.0
