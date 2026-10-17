# C6 · `docs/spec/testing.md`

#### Automated tests
- **`:core` (JVM):** every fixture's `expect` and every golden; library coverage; config completeness; noise guard (100 seeded random weeks of 25 entries → average `pattern` items per card ≤ 0.3; if it fails, tell the human, don't change thresholds); DST and time zones (`dst-week`, `travel`, `periods/`); `no-location`; low-run cases (5 Low on one day → no card; across 2 days → card; dismiss and re-arm); every `CheckInMachine` row plus the one-notification property test; export crypto and `vector-1.json`.
- **Robolectric (`:app`):** Room and repositories; seal/open, tamper and wrong-id failure; `CheckInReceiver` save / fail / restore / dismiss / test paths; `ShadowNotificationManager` never holds more than one SmileProc notification; alarm rescheduling on boot, time and time-zone change; finishing or skipping onboarding (fresh install, and after Delete all) stores the four default reminders and schedules the next one (e.g. 12:00 when it's 10:00); building a weekly card posts no notification.
- **Compose UI:** onboarding skip path (the default reminders are stored); tap face → saved → follow-up optional; edit and delete; reminders CRUD (first run shows the four defaults; deleting all leaves none); Lock-screen check-in rows; Week tab with demo data.
- **Instrumented (emulator):** `KeystoreSealer` seal/open; migration tests after M5.

#### Emulator scenarios (Claude runs them; evidence = command output + screenshots)
| Id | Scenario | Expected |
|---|---|---|
| E1 | Standing on. Lock, wake. `notifs.py`; screenshot; `tap.py "Okay" --shade`; `notifs.py`; `probe.sh` | Exactly 1 notification, ongoing, "How are you right now?", actions Low/Okay/Good; after the tap "Saved ✓" and no actions; probe: +1 entry, okay +1, source notification; the screenshot shows no mood data |
| E2 | After E1: `fire.sh restore`, then once with a real wait of up to 3 min | Back to the faces, silent |
| E3 | `fire.sh reminder` twice, tap, `fire.sh reminder`, test check-in; `notifs.py --expect-max 1` after every step | Never more than 1 SmileProc notification |
| E4 | API 34+, unlocked: swipe the notification away; `probe.sh`; `fire.sh reminder` | Gone; `dismissed` true; back after the reminder |
| E5 | Locked: try to swipe it away | Still there |
| E6 | `reboot.sh`; `notifs.py` before unlock; `unlock.sh`; wait 30 s; `notifs.py`; `dumpsys alarm` for the package | None before unlock; standing back after; next reminder alarm scheduled |
| E7 | Turn standing off in Settings; `fire.sh reminder`; tap Good; `fire.sh restore` | Faces (not ongoing) → "Saved ✓" → gone |
| E8 | API 33+: `pm revoke … POST_NOTIFICATIONS`; open Lock-screen check-in; screenshot; `pm grant`; screenshot | Problem row with fix, then ✓ |
| E9 | Send a test check-in; lock; wait 12 s; tap Good on the lock screen; unlock; screenshot; `probe.sh` | Test faces → "It works ✓"; screen shows "It works ✓"; entry count unchanged |
| E10 | `geo.sh 45.4642 9.19`; tap a face in the app; wait 10 s; `probe.sh` | located +1 |
| E11 | Fix set 1 min earlier; lock; tap a face on the lock screen; `probe.sh` | Saved; record whether located (spike A2 data) |
| E12 | Revoke location; tap a face in the app and on the lock screen | Both saved, located unchanged |
| E13 | Reminder 3 min ahead; `idle.sh on`; wait up to 15 min | Reminder fires (record the delay) |
| E14 | App lock on; background 61 s; reopen; screenshot; enter PIN; open recents; screenshot | Prompt shown; app after PIN; recents show no content |
| E15 | `fire.sh seed-demo`; screenshots of Week (normal, low week, not enough data, first week) | Each variant renders; low week leads with acknowledgment and Talk to someone |
| E16 | Delete all; `probe.sh`; `notifs.py`; `dumpsys alarm` | 0 entries, no notification, no alarms |
| E17 | `settings put system font_scale 2.0`; screenshots of Today, the follow-up sheet, Week, Settings; reset | Nothing clipped or overlapping |
| E18 | `cmd uimode night yes`; the same screenshots; reset | Readable, sufficient contrast |
| E19 | On `sp29`: E1, E3, E14 | Same results on Android 10 |

#### Human checks (real phone)
| Id | Check |
|---|---|
| H1 | Real lock screen (Pixel and/or Samsung): standing notification visible with default settings and silent; each face saves without unlocking; on Samsung, with lock-screen notifications as icons only and as details |
| H2 | Real GPS: fresh fix in the app within ~10 s; result of a notification tap (fresh / cached / none) |
| H3 | Reminders within a few minutes of the set time after an overnight idle |
| H4 | TalkBack through check-in, the tag list, the weekly card and the notification buttons |
| H5 | Real fingerprint or face unlock for the app lock |
| H6 | Export → uninstall the debug app → reinstall → import (SAF picker) → history restored |
| H7 | Phone-to-phone transfer or backup doesn't carry SmileProc data |
| H8 | Daily use of the dogfood build from M5 on |
