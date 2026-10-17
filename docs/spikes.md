# Spikes

Filled in M1 (android-implementation-plan.md, Part D › M1), and whenever an Android API behaves differently from the spec (then: write it up here and stop).

The spike app is `:spikes` (`com.example.smileproc.spikes`), throwaway and never shipped. It mirrors the spec's check-in notification (channel `checkin`, ID 1, Low/Okay/Good actions, ongoing when standing is on, logic.md's resting state and `dismissed`). It seals every record with its own Keystore key (`spikes-db-v1`, no user-auth or unlocked-device requirement) into a one-table Room DB. Its readout screen shows the results. Records never hold coordinates: only outcome, provider, accuracy and age. The one plaintext copy is the adb probe file. `spikes.sh probe` deletes it right after reading it, and CLEAR deletes it too.

## Running it
**Emulator (Claude).** Run every command below from the repo root in Git Bash, with `export PKG=com.example.smileproc.spikes` and `SMILEPROC_SERIAL` set (`emulator-5554` = sp36, `emulator-5556` = sp29).
- `scripts/emu/spikes.sh install` builds, installs and grants location (and notifications on 13+).
- `scripts/emu/spikes.sh probe` prints device state and the last 40 records.
- `scripts/emu/spikes.sh ctl <ACTION>` takes:
  - `POST --es variant silent|alerting`
  - `CANCEL`
  - `SET --ez standing true|false --es mode lastKnown|fgs`
  - `SCHEDULE_REMINDER --el seconds N` or `--es at HH:MM`
  - `CLEAR`
- `scripts/emu/spikes.sh app [locateNow|startFgs]` opens the readout in the foreground and runs the command.
- `scripts/py scripts/emu/notifs.py`, `tap.py` and the other emulator scripts follow `PKG`.

**Phone (human).** Build `./gradlew :spikes:assembleDebug`, then install `spikes/build/outputs/apk/debug/spikes-debug.apk`. It has its own id and doesn't touch the daily app. The readout screen has everything:
- permissions;
- post silent or alerting;
- standing on/off;
- the A2 switch ("After a face tap, also run the location service");
- "Get location now" and "Start location service";
- reminders, in 3 minutes or at HH:MM;
- the results list.

**Location outcomes:** **fresh** = at most 10 s old, **cached** = older, **none** = no fix.

**Reaching the face buttons on a locked emulator** (the card is collapsed on both API levels):
- API 36: `tap.py Expand`, then `tap.py Okay`.
- API 29: `tap.py Okay --pull` (a slow pull-down). On API 29, Expand and `cmd statusbar` do nothing on the lock screen.

## A1 — Faces notification on the lock screen
- **Question:** are the silent and alerting variants visible on the lock screen, and do the face buttons save without unlocking?
- **Steps (each AVD):**
  1. `spikes.sh install`, `spikes.sh ctl CLEAR`, `spikes.sh ctl SET --ez standing true --es mode lastKnown`.
  2. `spikes.sh ctl POST --es variant silent`, `lock.sh`, `wake.sh`. `notifs.py --expect-max 1`; screenshot `m1-a1-<avd>-silent-locked`.
  3. Reach the buttons without unlocking (see Running it). Screenshot `m1-a1-<avd>-silent-expanded`, then tap Okay. After 3 s, `notifs.py` and screenshot `m1-a1-<avd>-silent-saved`. Check the device still reports locked (`dumpsys trust`).
  4. `spikes.sh probe`: one new `tap` record, mood 2, `locked: true` (sealed, and opened again by the probe).
  5. Repeat 2–4 with `--es variant alerting` and Good (mood 3). Compare the record's flags in `dumpsys notification`.
- **Results:**
  - **Visible on both:** both variants show on the lock screen of sp36 and sp29 as a collapsed card ("SmileProc", "How are you right now?", a chevron). Alerting adds a bell icon. No mood data, ever.
  - **The buttons are one gesture away, on both:**
    - API 36: tap the card's Expand chevron. This opens the locked shade with LOW / OKAY / GOOD.
    - API 29: pull the shade down. A tap on the chevron does nothing on the lock screen.
    - Neither unlocks.
  - **A face tap saves while locked, on both:** `dumpsys trust` reports deviceLocked before and after; the tap record has `locked: true`. Sealing and opening with the Keystore key works while locked. The card becomes "Saved ✓" with no buttons, still ID 1, and `notifs.py --expect-max 1` passed at every step.
  - **Flags:**
    - API 36: silent `ONGOING_EVENT|ONLY_ALERT_ONCE|LOCAL_ONLY|SILENT`, alerting `ONGOING_EVENT|LOCAL_ONLY`; importance 3, visibility PUBLIC.
    - API 29: silent `0x10a` plus group "silent" with GROUP_ALERT_SUMMARY (how `setSilent` works there), alerting `0x102`.
  - **Not tested:** whether the faces could sit on the collapsed card itself (a custom collapsed layout). See Decisions.

