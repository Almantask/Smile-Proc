#!/usr/bin/env bash
# Reboots and waits for boot. The device is then locked before its first unlock, when the app
# (not direct-boot aware) doesn't run and has no notification.
set -uo pipefail
. "$(dirname "$0")/env.sh"

adb reboot
sleep 5
wait_for_boot 420 || exit 1
echo "$SMILEPROC_SERIAL rebooted (locked until the first unlock)"
