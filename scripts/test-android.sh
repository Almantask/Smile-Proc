#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root/android"
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
