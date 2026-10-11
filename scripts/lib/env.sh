# Shared setup for scripts/: repo root, Android SDK, a JDK 17+, tool paths. Source it.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

is_windows() {
  case "$(uname -s)" in MINGW* | MSYS* | CYGWIN*) return 0 ;; *) return 1 ;; esac
}

unix_path() {
  if is_windows && command -v cygpath >/dev/null 2>&1; then cygpath -u "$1"; else printf '%s\n' "$1"; fi
}

# Android SDK: ANDROID_HOME, ANDROID_SDK_ROOT, local.properties sdk.dir, then the default install path.
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    ANDROID_HOME=$ANDROID_SDK_ROOT
  elif [ -f "$ROOT/local.properties" ] && grep -q '^sdk\.dir=' "$ROOT/local.properties"; then
    ANDROID_HOME=$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | sed 's/\\:/:/g; s/\\\\/\\/g' | tr -d '\r')
  elif is_windows && [ -d "${LOCALAPPDATA:-}/Android/Sdk" ]; then
    ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    ANDROID_HOME="$HOME/Library/Android/sdk"
  elif [ -d "$HOME/Android/Sdk" ]; then
    ANDROID_HOME="$HOME/Android/Sdk"
  fi
fi
if [ -n "${ANDROID_HOME:-}" ]; then
  ANDROID_HOME=$(unix_path "$ANDROID_HOME")
  export ANDROID_HOME
  export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
fi

# A JDK 17+ for Gradle and the SDK tools: keep JAVA_HOME if it is new enough, else use Android Studio's JBR.
java_major() {
  "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1
}
_java=""
[ -n "${JAVA_HOME:-}" ] && _java=$(java_major "$(unix_path "$JAVA_HOME")")
if [ -z "$_java" ] || [ "$_java" -lt 17 ]; then
  for jbr in "/c/Program Files/Android/Android Studio/jbr" \
             "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
             "/opt/android-studio/jbr" "$HOME/android-studio/jbr"; do
    if [ -n "$(java_major "$jbr")" ]; then export JAVA_HOME="$jbr"; break; fi
  done
fi
unset _java

# cmdline-tools (apkanalyzer, avdmanager) are .bat files on Windows, and cmd splits their
# arguments on ';'. Run their Java entry points directly instead.
sdk_cli() { # tool main-class args...
  local tool=$1 main=$2
  shift 2
  local bin="$ANDROID_HOME/cmdline-tools/latest/bin"
  if is_windows; then
    "$JAVA_HOME/bin/java" "-Dcom.android.sdklib.toolsdir=$(cygpath -w "$bin/..")" \
      "-Dcom.android.sdkmanager.toolsdir=$(cygpath -w "$bin/..")" \
      -classpath "$(cygpath -w "$bin/../lib/$tool-classpath.jar")" "$main" "$@"
  else
    "$bin/$tool" "$@"
  fi
}
apkanalyzer() { sdk_cli apkanalyzer com.android.tools.apk.analyzer.ApkAnalyzerCli "$@"; }
avdmanager() { sdk_cli avdmanager com.android.sdklib.tool.AvdManagerCli "$@"; }
