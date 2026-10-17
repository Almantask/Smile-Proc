#!/usr/bin/env bash
# geo.sh <lat> <lon>: sets the emulator's GPS fix (the console takes longitude first).
set -euo pipefail
. "$(dirname "$0")/env.sh" || exit 1

lat=${1:?usage: geo.sh <lat> <lon>}
lon=${2:?usage: geo.sh <lat> <lon>}
adb emu geo fix "$lon" "$lat"
