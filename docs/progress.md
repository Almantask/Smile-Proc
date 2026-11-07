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
- **Not fixed (for M2):** logic.md rules no fixture could fail in M0 (all 5 covered by unit tests in M2):
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
  3. **Balance rule**, before M2 builds `WeeklyCardBuilder` (logic.md › Selection): **decided 2026-11-07** — see M2 › Decided before M2.
  4. **Support screen emergency line**, before M8: should it include a generic "if you're in danger right now, contact your local emergency number" line? Today's copy names no contacts.
  5. **Version pairing (for information).** AGP 9.4.1 is newer than the AGP range Kotlin 2.4.21 lists as tested (up to 9.3.1). It builds cleanly. KSP 2.3.12 with Kotlin 2.4.21 gets proven when Room arrives in M3.

## M1 — Spikes
- **Status:** accepted (2026-11-07; claude-verified 2026-10-17, tag `m1-claude-verified`). A1 and A2 are decided (see Decided).
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
- **Left:** nothing. Copy-reviewer not needed: no strings.xml or content changes.
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
- **Human check results (2026-10-17, the human's Samsung; model and Android version not recorded):**
  1. **A1:** the card shows on the lock screen and a face tap saves while locked. Not reported: icons-only vs details, and silent vs alerting.
  2. **A2:** with the switch off, lock-screen taps got a location from last known, unlike the AOSP emulators. Not reported: outcome (cached or fresh), accuracy and age; the in-app fix time.
  3. **A3:** yes: unlocked, the ongoing card can be swiped away and a `dismiss` line appears.
  4. **A4:**
     - Reminders arrive.
     - During Do Not Disturb the alarm still fires (the readout lists the `reminder` line), but the notification gives no alert. That is DND doing its job; the spec doesn't ask SmileProc to break through DND.
     - Delay: not recorded.
- **Decided:**
  1. **A2 (human, 2026-10-17, after the phone checks): last known only.** No location foreground service.
     - This replaces the provisional choice of the location service made earlier the same day.
     - Nothing changes: the spec (android.md › Location: last known from a notification tap, else none) and the guards stay as they are; no Play declaration.
     - Caveat: on the AOSP emulators, last known gave none on every notification tap, so some phones (likely Pixels) may save notification taps without location. Not tested on a Pixel.
  2. **Default reminders every 4 hours (human, 2026-10-17):** for M5; see M5 › Decided before M5.
  3. **A1: standard collapsed lock-screen card accepted (human, 2026-11-07).** Reaching the faces with one expand gesture on the lock screen without unlocking is accepted; no custom collapsed layout spike needed.
- **Decisions needed:**
  1. Still open from M0: the Room line (before M3), Android 17 coverage, the Support emergency line (before M8).

## M2 — Core logic
- **Status:** claude-verified (2026-11-07, tag `m2-claude-verified`)
- **Decided before M2:**
  1. **Balance rule (human, 2026-11-07):** respect max one per family on the final card. When `limit >= 2`, at least 2 items were picked, and nothing picked has `direction == higher`, replace the last pick with the highest-scoring `higher` candidate with `pattern` confidence whose `family` is not among the remaining kept picks (`picked.dropLast(1)`).
- **Built:**
  - `:core` (`core/src/main/kotlin/com/example/smileproc/core/`, pure Kotlin/JVM, zero `android.*` / `androidx.*` imports):
    - `CanonicalJson` (`IsoInstantSerializer` enforcing millisecond `.000Z` UTC ISO-8601, strict `json`, `prettyJson`, `contentJson`), `Models` (`Mood` 1..3 int serializer, `EntrySource`, `LocationKind`, `Daypart`, `PlaceKind`, `LocationSample`, `Entry` with validation and entry-zone `localDate`/`isoWeekday`/`daypart`/`hasCore`, `PlaceLabel`), `LocationAcceptance`.
    - Content loaders: `PatternConfig` (all 32 keys from `content/pattern-config.json`, zero fallback defaults), `EmotionTags`, `PatternLibrary` (`LibraryMatchCriteria`, `LibraryEntry`, placeholder rendering), `SupportCatalog` (`verified` region filtering).
    - `Geo` (Haversine `R = 6,371,000 m`, arithmetic `centroid`), `PlaceResolver` (5-step deterministic clustering, single stabilise pass, lower-`ClusterID` tie-breaker, `findMergeCandidate`, `mergedAnchor`, `summarizeUnnamed`), `CardPeriod` (DST-safe 7/14/28-day calendar windows and `previousDueCardPeriod`).
    - `PatternModels` and `PatternEngine`: all 7 detectors (`timeOfDay`, `weekday`, `weekdayDaypart` excluding `night`, `place`, `awayFromHome`, `tagOnLow`, `tagAtPlace`), `sdFloor`, `fineTagDominance`, deterministic comparator rounding `score` to 9 decimals.
    - `LibraryMatcher`, `WeeklyCardBuilder` (`Variant`, `CardItem`, `WeeklyCard`, greedy one-per-family selection, early-hint slot filling, balance swap per decision 1, `dueCardPeriod`), `LowMoodMonitor` (last `lowRunCount` entries across `>= lowRunMinDistinctDays`, dismiss and non-Low re-arm), `CheckInMachine` (pure reducer across all states/events, `ID_CHECKIN = 1`, `NotificationCopyKey` mapping to `strings.xml`), `SyntheticDataGenerator`.
  - `:core` unit tests (`core/src/test/kotlin/com/example/smileproc/core/`, 43 tests across 15 test classes including M0's 3 contract suites): `ModelsAndCodecTest`, `LocationAcceptanceTest`, `ContentLoadersTest`, `PlaceResolverTest`, `CardPeriodTest`, `PatternEngineTest`, `LibraryMatcherTest`, `WeeklyCardBuilderTest`, `LowMoodMonitorTest`, `CheckInMachineTest`, `PatternFixturesTest`, `NoiseGuardTest`.
  - `core/build.gradle.kts`: forwards `WRITE_GOLDENS` to `smileproc.writeGoldens`.
  - `fixtures/patterns/*.golden.json`: 14 golden snapshots generated via `WRITE_GOLDENS=1 ./gradlew :core:test`.
- **Evidence:**
  - `scripts/check.sh`: `privacy-guard`, `fixtures`, `json`, `gradle` (`:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDogfood`), `dogfood-classpath`, `dogfood-permissions` and `dogfood-manifest` all PASS → `check.sh: PASS`.
  - `:core:test` covers every `:core` item in `docs/spec/testing.md` (except M9's `.smileproc` export crypto and `vector-1.json`) plus all five M0 `Not fixed (for M2)` rules:
    - All 14 `fixtures/patterns/*` expectations and golden snapshots (`afternoon-dip`, `low-week`, `sunday-evening`, `out-lifts`, `tired-on-low`, `stressed-at-work`, `first-week`, `not-enough-data`, `dst-week`, `travel`, `no-location`, `noise-1..3`), 5 `fixtures/places/*` scenarios (including `centroid-drift` and lower-`ClusterID` tie-breaking), and 10 `fixtures/periods/card-periods` cases.
    - Exhaustive `LibraryMatcherTest` across all 7 detectors × directions × dayparts/weekdays/placeKinds/42 tags; `ContentLoadersTest` mutates and omits every one of the 32 keys in `content/pattern-config.json`.
    - `PatternEngineTest` covers `weekdayMinDataDays` skip, `weekdayMinDistinctDates`, `weekdayDaypart` `night` exclusion, and 9-decimal score tie-breaking; `WeeklyCardBuilderTest` covers early hints filling free slots and the one-per-family balance swap.
    - `NoiseGuardTest`: 100 seeded random single weeks of 25 entries (`Variant.NORMAL`, `maxItems = 3`) → average `0.26` `pattern` items per card (`<= 0.3`).
    - `LowMoodMonitorTest`: 5 Low on one day → no card; across 2 days → card; dismiss stays off until a non-Low entry re-arms.
    - `CheckInMachineTest`: every spec transition row plus 1,000 × 50 seeded random event sequences verifying at most one `ID_CHECKIN` notification action per transition and only `strings.xml`'s four safe `NotificationCopyKey` strings.
  - `spec-reviewer` (`product.md`, `data.md`, `logic.md`, `testing.md`; range `m1-claude-verified..HEAD`): `"No gaps"`.
  - `copy-reviewer` (`m1-claude-verified..HEAD`): flagged hard-coded English notification string constants in `CheckInMachine`; fixed (`3dd648f`) by replacing them with `NotificationCopyKey` resource keys backed solely by `strings.xml`.
- **Left:** nothing for M2 beyond the human checks below.
- **Deviations:**
  1. Host SDK / JDK setup (machine-only, zero repo script edits): installed Android SDK `cmdline-tools/latest` (23.0.0) into `%LOCALAPPDATA%\Android\Sdk` for `apkanalyzer` and ran Gradle with OpenJDK 25 (`C:\Users\ITWORK\.jdks\openjdk-25.0.1`) since the system `JAVA_HOME` pointed to a missing JBR path.
  2. Added `AGENTS.md` and `.agents/` mirroring `.claude/` (committed on `main` per human decision on 2026-11-07) so Antigravity and Claude Code share identical rules, skills, and subagents.
- **Not run:** none.
- **Not fixed:** none.
- **Human checks:**
  1. Skim `fixtures/patterns/afternoon-dip.golden.json` and `fixtures/patterns/low-week.golden.json` and reply `"goldens accepted"` / `"M2 accepted"`.
- **Decisions needed:**
  1. **Multi-week weekday noise rate (optional, before M8):** On 100 seeded single weeks of 25 uniform random entries (`testing.md` › Noise guard), average `pattern` items per card is `0.26` (`<= 0.3`, passes). Across **4-week (100-entry) uniform random histories**, `weekday` (7 segments) + `weekdayDaypart` (21 segments) tests 28 segments at unadjusted `zPattern = 2.0`, yielding ~`0.55` weekday false positives per card (~`0.84` total across all families). `content/pattern-config.json` was left untouched; decide before M8 whether to keep `zPattern = 2.0` for weekday detectors or introduce a stricter weekday z-threshold.
  2. Still open from M0: the Room line (before M3), Android 17 coverage, and the Support emergency line (before M8).

## M3 — Storage and crypto
- **Status:** not started

## M4 — Check-in in the app
- **Status:** not started

## M5 — Check-in notification and reminders → dogfood build 1
- **Status:** not started
- **Decided before M5:**
  1. **Default reminders: 08:00, 12:00, 16:00 and 20:00, every day** (human, 2026-10-17, after the M1 phone checks: "every 4 hours").
     - The spec was updated on 2026-10-17:
       - product.md › What we're building;
       - screens.md › Onboarding (3): pre-filled and editable;
       - android.md › Reminders › Defaults;
       - testing.md: Robolectric and Compose UI lines.
     - How it works:
       - Onboarding step 3 shows the four times pre-filled.
       - They are stored, and the next one is scheduled, when onboarding is finished or skipped. That happens on first run, and again after Delete all, so skipping keeps them.
       - E16 stays as written: right after Delete all there are no alarms, because the app is back in onboarding.
       - Deleting every reminder leaves none, not the defaults.
     - **Copy (M5, copy-reviewer):** `onboarding_reminders_body` ("Pick the times you'd like a check-in…") changes to say the times are suggested and can be changed.

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
