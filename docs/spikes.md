# Spikes

Filled in M1 (android-implementation-plan.md, Part D › M1), and whenever an Android API behaves differently from the spec (then: write it up here and stop).

The spike app is `:spikes` (`com.example.smileproc.spikes`), throwaway and never shipped. It mirrors the spec's check-in notification (channel `checkin`, ID 1, Low/Okay/Good actions, ongoing when standing is on), seals every record with its own Keystore key (`spikes-db-v1`, no user-auth or unlocked-device requirement) into a one-table Room DB, and shows the results on its readout screen. Records never hold coordinates: only outcome, provider, accuracy and age.

## Running it
Emulator (Claude): every command below runs from the repo root in Git Bash with `export PKG=com.example.smileproc.spikes` and `SMILEPROC_SERIAL` set (`emulator-5554` = sp36, `emulator-5556` = sp29).
- `scripts/emu/spikes.sh install` builds, installs and grants location (and notifications on 13+).
- `scripts/emu/spikes.sh probe` prints device state and the last 40 records.
- `scripts/emu/spikes.sh ctl <ACTION>`: `POST --es variant silent|alerting`, `CANCEL`, `SET --ez standing true|false --es mode lastKnown|fgs`, `SCHEDULE_REMINDER --el seconds N` or `--es at HH:MM`, `CLEAR`.
- `scripts/emu/spikes.sh app [locateNow|startFgs]` opens the readout in the foreground and runs the command.
- `scripts/py scripts/emu/notifs.py`, `tap.py` and the other emulator scripts follow `PKG`.

Phone (human): install `spikes/build/outputs/apk/debug/spikes-debug.apk` (built by `./gradlew :spikes:assembleDebug`). It has its own id and doesn't touch the daily app. Everything is on the readout screen: permissions, post silent or alerting, standing on/off, the A2 switch, "Get location now", "Start location service", reminders, and the results list.

Location outcomes: **fresh** = at most 10 s old, **cached** = older, **none** = no fix.

## A1 — Faces notification on the lock screen
- **Question:** are the silent and alerting variants visible on the lock screen, and do the face buttons save without unlocking?
- **Steps (each AVD):**
  1. `spikes.sh install`, `spikes.sh ctl CLEAR`, `spikes.sh ctl SET --ez standing true --es mode lastKnown`.
  2. `spikes.sh ctl POST --es variant silent`, `lock.sh`, `wake.sh`. `notifs.py --expect-max 1`; screenshot `m1-a1-<avd>-silent-locked`.
  3. Reach the buttons without unlocking: `tap.py Expand` (if there is no Expand node, `tap.py Okay --shade`); screenshot `m1-a1-<avd>-silent-expanded`; `tap.py Okay`; after 3 s `notifs.py` and screenshot `m1-a1-<avd>-silent-saved`. Check the device still reports locked (`lock.sh` prints "locked" without a state change; or `dumpsys trust`).
  4. `spikes.sh probe`: one new `tap` record, mood 2, `locked: true` (sealed and opened again by the probe).
  5. Repeat 2–4 with `--es variant alerting` and Good (mood 3). In `dumpsys notification`, the silent variant has the `SILENT` flag and the alerting one doesn't.
- **Results:** —

## A2 — Location from a notification tap
- **Question:** (a) how often does last known location work inside the receiver; (b) what does a short location foreground service started from a button return (fresh / cached / none, accuracy, age)?
- **Steps (each AVD; after A3's reboot, so nothing has asked for a fix since boot):**
  1. **a / none:** `SET --es mode lastKnown`. Lock, wake, expand, tap Low. Probe: the tap's `location`.
  2. **a / fix but no request:** `geo.sh 45.4642 9.19`. Lock, wake, expand, tap Okay. Probe.
  3. **a / cached:** `unlock.sh`, `geo.sh 45.4642 9.19`, `spikes.sh app locateNow`, wait 15 s, probe (a `locate` record). `lock.sh`, wait 60 s, wake, expand, tap Good. Probe: outcome and age (≈ 60–75 s).
  4. **b / FGS from the face button:** `SET --es mode fgs`, `geo.sh 45.4650 9.1900`. Lock, wake, expand, tap Okay. `notifs.py --expect-max 1` right away and after 15 s. Probe: the `fgs` record (`started`, `exception`, `location`, `totalMs`) next to the tap.
  5. **b / FGS from the app:** `unlock.sh`, `geo.sh 45.4660 9.1900`, `spikes.sh app startFgs`, wait 15 s, `notifs.py --expect-max 1`, probe.
- **Results:** —
- **Human decision:** last known only, or a short location foreground service (Appendix 3). —

## A3 — Swipe-away and reboot
- **Question:** can the ongoing notification be swiped away while locked; on API 34+ while unlocked (does the delete intent fire); does it come back after reboot via `BOOT_COMPLETED`?
- **Steps (each AVD):**
  1. Standing on, `POST --es variant silent`, `lock.sh`, `wake.sh`, `tap.py SmileProc --swipe`, `notifs.py`, screenshot `m1-a3-<avd>-locked-swipe`. Probe: no `dismiss` record.
  2. `unlock.sh`, `tap.py SmileProc --shade --swipe`, `notifs.py`, screenshot `m1-a3-<avd>-unlocked-swipe`. Probe: a `dismiss` record when it went away.
  3. `POST --es variant silent`, `reboot.sh`. Before unlocking: `notifs.py` and screenshot `m1-a3-<avd>-boot-locked`. `unlock.sh`, wait 30 s, `notifs.py`, probe (a `boot` record, `posted: true`).
- **Results:** —

## A4 — Reminder under Doze (short form)
- **Question:** how late is an inexact `setAndAllowWhileIdle` reminder set 3 minutes ahead under forced idle?
- **Steps (each AVD):**
  1. `unlock.sh`, `spikes.sh ctl CANCEL`, `spikes.sh ctl SCHEDULE_REMINDER --el seconds 180`, note the time; `dumpsys alarm | grep -A3 smileproc.spikes`.
  2. `idle.sh on` (screen off, deep IDLE).
  3. Poll `notifs.py` (dumpsys only, nothing sent to the app) every 20 s for up to 15 min until the faces appear; check `dumpsys deviceidle get deep` stays IDLE until then.
  4. `idle.sh off`, probe: the `reminder` record's `delayS` and `idle`.
- **Results:** —

## Results table
| Spike | Device / API | Variant | Result | Notes |
|---|---|---|---|---|
