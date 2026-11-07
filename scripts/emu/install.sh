#!/usr/bin/env bash
# Builds and installs the debug build (com.example.smileproc.debug) on $SMILEPROC_SERIAL.
set -euo pipefail
. "$(dirname "$0")/env.sh" || exit 1
cd "$ROOT"

MSYS_NO_PATHCONV=0 ./gradlew -q :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
echo "installed $PKG on $SMILEPROC_SERIAL"
