# SmileProc progress

One section per milestone (android-implementation-plan.md, Part D). Read this at the start of every session.

Status values: not started · in progress · partly verified · claude-verified · accepted.

## M0 — Repo, Claude Code setup, scaffold
- **Status:** accepted (2026-10-17; claude-verified 2026-10-11, tag `m0-claude-verified`)
- **Built:**
  - `docs/spec/*.md` split verbatim from Part C; this file, `docs/spikes.md`, `docs/device-test-checklist.md`.
  - `content/`: `pattern-config.json`, `pattern-library.en.json` and `support.json` verbatim from data.md; `emotion-tags.json` (7 cores × 5 fine ids, one colour per core).
  - `app/src/main/res/values/strings.xml`: faces, notification text, 42 tag labels, dayparts, weekdays, `week_observation_*` lines and all copy in screens.md.
  - `tools/generate_fixtures.py` and its output: `fixtures/patterns/` (14 scenarios), `fixtures/places/` (5), `fixtures/periods/card-periods.*` (10 cases). No goldens yet (M2).
  - Gradle 9.7.1 wrapper; `gradle/libs.versions.toml` pinned to the latest stable versions on 2026-10-11 (AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5, DataStore 1.2.1, biometric 1.1.0, coroutines and serialization 1.11.0, Robolectric 4.17). compileSdk and targetSdk 37, minSdk 29.
  - `:core` (JVM) with `ContentContractTest`, `FixturesSmokeTest` and `StringsContractTest` (12 tests). No main code yet.
  - `:app`: Compose hello screen, theme, `SafeLog`, manifest with backups off, launcher icon. Build types `debug` (`.debug`, "SmileProc (debug)"), `dogfood` and `release`. Debug-only `ProbeReceiver` and `DebugEventsReceiver` stubs.
  - Scripts:
    - `scripts/py`, `scripts/privacy-guard.sh` (rules in `scripts/lib/privacy_guard.py`), `scripts/check.sh` (with `scripts/lib/check_manifest.py`) and `scripts/lib/env.sh`.
    - `scripts/emu/*`: everything in B.8, plus `create-avds.sh` and `_adb.py`.
  - AVDs `sp36` and `sp29`.
  - `.claude/hooks/` (`guard_edit.py` and `stop_check.sh` verbatim, `py.sh`) and `.claude/settings.json`.
  - Machine-only, not committed: `local.properties`, plus `.claude/settings.local.json` with `JAVA_HOME` = Android Studio JBR and `ANDROID_HOME`.
- **Evidence:**
  - `scripts/check.sh`: privacy-guard, fixtures, json, gradle (`:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDogfood`), dogfood-classpath, dogfood-permissions and dogfood-manifest all PASS → `check.sh: PASS`.
    - Lint: 0 errors. The 268 warnings are strings later milestones use.
    - The dogfood APK's only permission is AndroidX's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; it has no exported receivers.
  - Guard self-test (step 7), 17/17 cases:
    - The plan's two cases: the okhttp3 write exits 2; harmless Kotlin exits 0.
    - Also covered: SafeLog and CheckInNotifier exemptions, `:core` android imports, human-owned files, goldens, MultiEdit, Windows paths.
    - Once `settings.json` existed, the live hook blocked a Write of `import okhttp3.OkHttpClient` in this session.
  - `scripts/emu/start.sh sp36 && scripts/emu/install.sh`: hello screen in `build/emu-shots/m0-hello.png` (API 36). The same on sp29: `build/emu-shots/m0-hello-sp29.png` (API 29).
  - Emulator scripts exercised:
    - `probe.sh` returns the stub JSON and `notifs.py` returns `[]`.
    - `lock.sh`, `wake.sh` and `unlock.sh` work on API 29 and 36.
    - `idle.sh on` reaches deep IDLE and `off` leaves it.
    - `reboot.sh` comes back locked with no notification.
    - `geo.sh` and `fire.sh` work.
    - adb refuses `com.example.smileproc`.
  - Fixtures cross-checked by the spec-reviewer against its own implementation of logic.md (not committed): all pattern, places and period expectations agree.
