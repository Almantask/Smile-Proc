#!/usr/bin/env bash
# Fast guard, no Gradle: the rules of .claude/hooks/guard_edit.py applied to the files in the
# tree (edits made through Bash bypass the hook). Prints each hit as `file:line rule` and
# exits 1 on any hit. Scans core/, app/src/main/, content/, the Gradle files and the version
# catalog; skips spikes/, build/ and app/src/debug/.
set -uo pipefail
cd "$(dirname "$0")/.."

files=()
while IFS= read -r f; do files+=("$f"); done < <(
  {
    find core app/src/main content -type f -not -path '*/build/*' 2>/dev/null
    ls settings.gradle.kts build.gradle.kts app/build.gradle.kts gradle/libs.versions.toml 2>/dev/null
  } | sort -u
)

hits=0

# scan <rule> <extended regex> <file>...  (GNU grep -E; \b \s work, [\w] doesn't)
scan() {
  local rule=$1 pattern=$2
  shift 2
  [ $# -gt 0 ] || return 0
  local file line
  while IFS=: read -r file line _; do
    echo "$file:$line $rule"
    hits=$((hits + 1))
  done < <(grep -nHE -- "$pattern" "$@" 2>/dev/null)
}

source_files=() core_kt=() main_kt=() notify_kt=()
for f in "${files[@]}"; do
  case "$f" in
    *.kt | *.kts | *.xml | *.toml) source_files+=("$f") ;;
  esac
  case "$f" in
    *.kt)
      case "$f" in core/src/*) core_kt+=("$f") ;; esac
      case "$f" in app/src/main/*) [[ "$f" == */SafeLog.kt ]] || main_kt+=("$f") ;; esac
      [[ "$f" == */CheckInNotifier.kt ]] || notify_kt+=("$f")
      ;;
  esac
done

scan "not allowed: HTTP clients" 'okhttp3|retrofit2|io\.ktor' ${source_files[@]+"${source_files[@]}"}
scan "not allowed: network APIs" 'java\.net\.(URL|HttpURLConnection)\b|HttpsURLConnection' ${source_files[@]+"${source_files[@]}"}
scan "not allowed: WebView" 'android\.webkit\.WebView|<WebView' ${source_files[@]+"${source_files[@]}"}
scan "not allowed: Geocoder" 'android\.location\.Geocoder' ${source_files[@]+"${source_files[@]}"}
scan "not allowed: Play Services and Firebase" 'com\.google\.android\.gms|com\.google\.firebase|firebase-' ${source_files[@]+"${source_files[@]}"}
scan "not allowed: the INTERNET permission" 'android\.permission\.INTERNET' ${source_files[@]+"${source_files[@]}"}
scan "not allowed: widgets" 'android\.appwidget|appwidget-provider|androidx\.glance' ${source_files[@]+"${source_files[@]}"}
scan "not allowed: Quick Settings tiles" 'android\.service\.quicksettings|TileService' ${source_files[@]+"${source_files[@]}"}
scan "not allowed: app shortcuts" 'ShortcutManager|android\.app\.shortcuts' ${source_files[@]+"${source_files[@]}"}
scan "not allowed: permissions outside the spec" 'ACCESS_BACKGROUND_LOCATION|SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM' ${source_files[@]+"${source_files[@]}"}
scan "not allowed: foreground services (only if the human approves spike A2)" 'FOREGROUND_SERVICE' ${source_files[@]+"${source_files[@]}"}
scan "not allowed: activities over the lock screen" 'showWhenLocked\s*=\s*"true"|setShowWhenLocked\(\s*true' ${source_files[@]+"${source_files[@]}"}
scan ":core must stay pure Kotlin/JVM (no android.* or androidx.* imports)" '^\s*import\s+(android|androidx)\.' ${core_kt[@]+"${core_kt[@]}"}
scan "logging in main sources; use SafeLog with event names and exception types only" \
  'android\.util\.Log\b|\bLog\.[vdiwe]\(|\bprintln\(' ${main_kt[@]+"${main_kt[@]}"}
scan "only CheckInNotifier may post notifications (one notification, one ID)" \
  '\.notify\(\s*[[:alnum:]_.]+\s*,' ${notify_kt[@]+"${notify_kt[@]}"}

if [ "$hits" -gt 0 ]; then
  echo "privacy-guard: $hits hit(s). See docs/spec/product.md." >&2
  exit 1
fi
echo "privacy-guard: OK (${#files[@]} files)"
