#!/usr/bin/env bash
# Builds dogfood and debug APKs and copies them to E:/GDrive/APK.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$ROOT/scripts/lib/env.sh" || exit 1

DEST_DIR="E:/GDrive/APK"
if [ ! -d "$DEST_DIR" ]; then
  DEST_DIR="/e/GDrive/APK"
fi

if [ ! -d "$DEST_DIR" ]; then
  mkdir -p "$DEST_DIR"
fi

echo "Building :app:assembleDogfood and :app:assembleDebug..."
"$ROOT/gradlew" :app:assembleDogfood :app:assembleDebug

DOGFOOD_SRC="$ROOT/app/build/outputs/apk/dogfood/app-dogfood.apk"
DEBUG_SRC="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$DOGFOOD_SRC" ]; then
  echo "Error: dogfood APK not found at $DOGFOOD_SRC" >&2
  exit 1
fi

# Extract version from app/build.gradle.kts
VERSION=$(grep -n "versionName" "$ROOT/app/build.gradle.kts" | sed -n 's/.*versionName = "\(.*\)".*/\1/p' | tr -d '\r')
VERSION=${VERSION:-"0.0.1"}

# Resolve milestone label from git tag or default
MILESTONE=$(git tag --list "m*-claude-verified" --sort=-v:refname 2>/dev/null | head -n 1 | sed 's/-claude-verified//' | tr '[:lower:]' '[:upper:]')
MILESTONE=${MILESTONE:-"M9"}

echo "Copying APKs to $DEST_DIR..."
cp -f "$DOGFOOD_SRC" "$DEST_DIR/SmileProc-dogfood.apk"
cp -f "$DOGFOOD_SRC" "$DEST_DIR/SmileProc-$MILESTONE-dogfood-$VERSION.apk"
if [ -f "$DEBUG_SRC" ]; then
  cp -f "$DEBUG_SRC" "$DEST_DIR/SmileProc-debug.apk"
fi

echo "Published APKs to $DEST_DIR:"
ls -lh "$DEST_DIR"/SmileProc-*.apk
