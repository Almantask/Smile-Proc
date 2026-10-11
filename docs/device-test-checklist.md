# Device test checklist

From docs/spec/testing.md. Claude runs the emulator scenarios (E) and records evidence in docs/progress.md; the human runs the real-phone checks (H).

## Human checks (real phone)
| Id | Check | First milestone | Status | Device / notes |
|---|---|---|---|---|
| H1 | Real lock screen (Pixel and/or Samsung): standing notification visible with default settings and silent; each face saves without unlocking; on Samsung, with lock-screen notifications as icons only and as details | M1 (spike A1), M5 | open | |
| H2 | Real GPS: fresh fix in the app within ~10 s; result of a notification tap (fresh / cached / none) | M1 (spike A2), M4 | open | |
| H3 | Reminders within a few minutes of the set time after an overnight idle | M1 (spike A4), M5 | open | |
| H4 | TalkBack through check-in, the tag list, the weekly card and the notification buttons | M6, M10 | open | |
| H5 | Real fingerprint or face unlock for the app lock | M9 | open | |
| H6 | Export → uninstall the debug app → reinstall → import (SAF picker) → history restored | M9 | open | |
| H7 | Phone-to-phone transfer or backup doesn't carry SmileProc data | M9 | open | |
| H8 | Daily use of the dogfood build from M5 on | M5 | open | |

## Emulator scenarios (Claude)
Emulators (created in M0 by `scripts/emu/create-avds.sh`): `sp36` = API 36 AOSP image without Google Play, `emulator-5554` (API 37 has no Play-free image); `sp29` = API 29, `emulator-5556`. M0 smoke check: the debug build installs and shows the hello screen on both (`build/emu-shots/m0-hello.png`, `m0-hello-sp29.png`).

| Id | Scenario | Milestone | sp36 | sp29 |
|---|---|---|---|---|
| E1 | Standing notification on the lock screen; tap Okay → "Saved ✓" | M5, M10 | — | M5 (E19) |
| E2 | Restore after "Saved ✓" | M5, M10 | — | |
| E3 | Never more than one SmileProc notification | M5, M10 | — | M5 (E19) |
| E4 | API 34+ swipe-away while unlocked → `dismissed`; back after reminder | M5, M10 | — | n/a |
| E5 | Locked: can't swipe away | M5, M10 | — | |
| E6 | Reboot: none before unlock; standing back after; next alarm scheduled | M5, M10 | — | |
| E7 | Standing off: faces → "Saved ✓" → gone | M5, M10 | — | |
| E8 | API 33+: revoke POST_NOTIFICATIONS → problem row, grant → ✓ | M5, M10 | — | n/a |
| E9 | Test check-in → "It works ✓", no entry | M5, M10 | — | |
| E10 | In-app tap with GPS fix → located +1 | M4, M10 | — | |
| E11 | Lock-screen tap with a 1-min-old fix → saved, record located | M5, M10 | — | |
| E12 | Location revoked: app and lock-screen taps both save, located unchanged | M4, M5, M10 | — | |
| E13 | Reminder 3 min ahead under forced idle (record delay) | M5, M10 | — | |
| E14 | App lock after 61 s in background; recents show no content | M9, M10 | — | M9 (E19) |
| E15 | Seeded demo: Week variants render; low week leads with acknowledgment | M8, M10 | — | |
| E16 | Delete all: 0 entries, no notification, no alarms | M9, M10 | — | |
| E17 | Font scale 2.0: nothing clipped (Today, sheet, Week, Settings) | M4, M6, M10 | — | |
| E18 | Night mode: readable, sufficient contrast | M4, M6, M10 | — | |
| E19 | On sp29: E1, E3, E14 | M5, M9, M10 | n/a | — |
