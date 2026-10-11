---
name: android-emulator
description: Boot the SmileProc emulators and drive the lock screen, notifications, location, biometrics, idle mode and reboots with adb; take screenshots and read them. Use whenever a task runs or checks the app on an emulator or a connected phone.
---
# Driving the emulator

All scripts read scripts/emu/env.sh: `SMILEPROC_SERIAL` (default: the first running emulator), `PKG=com.example.smileproc.debug`, `PIN=1111`. Never target the dogfood package.

## Emulators (created in M0)
- `sp36` (emulator-5554): API 36, AOSP x86_64/arm64 image without Google Play (proves nothing depends on Play Services). Default. API 36 is the newest image without Google APIs; the app targets API 37.
- `scripts/emu/create-avds.sh` creates both once (Pixel 7 profile) and leaves other AVDs alone.
- `sp29` (emulator-5556): API 29, for minSdk behaviour (no POST_NOTIFICATIONS, Android 10 biometric rules, no swipe-away of ongoing notifications).

## Scripts
- `scripts/emu/start.sh [sp36|sp29]` boots headless if needed, waits for boot, turns animations off, sets the lock-screen PIN once.
- `scripts/emu/install.sh` installs the debug build.
- `scripts/emu/lock.sh`, `wake.sh` (screen on, still locked), `unlock.sh` (opens the PIN pad, types the PIN; emulators only).
- `scripts/py scripts/emu/notifs.py [--expect-max 1]` prints SmileProc notifications as JSON (id, channel, ongoing, text, action titles) from `dumpsys notification --noredact`; exits 1 if more than the expected maximum.
- `scripts/py scripts/emu/tap.py "<text>" [--shade]` finds a node by text or content description with uiautomator (opens the notification shade first with --shade) and taps its centre. Works on the lock screen.
- `scripts/emu/shot.sh <name>` saves build/emu-shots/<name>.png and prints the path. Read the PNG to see the screen.
- `scripts/emu/probe.sh` prints the debug probe JSON: entry count, counts per mood, per source, located count, notification machine state, last test result. No content.
- `scripts/emu/fire.sh reminder|restore|seed-demo|clear-demo` sends debug broadcasts that run the same code paths as the real alarms.
- `scripts/emu/geo.sh <lat> <lon>` sets the emulator GPS (`adb emu geo fix <lon> <lat>`).
- `scripts/emu/idle.sh on|off` forces or leaves Doze (`dumpsys deviceidle enable` + `force-idle` / `unforce`; emulators ship with Doze disabled).
- `scripts/emu/reboot.sh` reboots and waits for boot (the phone is then locked before its first unlock).

## Gotchas
- A cold boot takes about 5 minutes (software GPU). Right after it, "System UI isn't responding" can appear: `scripts/py scripts/emu/tap.py Wait`.
- `scripts/py` stands in for python3 (Windows Store stubs). `env.sh` sets `MSYS_NO_PATHCONV=1` so Git Bash leaves device paths like /sdcard alone.
- Before the first unlock after a reboot, the app doesn't run and has no notification. That is correct.
- uiautomator dumps can be stale right after a tap: sleep 1 s before the next dump.
- Inexact alarms can be late; for logic checks use `fire.sh`, and run the real-alarm scenario (E13) only where the milestone asks.
- Screenshots are evidence: name them by scenario (e1-locked-faces.png) and list the paths in your report.
- Real phone: only if the human connected it and set SMILEPROC_SERIAL. Every adb command to it asks for approval. Don't type the human's PIN; ask them to unlock.

## No emulator
Cloud sessions have no adb, SDK or hardware acceleration. If `command -v adb` fails or `adb devices` lists nothing, don't try to install or boot anything: report each scenario as "not run (cloud)" and leave it for a local session.
