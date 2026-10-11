#!/usr/bin/env bash
# Everything Claude checks before a milestone ends (plan B.10). One PASS/FAIL line per step;
# full logs in build/check/. Exits 1 if any step fails.
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/lib/env.sh

LOGS=build/check
DOGFOOD_APK=app/build/outputs/apk/dogfood/app-dogfood.apk
mkdir -p "$LOGS"
failed=0

step() { # name command...
  local name=$1
  shift
  if "$@" >"$LOGS/$name.log" 2>&1; then
    echo "PASS $name"
  else
    echo "FAIL $name (log: $LOGS/$name.log)"
    tail -n 25 "$LOGS/$name.log" | sed 's/^/    /'
    failed=1
    return 1
  fi
}

not_run() { # name reason
  echo "FAIL $1 (not run: $2)"
  failed=1
}

json_parses() {
  scripts/py - <<'EOF'
import json, pathlib, sys
bad = 0
files = sorted(p for d in ("content", "fixtures") for p in pathlib.Path(d).rglob("*.json"))
for p in files:
    try:
        json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:
        print(f"{p}: {e}")
        bad += 1
print(f"{len(files)} JSON files, {bad} broken")
sys.exit(1 if bad or not files else 0)
EOF
}

classpath_clean() {
  local deps
  deps=$(./gradlew -q :app:dependencies --configuration dogfoodRuntimeClasspath) || return 1
  if ! grep -q 'androidx\.core:core' <<<"$deps"; then
    echo "the dependency report looks empty"
    return 1
  fi
  if grep -nE 'com\.google\.android\.gms|com\.google\.firebase|com\.squareup\.okhttp3|com\.squareup\.retrofit2|io\.ktor|androidx\.glance' <<<"$deps"; then
    return 1
  fi
  echo "dogfoodRuntimeClasspath has none of the forbidden groups"
}

permissions_clean() {
  local perms
  perms=$(apkanalyzer manifest permissions "$DOGFOOD_APK") || return 1
  echo "$perms"
  if grep -E 'android\.permission\.(INTERNET|FOREGROUND_SERVICE[A-Z_]*|ACCESS_BACKGROUND_LOCATION|SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM)\b' <<<"$perms"; then
    echo "forbidden permission in the dogfood APK"
    return 1
  fi
}

manifest_clean() {
  apkanalyzer manifest print "$DOGFOOD_APK" >"$LOGS/dogfood-manifest.xml" || return 1
  scripts/py scripts/lib/check_manifest.py "$LOGS/dogfood-manifest.xml"
}

step privacy-guard scripts/privacy-guard.sh
step fixtures scripts/py tools/generate_fixtures.py --check
step json json_parses

rm -f "$DOGFOOD_APK"
if step gradle ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDogfood; then
  step dogfood-classpath classpath_clean
  step dogfood-permissions permissions_clean
  step dogfood-manifest manifest_clean
else
  for s in dogfood-classpath dogfood-permissions dogfood-manifest; do not_run "$s" "the Gradle step failed"; done
fi

if [ "$failed" -eq 0 ]; then echo "check.sh: PASS"; else echo "check.sh: FAIL"; fi
exit "$failed"
