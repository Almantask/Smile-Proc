#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
if ! command -v swift >/dev/null 2>&1; then
  echo "swift is not installed; SmileCore tests need a Mac with Xcode."
  exit 1
fi
swift test --package-path ios/SmileCore
if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen is not installed."
  exit 1
fi
xcodegen generate --spec ios/project.yml
xcodebuild -project ios/SmileProc.xcodeproj -scheme SmileProc -destination 'generic/platform=iOS Simulator' -quiet build
