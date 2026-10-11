#!/usr/bin/env bash
# Fail the build when a non-negotiable leaks into source, the manifest, or the classpath.
set -euo pipefail
cd "$(dirname "$0")/.."

fail() {
  echo "privacy-guard: $*" >&2
  exit 1
}

forbidden='HttpURLConnection|java\.net\.URL|okhttp3|retrofit2|io\.ktor|WebView|Geocoder|com\.google\.android\.gms|com\.google\.firebase'
if grep -RInE --include='*.kt' --include='*.java' --include='*.kts' "$forbidden" app/src core/src; then
  fail "forbidden API in source"
fi

notify_hits="$(grep -RIn --include='*.kt' --include='*.java' 'notify(' app/src core/src || true)"
if [[ -n "$notify_hits" ]]; then
  while IFS= read -r hit; do
    file="${hit%%:*}"
    base="$(basename "$file")"
    if [[ "$base" != "CheckInNotifier.kt" ]]; then
      echo "$hit" >&2
      fail "notify( is only allowed in CheckInNotifier"
    fi
  done <<<"$notify_hits"
fi

./gradlew :app:assembleDebug --quiet
./gradlew :app:dependencies --configuration releaseRuntimeClasspath > /tmp/smileproc-release-classpath.txt
if grep -E -q 'com\.google\.android\.gms|com\.google\.firebase|com\.squareup\.okhttp3|com\.squareup\.retrofit2|(^|[^a-z])io\.ktor([^a-z]|$)' /tmp/smileproc-release-classpath.txt; then
  fail "banned dependency on the release runtime classpath"
fi

apk="app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk" ]] || fail "debug apk was not produced"

if [[ -z "${ANDROID_HOME:-}" && -f local.properties ]]; then
  ANDROID_HOME="$(sed -n 's/^sdk.dir=//p' local.properties)"
  export ANDROID_HOME
fi
[[ -n "${ANDROID_HOME:-}" ]] || fail "ANDROID_HOME is not set and local.properties has no sdk.dir"
apkanalyzer="${ANDROID_HOME}/cmdline-tools/latest/bin/apkanalyzer"
[[ -x "$apkanalyzer" ]] || fail "apkanalyzer not found at $apkanalyzer"

permissions="$("$apkanalyzer" manifest permissions "$apk")"
if grep -q 'android.permission.INTERNET' <<<"$permissions"; then
  echo "$permissions" >&2
  fail "debug apk requests INTERNET"
fi

manifest="$("$apkanalyzer" manifest print "$apk")"
if grep -E -q 'appwidget-provider|android\.appwidget|TileService|android\.service\.quicksettings|android\.app\.shortcuts|<shortcut' <<<"$manifest"; then
  fail "widget, Quick Settings tile, or shortcut declared in the merged manifest"
fi

echo "privacy-guard: ok"
