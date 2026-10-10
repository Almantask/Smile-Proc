#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
fail=0

note() { echo "privacy-guard: $*"; }
bad() { echo "privacy-guard: $*" >&2; fail=1; }

if [[ -d ios ]]; then
  if rg -n --glob '!**/Package.resolved' -e '\bURLSession\b' -e '\bURLRequest\b' -e '\bNWConnection\b' -e '\bWKWebView\b' -e '\bCLGeocoder\b' -e '\bMapKit\b' ios; then
    bad "iOS source references a forbidden networking or map symbol"
  fi
  resolved="$(find ios -name Package.resolved -print)"
  if [[ -n "$resolved" ]]; then
    python3 - "$resolved" <<'PY'
import json, sys
from pathlib import Path
text = Path(sys.argv[1]).read_text()
data = json.loads(text)
pins = data.get("pins") or data.get("object", {}).get("pins") or []
names = []
for pin in pins:
    identity = pin.get("identity") or pin.get("package") or ""
    names.append(identity.lower())
allowed = {"grdb.swift", "grdb"}
extra = [name for name in names if name and not any(ok in name for ok in allowed)]
if extra:
    raise SystemExit("Package.resolved has dependencies other than GRDB: " + ", ".join(extra))
PY
  fi
fi

if [[ -d android ]]; then
  if rg -n -g '!**/build/**' -e 'HttpURLConnection' -e 'java\.net\.URL' -e 'okhttp3' -e 'retrofit2' -e 'io\.ktor' -e 'android\.webkit\.WebView' -e 'android\.location\.Geocoder' -e 'com\.google\.android\.gms' -e 'com\.google\.firebase' android; then
    bad "Android source references a forbidden network, map, or Google SDK"
  fi
  internet_hits="$(rg -n -g '!**/build/**' 'android.permission.INTERNET' android || true)"
  real_internet="$(printf '%s\n' "$internet_hits" | grep -v 'tools:node="remove"' | grep -v '^$' || true)"
  if [[ -n "$real_internet" ]]; then
    echo "$real_internet"
    bad "INTERNET permission appears in Android sources"
  fi
  if [[ -x android/gradlew ]]; then
    (cd android && ./gradlew :app:dependencies --configuration releaseRuntimeClasspath) > /tmp/smileproc-deps.txt
    if rg -n 'com\.google\.android\.gms|com\.google\.firebase|com\.squareup\.okhttp3|com\.squareup\.retrofit2|io\.ktor' /tmp/smileproc-deps.txt; then
      bad "release runtime classpath includes a forbidden dependency"
    fi
  fi
  apk="$(find android -path '*outputs/apk/debug/*.apk' -print | head -n 1)"
  analyzer="${ANDROID_HOME:-$HOME/android-sdk}/cmdline-tools/latest/bin/apkanalyzer"
  if [[ -n "$apk" && -x "$analyzer" ]]; then
    perms="$("$analyzer" manifest permissions "$apk" || true)"
    echo "$perms"
    if echo "$perms" | rg -q 'android.permission.INTERNET'; then
      bad "debug APK requests INTERNET"
    fi
  else
    note "debug APK not built yet; skipped apkanalyzer"
  fi
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "privacy-guard: ok"
