#!/usr/bin/env bash
# JVM tests, debug unit tests, debug APK, and debug lint.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug "$@"