## A2 — Location from a notification tap
- **Question:** (a) how often does last known location work inside the receiver; (b) what does a short location foreground service started from a button return (fresh / cached / none, accuracy, age)?
- **Steps (each AVD; after A3's reboot, so nothing has asked for a fix since boot):**
  1. **a / none:** `SET --es mode lastKnown`. Lock, wake, reach the buttons, tap Low. Probe: the tap's `location`.
  2. **a / fix but no request:** `geo.sh 45.4642 9.19`. Lock, wake, tap Okay. Probe.
  3. **a / cached:**
     - `unlock.sh`, `geo.sh 45.4642 9.19`, `spikes.sh app locateNow`, wait 15 s, probe (a `locate` record).
     - `lock.sh`, wait 60 s, wake, tap Good. Probe: outcome and age.
  4. **b / service from the face button:** `SET --es mode fgs`, `geo.sh 45.4650 9.1900`. Lock, wake, tap Okay. `notifs.py --expect-max 1` right away and after 15 s. Probe: the `fgs` record (`started`, `exception`, `location`, `totalMs`) next to the tap.
  5. **b / service from the app:** `unlock.sh`, `geo.sh 45.4660 9.1900`, `spikes.sh app startFgs`, wait 15 s, `notifs.py --expect-max 1`, probe.
- **Results:**
  - **(a) Last known inside the receiver: always none.**
    - sp36: 5 of 5 taps (4 locked, 1 unlocked with the app in the background). sp29: 4 of 4.
    - This held even 90–107 s after a fresh fix that `dumpsys location` still listed for gps, fused and passive.
    - Every provider returned null; no exception was thrown. AppOps shows FINE and COARSE as `mode=foreground`, and sp29 logs `FINE_LOCATION Reject: [bg-s]` at the second of each tap.
    - Cause: without `ACCESS_BACKGROUND_LOCATION`, a broadcast receiver runs in the background process state, and the platform hides location from it.
    - The lookup took 11–289 ms and never held up the save.
  - **(b) A short location foreground service started from the face tap works, while locked.**
    - It may start because the notification action gives the app a temporary allow-list (logcat: `reasonCode:NOTIFICATION_SERVICE, duration:30000`). It gets while-in-use location (`fgsvcl-s` in AppOps).
    - sp36: fresh, gps, ±5 m, age 0 s, 4.6 s from start to record. sp29: fresh, gps, ±20 m, age 0 s, 80 ms (GNSS already warm).
    - Exactly one notification throughout: the service runs on ID 1 showing "Saved ✓" and detaches it when it stops.
    - Started from the app instead: the same result (sp36 4.6 s, sp29 62 ms).
  - **In-app current location** (the spec's in-app path): fresh in 4.3–4.4 s on sp36 and 0.2 s on sp29.
    - Spike bug, fixed: the callback ran on the main executor. On a cold start the main thread was busy for over 10 s, so the timeout dropped a fix that had arrived after 4 s. The product's in-app path should deliver off the main thread too.
  - **Emulator note:** on sp36, `adb emu geo fix` didn't move the reported coordinates (they stayed near 39.2, −123.15). sp29 took them. Outcome, accuracy and age were unaffected, and nothing records coordinates.
- **Human decision:** last known only, or a short location foreground service (Appendix 3). **Pending.** See progress.md › M1 › Decisions.

## A3 — Swipe-away and reboot
- **Question:** can the ongoing notification be swiped away while locked; on API 34+ while unlocked (does the delete intent fire); does it come back after reboot via `BOOT_COMPLETED`?
- **Steps (each AVD):**
  1. Locked: standing on, `POST --es variant silent`, `lock.sh`, `wake.sh`, swipe the card away (`tap.py "How are you right now?" --swipe`), `notifs.py`. Probe: any `dismiss` record.
  2. Unlocked: `unlock.sh`, `POST`, `cmd statusbar expand-notifications`, swipe, `notifs.py`, probe.
  3. Controls: the same with `SET --ez standing false` (not ongoing).
  4. Reboot: `POST --es variant silent`, `reboot.sh`. Before unlocking: `notifs.py` and a screenshot. Then `unlock.sh`, wait 30 s, `notifs.py`, probe (a `boot` record).
  - A swipe sent through `adb shell input swipe` registers as a dismissal only about 1 time in 4, even for a dismissible notification. Repeat each case at least 5 times and compare with the non-ongoing control. Treat a single failed swipe as no evidence.
- **Results:**
  - **sp36 (API 36):**

    | Case | Dismissed by swipe |
    |---|---|
    | Ongoing, locked | 0 of 10 |
    | Not ongoing, locked (control) | 3 of 10, `dismiss` records `locked: true` |
    | Ongoing, unlocked | 2 of 8, each with a `dismiss` record `locked: false`: the delete intent fired |
    | Not ongoing, unlocked (control) | 1 of 5 in the same run |

    This matches the spec: no swipe-away while locked; from Android 14, swipe-away while unlocked → Dismissed. The first sp36 run had reported "not swipeable while unlocked". That came from a slow swipe and too few attempts, and was re-tested on 2026-10-17. AOSP source agrees: SystemUI blocks dismissal only for `FLAG_NO_DISMISS` or while locked, and our record never had `FLAG_NO_DISMISS`.
  - **sp29 (API 29):** the ongoing notification can't be swiped away, locked or unlocked (expected below API 34). The non-ongoing control was dismissed in the unlocked shade and in the pulled-down locked shade, but not on the collapsed lock-screen card.
  - **Reboot, both API levels:**
    - Before the first unlock there is no notification. The app isn't direct-boot aware, as the spec says.
    - After unlock, `BOOT_COMPLETED` brings the silent faces back within 30 s (boot record `posted: true`, `locked: false`; on sp29 about 10 s after unlock).
    - API 36 showed "System UI isn't responding" once after unlock (the emulator's software GPU); `tap.py Wait` cleared it.

## A4 — Reminder under Doze (short form)
- **Question:** how late is an inexact `setAndAllowWhileIdle` reminder set 3 minutes ahead under forced idle?
- **Steps (each AVD):**
  1. `unlock.sh`, `spikes.sh ctl CANCEL`, `spikes.sh ctl SCHEDULE_REMINDER --el seconds 180`, note the time; `dumpsys alarm | grep -A3 smileproc.spikes`.
  2. `idle.sh on` (screen off, deep IDLE).
  3. Poll `notifs.py` and `dumpsys deviceidle get deep` every 20 s, for up to 15 min, until the faces appear. Send nothing to the app.
  4. `idle.sh off`, then probe: the `reminder` record's `delayS` and `idle`.
- **Results:**
  - **sp36:** fired 135 s late (`delayS` 135.0, `idle: true`), at the very end of the alarm's inexact window: `window=+2m15s`, `maxWhenElapsed` = scheduled + 5 m 15 s. Deep IDLE throughout.
  - **sp29:** fired 0.2 s late under deep IDLE, with the same window.
  - Both are within the spec's "a few minutes of drift". A real overnight idle is human check H3.

## Results table
| Spike | Device / API | Variant | Result | Notes |
|---|---|---|---|---|
| A1 | sp36 / 36 | silent | pass | visible collapsed; Expand → buttons; Okay saved while locked |
| A1 | sp36 / 36 | alerting | pass | same; bell icon; Good saved while locked |
| A1 | sp29 / 29 | silent | pass | visible collapsed; slow pull-down → buttons; Okay saved while locked |
| A1 | sp29 / 29 | alerting | pass | same; Good saved while locked |
| A2a | sp36 / 36 | last known in receiver | none, 5 of 5 | background app-op hides location; save not delayed |
| A2a | sp29 / 29 | last known in receiver | none, 4 of 4 | `Reject: [bg-s]` at each tap |
| A2b | sp36 / 36 | service from locked face tap | fresh gps ±5 m, 0 s, 4.6 s | one notification throughout |
| A2b | sp29 / 29 | service from locked face tap | fresh gps ±20 m, 0 s, 80 ms | one notification throughout |
| A2b | sp36, sp29 | service from the app | fresh | control |
| A3 | sp36 / 36 | locked swipe | stays (0 of 10) | control dismissed 3 of 10 |
| A3 | sp36 / 36 | unlocked swipe | dismissed (2 of 8), delete intent fires | matches spec |
| A3 | sp29 / 29 | swipe, locked or unlocked | stays | expected below API 34 |
| A3 | sp36, sp29 | reboot | none before unlock; faces back after unlock | `BOOT_COMPLETED` |
| A4 | sp36 / 36 | +3 min, forced deep idle | 135 s late | end of the inexact window |
| A4 | sp29 / 29 | +3 min, forced deep idle | 0.2 s late | |