- **Left:** nothing for M0 beyond the human checks.
- **Deviations:**
  1. The plan stays at the repo root as `android-implementation-plan.md` (CLAUDE.md), not `docs/plan.md`.
  2. **Python resolver.** On this PC `python3` is a Windows Store stub.
     - `scripts/py` and `.claude/hooks/py.sh` try python3, then `py -3`, then python. `py.sh` exits 2 when none works, so the guard fails closed.
     - The PreToolUse command runs `guard_edit.py` through `py.sh`. Otherwise `settings.json` is B.3, minus the `Bash(git push *)` deny rule, which the human had removed on 2026-10-11 so Claude can push.
     - CLAUDE.md and the android-emulator skill say `scripts/py` instead of `python3`.
  3. `privacy-guard.sh` runs `scripts/lib/privacy_guard.py`, which uses the hook's own regexes on whole files. A line-based grep let a `.notify(` call split over several lines through.
  4. **sp36 is API 36** (`system-images;android-36;default;x86_64`): API 37 has no image without Google APIs or Play. The app compiles and targets 37.
  5. JVM target 17, compiled by Android Studio's JBR 21 (`-Xjdk-release=17` in `:core`). There is no JDK 17 here, and no toolchain-download plugin (not on the allowlist).
  6. Debug receivers are exported but need `android.permission.DUMP`, which only the adb shell holds.
  7. androidx.profileinstaller's exported `ProfileInstallReceiver` is removed (`tools:node="remove"`), so `SystemEventsReceiver` will be the only exported receiver. Profiles still install at startup.
  8. Observation lines are `<plurals>`, keeping the spec's names and English text (needed for Italian later).
  9. Tag colours are chip fills under dark text (`#1C1B1F`) in both themes, at least 7.9:1, checked in `ContentContractTest`. Colour is never the only signal (chips carry labels).
  10. **Fixtures.**
      - `expect.json` files carry a `notes` key with the hand check; tests ignore it.
      - The extra places scenario `centroid-drift` exercises the stabilise step.
      - Periods come as one `card-periods` input/expect pair.
  11. Backup and data-extraction rules (on M3's list) came forward to M0: `allowBackup="false"`, `dataExtractionRules` and `fullBackupContent` exclude every domain.
  12. `.gitattributes` keeps scripts, JSON, Kotlin, Gradle and XML files LF on every platform. Git here has `core.autocrlf=true`, which would break the bash scripts.
- **Not run:** none.
- **Not fixed (for M2):** logic.md rules no fixture can fail yet. M2 adds unit tests for:
  - weekday detectors skipped while the first entry is under `weekdayMinDataDays` before periodEnd;
  - `weekdayMinDistinctDates`;
  - the balance swap (see decision 3);
  - early hints filling free slots;
  - the PlaceResolver tie rule (lower ClusterID wins).
- **Human checks:**
  1. In a terminal `claude` session in this repo, run `/hooks` and confirm both hooks are listed:
     - PreToolUse `Edit|Write|MultiEdit` → `py.sh guard_edit.py`;
     - Stop → `stop_check.sh`.
  2. Set the identifiers (Appendix 7): `applicationId`, then the two package deny rules in `.claude/settings.json`. The guard blocks Claude from editing that file.
  3. **JAVA_HOME.** The system `JAVA_HOME` points to a JDK 11 JRE, which can't run Gradle.
     - `.claude/settings.local.json` sets Android Studio's JBR for Claude sessions. In the next session, check that `echo $JAVA_HOME` shows the JBR, because the Stop hook runs `./gradlew`.
     - Or point the system `JAVA_HOME` at a JDK 17+.
- **Decisions needed:**
  1. **Room line**, before M3 creates the schema (migrations are locked in after M5).
     - `androidx.room` 2.8.5 is what's pinned now.
     - Room 3 (`androidx.room3` 3.0.3) is coroutines-only and generates Kotlin.
  2. **Android 17 coverage.** sp36 runs API 36. Is that enough, or should I add an API 37 Google APIs AVD (about 2.2 GB, includes Play Services) to check Android 17 behaviour?
  3. **Balance rule**, before M2 builds `WeeklyCardBuilder` (logic.md › Selection). When a `higher` pattern replaces the last pick, may it come from a family already picked, which gives two items from one family? Or must it respect one-per-family?
  4. **Support screen emergency line**, before M8: should it include a generic "if you're in danger right now, contact your local emergency number" line? Today's copy names no contacts.
  5. **Version pairing (for information).** AGP 9.4.1 is newer than the AGP range Kotlin 2.4.21 lists as tested (up to 9.3.1). It builds cleanly. KSP 2.3.12 with Kotlin 2.4.21 gets proven when Room arrives in M3.

## M1 — Spikes
- **Status:** claude-verified (2026-10-17, tag `m1-claude-verified`). A2 is decided (decision 1 below), pending the human's phone checks.
- **Built:**
  - `:spikes` (`com.example.smileproc.spikes`, never shipped, not in check.sh), under `spikes/src/main/java/com/example/smileproc/spikes/`:
    - `SpikeNotifier`: the spec's check-in notification: channel `checkin`, ID 1, Low/Okay/Good, silent and alerting, ongoing when standing; resting state and `dismissed` per logic.md.
    - `SpikeSealer` + `SpikeDb`: Keystore AES-256-GCM, Room one-table DB.
    - `FaceReceiver`, `LocationFgs`, `ReminderReceiver`, `BootReceiver`.
    - `ControlReceiver`: adb control, DUMP-guarded.
    - `SpikeActivity`: readout and controls for phone runs.
  - Gradle: `include(":spikes")` and the KSP plugin (`apply false`) at the root.
  - Emulator scripts:
    - `scripts/emu/spikes.sh` (install / probe / ctl / app).
    - `PKG` override in `env.sh` and `_adb.py`.
    - `tap.py --swipe` and `--pull`.
  - `docs/spikes.md`: steps, results and the results table for A1–A4.
- **Evidence:**
  - Spike runs, on sp36 (API 36) and sp29 (API 29): every step of A1–A4 has a recorded result (docs/spikes.md).
    - Screenshots: `build/emu-shots/m1-a1-sp36-*`, `m1-a1-sp29-*`, `m1-a2-sp36-*`, `m1-a2-sp29-*`, `m1-a3-sp36-*`, `m1-a3r-sp36-*`, `m1-a3-sp29-*`, `m1-a4-sp36-*`, `m1-a4-sp29-*`.
    - A1: faces visible on the lock screen. A tap saves while locked (`dumpsys trust` deviceLocked before and after; tap records `locked: true`). One notification at every step (`notifs.py --expect-max 1`).
    - A2: last known in the receiver gave none in 9 of 9 taps (both API levels). The location service started from a locked face tap gave a fresh gps fix (sp36 ±5 m in 4.6 s, sp29 ±20 m in 80 ms) with one notification throughout.
    - A3 (sp36): ongoing never dismissed while locked (0 of 10). Dismissed while unlocked 2 of 8, with the delete intent. A non-ongoing control was dismissed 3 of 10 and 1 of 5 under the same `adb input swipe`. Reboot: none before unlock, back after unlock on both.
    - A4: 135 s late (sp36) and 0.2 s late (sp29) under forced deep idle.
  - KSP 2.3.12 + Room 2.8.5 compile with Kotlin 2.4.21 and AGP 9.4.1 (`:spikes:assembleDebug`, M0 decision 5).
  - The spike APK's permissions: location, notifications, boot, FOREGROUND_SERVICE(_LOCATION). No INTERNET.
  - `scripts/check.sh`, after the last code change: privacy-guard, fixtures, json, gradle, dogfood-classpath, dogfood-permissions and dogfood-manifest all PASS → `check.sh: PASS`.
  - Spec review (product.md, android.md; range `m0-claude-verified..HEAD`): nothing breaks the spec or a non-negotiable in the shipped app; `:app`, `:core`, the guards and check.sh are unchanged. Fixed from its findings:
    - `env.sh` refusal (see Deviations);
    - notifs.py zero-count guard;
    - plaintext probe file deleted after reading;
    - resting state and `dismissed`;
    - a location or lock-state exception can't fail a tap;
    - the service's start-failure record.
  - Research (AOSP 15/16 source and Android 14 behaviour changes): an ongoing notification from an ordinary app is dismissible whenever SystemUI sees the device unlocked, unless it has `FLAG_NO_DISMISS`. This agrees with the sp36 re-test.
- **Left:** the human checks and decision 1 below. Copy-reviewer not needed: no strings.xml or content changes.
- **Deviations:**
  1. **Two M0 tooling bugs fixed:**
     - On Android 16 `dumpsys` prints notification flags as names, not hex, so notifs.py skipped every record and `--expect-max` could never fail on sp36. It now parses both, and exits with an error if it finds a record it can't parse, or no Notification List.
     - The emulator scripts printed "✓" through cp1252 and crashed; they now use UTF-8.
  2. `env.sh` refusal: with `PKG` set to the daily app, every emu script now stops (`. env.sh || exit 1`), and the guarded `adb()` refuses as well.
  3. **The spike's own differences from android.md, none of which affect its results:**
     - SharedPreferences instead of DataStore;
     - the channel is created at render and at activity start;
     - the boot receiver handles BOOT_COMPLETED only;
     - one-shot reminders;
     - no Test state;
     - a plaintext `kind` column;
     - the service's fix is stored as a separate `fgs` record linked to the tap, not as an update of the tap.
- **Not run:** none.
- **Not fixed:** `.claude/hooks/guard_edit.py` exempts any path containing `/spikes/`, for example `app/src/main/.../spikes/X.kt`, not just the repo's `spikes/` module. The hook is human-owned. `privacy_guard.py` (check.sh, Stop hook) still scans `app/src/main`, so such a file would still fail check.sh. Suggested change for the human: match `<repo>/spikes/` only.
- **Human checks** (spike APK: docs/spikes.md › Running it; separate app id, the daily app is untouched):
  1. **A1 / H1:** on your phone(s), with the readout's "Keep it on the lock screen" on:
     - post silent, lock, check the card is on the lock screen, reach the faces without unlocking, tap one;
     - the readout should list a `tap … locked`;
     - on Samsung, do it once with lock-screen notifications as icons only and once as details;
     - repeat with alerting.
  2. **A2 / H2 (real GPS):**
     - with the switch off (last known): tap a face on the lock screen a few times through the day; does any tap show `cached` or `fresh`?
     - with the switch on: the `fgs` line: fresh/cached/none, accuracy and seconds.
  3. **A3 (Android 14+ phone):** unlocked, swipe the ongoing card away. Does it go, and does a `dismiss` line appear? Locked, it should stay.
  4. **A4 / H3:** set a reminder for tomorrow morning (HH:MM), leave the phone overnight, and read the delay in the readout.
- **Decided:**
  1. **A2 (human, 2026-10-17): a short location foreground service after the save**, once the phone checks (human check 2, H2) confirm it.
     - What the spikes showed: on both emulators last known gave no location on any notification tap; the service gave a fresh fix within about 5 s with no second notification.
     - Not done yet; these follow once the phone checks confirm:
       - **Spec:** android.md › Location (the notification-tap path: last known at save, then the service updates the entry) and › Manifest (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, the service with `foregroundServiceType="location"`); product.md › Scope ("location foreground service" moves into v1).
       - **Guards (Appendix 3, the human):** remove the FOREGROUND_SERVICE rule from `.claude/hooks/guard_edit.py` (human-owned), `scripts/lib/privacy_guard.py` and the dogfood-permissions step of `scripts/check.sh`. Claude won't weaken a guard on its own.
       - **Release (M10):** Google Play's foreground-service declaration for the location type.
- **Decisions needed:**
  1. **A1: faces one gesture away on the lock screen.** On both API levels the card is collapsed on the lock screen, and the faces need an expand gesture. They work without unlocking. Accept that? Or should a later spike try faces on the collapsed card itself (a custom collapsed layout; untested whether its buttons work while locked)?
  2. Still open from M0: the Room line (before M3), Android 17 coverage, the balance rule (before M2), the Support emergency line (before M8).

## M2 — Core logic
- **Status:** not started

## M3 — Storage and crypto
- **Status:** not started

## M4 — Check-in in the app
- **Status:** not started

## M5 — Check-in notification and reminders → dogfood build 1
- **Status:** not started

## M6 — Detailed emotion wheel
- **Status:** not started

## M7 — Places UI
- **Status:** not started

## M8 — Weekly card and support
- **Status:** not started

## M9 — Privacy and security
- **Status:** not started

## M10 — Polish and release prep
- **Status:** not started
