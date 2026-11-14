#!/usr/bin/env bash
# Builds dogfood and debug APKs, verifies in-place update compatibility, and copies to E:/GDrive/APK.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$ROOT/scripts/lib/env.sh" || exit 1

ALLOW_PACKAGE_CHANGE=0
ALLOW_SIGNING_CHANGE=0
for arg in "$@"; do
  case "$arg" in
    --allow-package-change) ALLOW_PACKAGE_CHANGE=1 ;;
    --allow-signing-change) ALLOW_SIGNING_CHANGE=1 ;;
    *)
      echo "Usage: scripts/publish-apk.sh [--allow-package-change] [--allow-signing-change]" >&2
      exit 2
      ;;
  esac
done

DEST_DIR="E:/GDrive/APK"
if [ ! -d "$DEST_DIR" ]; then
  DEST_DIR="/e/GDrive/APK"
fi

if [ ! -d "$DEST_DIR" ]; then
  mkdir -p "$DEST_DIR"
fi

APKSIGNER=$(ls -1 "$ANDROID_HOME"/build-tools/*/lib/apksigner.jar 2>/dev/null | sort -V | tail -n 1 || true)
if [ -z "$APKSIGNER" ] || [ ! -f "$APKSIGNER" ]; then
  echo "Error: apksigner.jar not found under $ANDROID_HOME/build-tools" >&2
  exit 1
fi

TMP_DIR=$(mktemp -d)
PROPS_FILE="$ROOT/app/version.properties"
PROPS_BACKUP="$TMP_DIR/version.properties.bak"
PUBLISHED=0
if [ -f "$PROPS_FILE" ]; then
  cp "$PROPS_FILE" "$PROPS_BACKUP"
fi

cleanup() {
  local status=$?
  if [ "$status" -ne 0 ] && [ "$PUBLISHED" -eq 0 ]; then
    if [ -f "$PROPS_BACKUP" ]; then
      cp "$PROPS_BACKUP" "$PROPS_FILE"
    fi
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

PREV_DOGFOOD="$DEST_DIR/SmileProc-dogfood.apk"
PREV_DEBUG="$DEST_DIR/SmileProc-debug.apk"

EXISTING_VC_ARGS=()
for existing_apk in "$PREV_DOGFOOD" "$PREV_DEBUG"; do
  if [ -f "$existing_apk" ]; then
    existing_vc=$(apkanalyzer manifest version-code "$existing_apk" | tr -d '\r')
    if [[ "$existing_vc" =~ ^[0-9]+$ ]]; then
      EXISTING_VC_ARGS+=(--existing-vc "$existing_vc")
    fi
  fi
done

GIT_COUNT=$(git -C "$ROOT" rev-list --count HEAD 2>/dev/null || echo 0)
if ! [[ "$GIT_COUNT" =~ ^[0-9]+$ ]]; then
  GIT_COUNT=0
fi

"$ROOT/scripts/py" "$ROOT/scripts/lib/apk_release_check.py" bump-version \
  --props "$PROPS_FILE" \
  --git-count "$GIT_COUNT" \
  "${EXISTING_VC_ARGS[@]}"

echo "Building :app:assembleDogfood and :app:assembleDebug..."
"$ROOT/gradlew" -p "$ROOT" :app:assembleDogfood :app:assembleDebug

DOGFOOD_SRC="$ROOT/app/build/outputs/apk/dogfood/app-dogfood.apk"
DEBUG_SRC="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$DOGFOOD_SRC" ]; then
  echo "Error: dogfood APK not found at $DOGFOOD_SRC" >&2
  exit 1
fi
if [ ! -f "$DEBUG_SRC" ]; then
  echo "Error: debug APK not found at $DEBUG_SRC" >&2
  exit 1
fi

verify_variant() {
  local variant="$1"
  local new_apk="$2"
  local prev_apk="$3"
  local expected_id="$4"

  local new_id new_vc new_vn
  new_id=$(apkanalyzer manifest application-id "$new_apk" | tr -d '\r')
  new_vc=$(apkanalyzer manifest version-code "$new_apk" | tr -d '\r')
  new_vn=$(apkanalyzer manifest version-name "$new_apk" | tr -d '\r')

  local new_certs_file="$TMP_DIR/${variant}-new-certs.txt"
  "$JAVA_HOME/bin/java" -jar "$APKSIGNER" verify --print-certs "$new_apk" >"$new_certs_file" 2>&1

  local extra_args=(--expected-id "$expected_id")
  if [ "$ALLOW_PACKAGE_CHANGE" -eq 1 ]; then
    extra_args+=(--allow-package-change)
  fi
  if [ "$ALLOW_SIGNING_CHANGE" -eq 1 ]; then
    extra_args+=(--allow-signing-change)
  fi

  if [ -f "$prev_apk" ]; then
    local prev_id prev_vc prev_certs_file
    prev_id=$(apkanalyzer manifest application-id "$prev_apk" | tr -d '\r')
    prev_vc=$(apkanalyzer manifest version-code "$prev_apk" | tr -d '\r')
    prev_certs_file="$TMP_DIR/${variant}-prev-certs.txt"
    "$JAVA_HOME/bin/java" -jar "$APKSIGNER" verify --print-certs "$prev_apk" >"$prev_certs_file" 2>&1
    extra_args+=(
      --prev-id "$prev_id"
      --prev-vc "$prev_vc"
      --prev-certs-file "$prev_certs_file"
    )
  fi

  "$ROOT/scripts/py" "$ROOT/scripts/lib/apk_release_check.py" verify-upgrade \
    --variant "$variant" \
    --new-id "$new_id" \
    --new-vc "$new_vc" \
    --new-vn "$new_vn" \
    --new-certs-file "$new_certs_file" \
    "${extra_args[@]}"
}

echo "Verifying in-place update compatibility..."
verify_variant "dogfood" "$DOGFOOD_SRC" "$PREV_DOGFOOD" "com.example.smileproc"
verify_variant "debug" "$DEBUG_SRC" "$PREV_DEBUG" "com.example.smileproc.debug"

VERSION=$(apkanalyzer manifest version-name "$DOGFOOD_SRC" | tr -d '\r')
VERSION=${VERSION:-"0.0.1"}

# Resolve milestone label from git tag or default
MILESTONE=$(git -C "$ROOT" tag --list "m*-claude-verified" --sort=-v:refname 2>/dev/null | head -n 1 | sed 's/-claude-verified//' | tr '[:lower:]' '[:upper:]')
MILESTONE=${MILESTONE:-"M9"}

echo "Copying APKs to $DEST_DIR..."
cp -f "$DOGFOOD_SRC" "$DEST_DIR/SmileProc-dogfood.apk"
cp -f "$DOGFOOD_SRC" "$DEST_DIR/SmileProc-$MILESTONE-dogfood-$VERSION.apk"
cp -f "$DEBUG_SRC" "$DEST_DIR/SmileProc-debug.apk"
PUBLISHED=1

echo "Published APKs to $DEST_DIR:"
ls -lh "$DEST_DIR"/SmileProc-*.apk
