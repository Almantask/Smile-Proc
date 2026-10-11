#!/usr/bin/env bash
# shot.sh <name>: saves a screenshot to build/emu-shots/<name>.png and prints the path.
set -euo pipefail
. "$(dirname "$0")/env.sh"

name=${1:?usage: shot.sh <name>}
mkdir -p "$ROOT/build/emu-shots"
adb exec-out screencap -p >"$ROOT/build/emu-shots/$name.png"
echo "build/emu-shots/$name.png"
