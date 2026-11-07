---
name: android-emulator
description: Boot the SmileProc Android emulators and drive the lock screen, notifications, location, biometrics, Doze idle mode, and reboots with adb; capture and inspect screenshots. Use when running, testing, or verifying SmileProc on an emulator or connected phone.
---

# Driving the SmileProc Emulator

All emulator scripts source `scripts/emu/env.sh`, which sets `SMILEPROC_SERIAL` (default: first running emulator), `PKG=com.example.smileproc.debug`, `PIN=1111`, and `MSYS_NO_PATHCONV=1`. Every script hard-refuses `com.example.smileproc` (the human's daily app) — never target it or call raw `adb` without `-s`.

## Quick start (Antigravity shell conventions)

On **Windows**, `run_command` executes in PowerShell where bare `bash` points to WSL (`System32\bash.exe`), which lacks the Windows Android SDK and AVDs. Always run `scripts/*` through **Git Bash** with single-quoted strings so PowerShell preserves `$` variables:

```powershell
& "$env:ProgramFiles\Git\bin\bash.exe" -c 'scripts/emu/start.sh sp36 && scripts/emu/install.sh'
& "$env:ProgramFiles\Git\bin\bash.exe" -c 'scripts/py scripts/emu/notifs.py --expect-max 1'
& "$env:ProgramFiles\Git\bin\bash.exe" -c 'scripts/emu/shot.sh e1-locked-faces'
```

On **Linux / macOS**, run via `bash -c '<command>'`. For slow commands (`start.sh`, `reboot.sh`), pass `WaitMsBeforeAsync: 10000` to `run_command` and wait for the background completion wakeup instead of polling.

## Emulators

- `sp36` (`emulator-5554`): API 36, AOSP x86_64/arm64 image without Google Play (proves zero Play Services dependency). Default AVD.
- `sp29` (`emulator-5556`): API 29, for `minSdk` behaviour (no `POST_NOTIFICATIONS`, Android 10 biometric rules, no swipe-away of ongoing notifications).
- `sp37` (`emulator-5558`): API 37.0 (Android 17), Play-free AOSP `default` image matching `compileSdk`/`targetSdk` 37.
- `scripts/emu/create-avds.sh`: creates all three AVDs once (Pixel 7 profile) without touching other AVDs.

## Scripts

- `scripts/emu/start.sh [sp36|sp29|sp37]` — boots headless if needed, waits for boot, disables animations, sets lock-screen PIN `1111` once.
- `scripts/emu/install.sh` — builds/installs the `debug` APK (`com.example.smileproc.debug`).
- `scripts/emu/lock.sh`, `scripts/emu/wake.sh` (screen on, still locked), `scripts/emu/unlock.sh` (opens PIN pad and enters `1111`; emulators only).
- `scripts/py scripts/emu/notifs.py [--expect-max 1]` — prints SmileProc notifications as JSON (`id`, `channel`, `ongoing`, `text`, action titles) from `dumpsys notification --noredact`; exits `1` if count exceeds `--expect-max`.
- `scripts/py scripts/emu/tap.py "<text>" [--shade|--pull|--swipe]` — locates a node by text or content description via `uiautomator` and taps/swipes it (works while locked).
- `scripts/emu/shot.sh <name>` — saves `build/emu-shots/<name>.png` and prints the path. Inspect the PNG directly with `read_file` using its absolute path.
- `scripts/emu/probe.sh` — prints debug probe JSON (entry count, counts per mood/source, located count, notification machine state, last test result; never user content).
- `scripts/emu/fire.sh reminder|restore|seed-demo|clear-demo|seal-test` — sends debug broadcasts exercising the exact alarm/demo/locked-seal code paths.
- `scripts/emu/geo.sh <lat> <lon>` — sets emulator GPS (`adb emu geo fix <lon> <lat>`).
- `scripts/emu/idle.sh on|off` — forces or exits deep Doze (`dumpsys deviceidle enable` + `force-idle` / `unforce`).
- `scripts/emu/reboot.sh` — reboots and waits for boot completion (device comes back locked before first unlock).

## Gotchas

- **Cold boot (~5 min on software GPU):** If `"System UI isn't responding"` appears right after boot, dismiss with `scripts/py scripts/emu/tap.py Wait`.
- **Direct Boot boundary:** Before the first unlock after reboot, the app does not run and posts no notification (expected).
- **Stale UI dumps:** `uiautomator` dumps right after a tap can be stale; `sleep 1` before reading UI again.
- **Collapsed lock-screen card:** On API 36, run `tap.py Expand`, then `tap.py Okay`. On API 29 (`Expand` and `--shade` are no-ops on the lock screen), run `tap.py Okay --pull` (slow pull-down). Neither unlocks the device.
- **Dismissing notifications:** `tap.py <text> --swipe` performs a fast swipe from the left (slow centre swipes do not dismiss on API 36).
- **M1 spike package:** Prefix `PKG=com.example.smileproc.spikes` to aim `notifs.py`, `tap.py`, and shell helpers at `:spikes` (`scripts/emu/spikes.sh`).
- **Alarms vs logic:** Inexact alarms may fire late; use `fire.sh` for state-machine checks and reserve real-alarm waiting (`E13`) for milestones that explicitly require it.
- **Evidence:** Name screenshots by scenario (`e1-locked-faces.png`), inspect them via `read_file`, and record their paths in your report.
- **Real phone:** Only when the human connects it and sets `SMILEPROC_SERIAL`. Never type the human's PIN — ask the human to unlock.

## Cloud / no-emulator sessions

If `adb` is unavailable via `scripts/lib/env.sh` or `adb devices` lists no emulator, do not attempt to install an SDK or boot an AVD: report every emulator scenario as `not run (cloud)` for verification in a local session.
