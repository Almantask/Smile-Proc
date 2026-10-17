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
- **Status:** not started

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
