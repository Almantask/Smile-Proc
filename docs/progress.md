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
  1. **Room line**, before M3 creates the schema: **decided 2026-11-07** — switch to Room 3 (`androidx.room3` `3.0.3`); see M3 › Decided before M3.
  2. **Android 17 coverage:** **decided 2026-11-07** — add an API 37 Google APIs AVD (`sp37`) alongside `sp36` and `sp29` to verify Android 17 behaviour.
  3. **Balance rule**, before M2 builds `WeeklyCardBuilder` (logic.md › Selection): **decided 2026-11-07** — see M2 › Decided before M2.
  4. **Support screen emergency line**, before M8: **decided 2026-11-07** — include a generic "If you're in danger right now, contact your local emergency number." line (naming no contacts); see M8 › Decided before M8.
  5. **Version pairing (for information).** AGP 9.4.1 is newer than the AGP range Kotlin 2.4.21 lists as tested (up to 9.3.1). It builds cleanly. KSP 2.3.12 with Kotlin 2.4.21 gets proven when Room 3 arrives in M3.

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
- **Decisions needed:** none (Room 3, Android 17 AVD, and Support emergency line all decided 2026-11-07).

## M2 — Core logic
- **Status:** accepted (2026-11-07; claude-verified 2026-11-07, tag `m2-claude-verified`)
- **Decided before M2:**
  1. **Balance rule (human, 2026-11-07):** respect max one per family on the final card. When `limit >= 2`, at least 2 items were picked, and nothing picked has `direction == higher`, replace the last pick with the highest-scoring `higher` candidate with `pattern` confidence whose `family` is not among the remaining kept picks (`picked.dropLast(1)`).
  2. **Stricter weekday z-threshold (`weekdayZPattern = 2.5`, human, 2026-11-07):** added `"weekdayZPattern": 2.5` to `content/pattern-config.json` (and `data.md` / `logic.md`) for `weekday` and `weekdayDaypart`, cutting 4-week uniform-noise weekday false positives from `56%` to `21%` and demoting the 3 spurious `pattern` items in `noise-2.golden.json` to `earlyHint` while keeping `sunday-evening` (`z = -6.44`) as `pattern`.
- **Built:**
  - `:core` (`core/src/main/kotlin/com/example/smileproc/core/`, pure Kotlin/JVM, zero `android.*` / `androidx.*` imports):
    - `CanonicalJson` (`IsoInstantSerializer` enforcing millisecond `.000Z` UTC ISO-8601, strict `json`, `prettyJson`, `contentJson`), `Models` (`Mood` 1..3 int serializer, `EntrySource`, `LocationKind`, `Daypart`, `PlaceKind`, `LocationSample`, `Entry` with validation and entry-zone `localDate`/`isoWeekday`/`daypart`/`hasCore`, `PlaceLabel`), `LocationAcceptance`.
    - Content loaders: `PatternConfig` (all 33 keys from `content/pattern-config.json` including `weekdayZPattern`, zero fallback defaults), `EmotionTags`, `PatternLibrary` (`LibraryMatchCriteria`, `LibraryEntry`, placeholder rendering), `SupportCatalog` (`verified` region filtering).
    - `Geo` (Haversine `R = 6,371,000 m`, arithmetic `centroid`), `PlaceResolver` (5-step deterministic clustering, single stabilise pass, lower-`ClusterID` tie-breaker, `findMergeCandidate`, `mergedAnchor`, `summarizeUnnamed`), `CardPeriod` (DST-safe 7/14/28-day calendar windows and `previousDueCardPeriod`).
    - `PatternModels` and `PatternEngine`: all 7 detectors (`timeOfDay`, `weekday`, `weekdayDaypart` excluding `night`, `place`, `awayFromHome`, `tagOnLow`, `tagAtPlace`), `sdFloor`, `weekdayZPattern`, `fineTagDominance`, deterministic comparator rounding `score` to 9 decimals.
    - `LibraryMatcher`, `WeeklyCardBuilder` (`Variant`, `CardItem`, `WeeklyCard`, greedy one-per-family selection, early-hint slot filling, balance swap per decision 1, `dueCardPeriod`), `LowMoodMonitor` (last `lowRunCount` entries across `>= lowRunMinDistinctDays`, dismiss and non-Low re-arm), `CheckInMachine` (pure reducer across all states/events, `ID_CHECKIN = 1`, `NotificationCopyKey` mapping to `strings.xml`), `SyntheticDataGenerator`.
  - `:core` unit tests (`core/src/test/kotlin/com/example/smileproc/core/`, 44 tests across 15 test classes including M0's 3 contract suites): `ModelsAndCodecTest`, `LocationAcceptanceTest`, `ContentLoadersTest`, `PlaceResolverTest`, `CardPeriodTest`, `PatternEngineTest`, `LibraryMatcherTest`, `WeeklyCardBuilderTest`, `LowMoodMonitorTest`, `CheckInMachineTest`, `PatternFixturesTest`, `NoiseGuardTest`.
  - `core/build.gradle.kts`: forwards `WRITE_GOLDENS` to `smileproc.writeGoldens`.
  - `fixtures/patterns/*.golden.json`: 14 golden snapshots generated via `WRITE_GOLDENS=1 ./gradlew :core:test`.
- **Evidence:**
  - `scripts/check.sh`: `privacy-guard`, `fixtures`, `json`, `gradle` (`:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDogfood`), `dogfood-classpath`, `dogfood-permissions` and `dogfood-manifest` all PASS → `check.sh: PASS`.
  - `:core:test` covers every `:core` item in `docs/spec/testing.md` (except M9's `.smileproc` export crypto and `vector-1.json`) plus all five M0 `Not fixed (for M2)` rules:
    - All 14 `fixtures/patterns/*` expectations and golden snapshots (`afternoon-dip`, `low-week`, `sunday-evening`, `out-lifts`, `tired-on-low`, `stressed-at-work`, `first-week`, `not-enough-data`, `dst-week`, `travel`, `no-location`, `noise-1..3`), 5 `fixtures/places/*` scenarios (including `centroid-drift` and lower-`ClusterID` tie-breaking), and 10 `fixtures/periods/card-periods` cases.
    - Exhaustive `LibraryMatcherTest` across all 7 detectors × directions × dayparts/weekdays/placeKinds/42 tags; `ContentLoadersTest` mutates and omits every one of the 33 keys in `content/pattern-config.json`.
    - `PatternEngineTest` covers `weekdayMinDataDays` skip, `weekdayMinDistinctDates`, `weekdayDaypart` `night` exclusion, `weekdayZPattern` demotion of borderline `2.0 <= |z| < 2.5` weekday candidates, and 9-decimal score tie-breaking; `WeeklyCardBuilderTest` covers early hints filling free slots and the one-per-family balance swap.
    - `NoiseGuardTest`: 100 seeded random single weeks of 25 entries (`Variant.NORMAL`, `maxItems = 3`) → average `0.26` `pattern` items per card (`<= 0.3`).
    - `LowMoodMonitorTest`: 5 Low on one day → no card; across 2 days → card; dismiss stays off until a non-Low entry re-arms.
    - `CheckInMachineTest`: every spec transition row plus 1,000 × 50 seeded random event sequences verifying at most one `ID_CHECKIN` notification action per transition and only `strings.xml`'s four safe `NotificationCopyKey` strings.
  - `spec-reviewer` (`product.md`, `data.md`, `logic.md`, `testing.md`; range `m1-claude-verified..HEAD`): `"No gaps"`.
  - `copy-reviewer` (`m1-claude-verified..HEAD`): flagged hard-coded English notification string constants in `CheckInMachine`; fixed (`3dd648f`) by replacing them with `NotificationCopyKey` resource keys backed solely by `strings.xml`.
- **Left:** nothing.
- **Deviations:**
  1. Host SDK / JDK setup (machine-only, zero repo script edits): installed Android SDK `cmdline-tools/latest` (23.0.0) into `%LOCALAPPDATA%\Android\Sdk` for `apkanalyzer` and ran Gradle with OpenJDK 25 (`C:\Users\ITWORK\.jdks\openjdk-25.0.1`) since the system `JAVA_HOME` pointed to a missing JBR path.
  2. Added `AGENTS.md` and `.agents/` mirroring `.claude/` (committed on `main` per human decision on 2026-11-07) so Antigravity and Claude Code share identical rules, skills, and subagents.
- **Not run:** none.
- **Not fixed:** none.
- **Human checks:** `afternoon-dip.golden.json` and `low-week.golden.json` accepted (2026-11-07).
- **Decisions needed:** none.

## M3 — Storage and crypto
- **Status:** accepted (2026-11-07; claude-verified 2026-11-07, tag `m3-claude-verified`)
- **Decided before M3:**
  1. **Room 3 (`androidx.room3` `3.0.3`, human, 2026-11-07):** switch `:app` to `androidx.room3` `3.0.3` (KSP Kotlin generation, coroutines-only DAOs, `AndroidSQLiteDriver` via `androidx.sqlite:sqlite-framework`, `room3` schema plugin exporting to `app/schemas/`). Keep `:spikes` on its existing M1 dependencies or migrate seamlessly without touching shipped code.
  2. **Android 17 AVD (`sp37`, human, 2026-11-07):** add an API 37 Google APIs AVD alongside `sp36` (API 36 Play-free AOSP) and `sp29` (API 29) for Android 17 verification.
- **Built:**
  - `:core`:
    - `Sealer` interface (`seal`, `open`, `sealUtf8`, `openUtf8`, `isKeyAvailable()`), `SealException` (`Reason`: `PAYLOAD_TOO_SHORT`, `KEY_UNAVAILABLE`, `AUTHENTICATION_OR_DECRYPT_FAILED`, `SEAL_FAILED`).
    - `JcaSealer` (pure-JVM AES-GCM-256 implementation, `TRANSFORMATION = "AES/GCM/NoPadding"`, `IV_BYTES = 12`, `TAG_BITS = 128`, validated row ID UTF-8 AAD).
    - `CanonicalJson`: weekly card canonical encoder and decoder helpers (`encodeWeeklyCard`, `decodeWeeklyCard`).
    - `SealerTest`: JVM unit tests for IV randomness, round-trip, AAD authentication check, ciphertext tampering rejection, payload truncation rejection.
  - `:app`:
    - Migrated `:app` to Room 3 (`androidx.room3` `3.0.3`, KSP `2.3.12`, `AndroidSQLiteDriver` via `androidx.sqlite:sqlite-framework`, `room3` gradle plugin).
    - Exported Room schema v1: `app/schemas/com.example.smileproc.data.db.SmileProcDatabase/1.json`.
    - Room entities (`EntryRow` with index on `createdAt`, `PlaceRow`, `WeeklyCardRow`).
    - DAOs (`EntryDao`, `PlaceDao`, `WeeklyCardDao`) with suspend queries and Flow streams.
    - `SmileProcDatabase` (`open(context, dbName, driver)`).
    - `ManagedSealer` interface + `KeystoreSealer` (`AndroidKeyStore` alias `smileproc-db-v1`, AES-256-GCM, no auth/unlock requirements, randomized IV, `ensureKeyExists()`, `deleteKeyIfPresent()`).
    - Sealed repositories (`EntryRepository`, `PlaceRepository`, `WeeklyCardRepository`) validating `row.id` against decrypted payload `id` and enforcing UTF-8 row ID AAD.
    - `AppContainer` and `SmileProcApplication` (`android:name=".SmileProcApplication"` in manifest) managing storage lifecycle (`StorageState`: `Loading`, `Ready`, `NeedsRecovery`). Detects existing DB with missing/unusable key, transitions to `NeedsRecovery` without auto-generating replacement keys or wiping data silently, handles coroutine cancellation gracefully, and implements `startFresh()`.
    - `RecoveryScreen` Composable (`R.string.recovery_title`, `recovery_body`, `recovery_import`, `recovery_start_fresh`, `recovery_start_fresh_confirm`, `action_cancel`) with accessible touch targets, 200% font scale scroll support, dark mode, and confirmation dialog for `startFresh`. Wired into `MainActivity`.
    - Backup & extraction rules: `allowBackup="false"`, `backup_rules.xml`, and `data_extraction_rules.xml` verified via `BackupRulesTest`.
    - Debug receivers: `ProbeReceiver` (exports non-sensitive counts `{"stub": false, "storageState": "ready", "entries": ..., "perMood": ..., "perSource": ..., "located": ...}` to `files/probe.json`), `DebugEventsReceiver` (`SEAL_TEST_ENTRY` action to seal and store entry while locked).
  - Scripts & AVDs:
    - Updated `scripts/emu/env.sh`, `scripts/emu/start.sh`, `scripts/emu/create-avds.sh`, and `scripts/emu/fire.sh` (`seal-test`).
    - Created `sp36` (API 36), `sp29` (API 29), and `sp37` (API 37.0 Android 17) Play-free AVDs.
    - Fixed Windows path conversion in `scripts/emu/install.sh` (`MSYS_NO_PATHCONV=0`).
  - Tests:
    - `:core:test`: `SealerTest`.
    - `:app:testDebugUnitTest`: `RoomRepositoriesTest`, `RecoveryStateTest`, `RecoveryScreenTest` (Compose UI), `BackupRulesTest`.
    - `:app:connectedDebugAndroidTest`: `KeystoreSealerAndroidTest` on `sp36`.
- **Evidence:**
  - `scripts/check.sh`: all 7 gates PASS (`privacy-guard`, `fixtures`, `json`, `gradle`, `dogfood-classpath`, `dogfood-permissions`, `dogfood-manifest`).
  - Instrumented test: `KeystoreSealerAndroidTest` passed on `sp36` (`AVD sp36 - 16`).
  - Emulator scenario `M3-locked-seal` passed via `emulator-tester-runner`:
    - Fresh debug install launched unlocked (`deviceLocked=0`, `m3-app-ready.png`), initial probe: `entries: 0`, `storageState: ready`.
    - Locked device (`deviceLocked=1`, `m3-locked-before-seal.png`).
    - Fired `fire.sh seal-test` twice while locked (`deviceLocked=1` maintained throughout); `probe.sh` incremented to `entries: 1` then `entries: 2` (`perMood.okay: 2`, `perSource.notification: 2`).
    - Cold process test while locked (`am force-stop`, then `fire.sh seal-test`) succeeded (`entries: 3`).
    - `logcat -d -s SmileProc:*` showed only fixed `SafeLog.Event` names (`PROBE_WRITTEN`, `DEBUG_SEAL_SAVED`), zero sensitive data.
  - Subagent reviews:
    - `copy-reviewer`: "No copy issues".
    - `spec-reviewer`: Two error-handling findings in `AppContainer` (rethrowing exception on `startFresh` and handling `CancellationException` in `initialize`) were resolved and verified with unit tests in commit `b80d415`.
- **Left:** nothing.
- **Deviations:**
  1. Room 3 (`androidx.room3` `3.0.3`): Used `AndroidSQLiteDriver` from `androidx.sqlite:sqlite-framework` instead of `sqlite-bundled` (which lacks Windows DLLs for Robolectric).
  2. Robolectric OpenJDK 25 compatibility: Added `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED` and `--add-opens=java.base/java.io=ALL-UNNAMED` to `testOptions.unitTests.all`.
  3. Espresso Core `3.7.0`: Added explicit test dependency in `:app` for Android 16/17 Robolectric input manager compatibility.
  4. Android 17 Play-free AVD (`sp37`): Created using `system-images;android-37.0;default;x86_64` alongside `sp36` and `sp29`.
- **Not run:** none.
- **Not fixed:** none.
- **Human checks:** none.
- **Decisions needed:** none.

## M4 — Check-in in the app
- **Status:** claude-verified (2026-11-07, tag `m4-claude-verified`)
- **Built:**
  - `FaceView` (`components/FaceView.kt`): 3 large custom-drawn accessible faces (Low, Okay, Good) with accessible content descriptions, light haptic feedback on tap, >= 48dp touch targets, and high contrast styling in light and dark themes. Supports selectable state for editing.
  - `TodayScreen` (`today/TodayScreen.kt`): Single tap saves immediately (`source = EntrySource.APP`, `location = null`), starts async GPS fix (`LocationClient.captureInAppFix()`), and opens `FollowUpSheet`. Lists today's entries with timestamp, mood face, tags, note, and place name.
  - `FollowUpSheet` (`today/FollowUpSheet.kt`): "Saved" header, `GradualTagPicker` (Step 1: 7 core chips; Step 2: 5 finer chips + "Just {core}"; max 3 tags), note field (up to 1,000 characters), "Done" action, saves details on dismiss/done without clobbering background location fix.
  - `LowRunCard` (`today/LowRunCard.kt`): Evaluated via `LowMoodMonitor` (>= 5 Low check-ins over >= 2 days), displays gentle supportive copy, "See support options" button (opens `SupportDialog`), and dismissible "Not now" button.
  - `HistoryScreen` & `EntryDetailSheet` (`history/`): All check-ins grouped by day (Today, Yesterday, date); displays time, mood face, tags, place name, and note snippet. `EntryDetailSheet` allows editing mood, tags, and note, naming unnamed places (`PlaceLabel`), and deleting check-in with confirmation dialog.
  - `SettingsScreen` & `SettingsRepository` (`settings/`, `data/SettingsRepository.kt`): Capture location toggle, Tag picker mode toggle, App lock toggle placeholder, Reminders placeholder, Privacy explanation, About dialog, and Delete all data dialog placeholder.
  - `OnboardingScreen` (`onboarding/OnboardingScreen.kt`): 2-step onboarding covering privacy promise (not in backups, export to keep copy) and location permission (skippable).
  - `PlatformLocationClient` (`data/PlatformLocationClient.kt`): Fresh GPS fix (<= 100m or best fix at 10s timeout <= 200m) or cached fix (<= 10 min, <= 200m). Respects runtime permissions and user settings.
  - Vector drawables: `ic_arrow_back.xml`, `ic_close.xml`, `ic_mode_switch.xml`, `ic_tab_today.xml`, `ic_tab_week.xml`, `ic_tab_settings.xml`.
  - Compose UI and Robolectric Unit Tests: `TodayScreenTest`, `LowRunCardTest`, `HistoryScreenTest`, `OnboardingSettingsTest`, `PlatformLocationClientTest`.
- **Evidence:**
  - `scripts/check.sh`: PASS (all 7 gates: privacy-guard, fixtures, JSON check, Gradle tests & lint, dogfood classpath, permissions, and manifest).
  - Test suites: `:core:test` 100% pass; `:app:testDebugUnitTest` 18 tests pass.
  - Emulator scenarios (API 36 `sp36` / `emulator-5554`):
    - **E10 (In-app tap with GPS fix):** `PASS`. Set geo to Milan `45.4642 9.19`. Good face tapped -> immediate save -> 10s async fix -> `probe.sh` reported `entries: 1, located: 1`. Captured screenshots: `e10-sheet-open.png`, `e10-today-with-entry.png`, `e10-located-entry.png`, `e10-today-list.png`.
    - **E12 (In-app location revoked):** `PASS`. `pm revoke ... ACCESS_FINE_LOCATION`. Okay face tapped -> `probe.sh` reported `entries: 2, located: 1` (`located` count unchanged). Captured screenshot: `e12-revoked-saved.png`.
    - **E17 (Font scale 2.0x):** `PASS`. `settings put system font_scale 2.0`. Captured screenshots across Today, FollowUpSheet, Week, Settings, and History (`e17-today-200.png`, `e17-sheet-200.png`, `e17-settings-200.png`, `e17-history-200.png`, `e17-week-200.png`). Verified clean layouts with no clipping or overlapping controls.
    - **E18 (Night mode):** `PASS`. `cmd uimode night yes`. Captured screenshots (`e18-today-night.png`, `e18-sheet-night.png`, `e18-history-night.png`). Verified dark surfaces, high contrast outlines, and clear readability.
  - Subagent reviews:
    - `spec-reviewer`: Verified against `docs/spec/` (screens.md, data.md, product.md). Addressed string resource extractions, selected states on `FaceButton`, and test coverage.
    - `copy-reviewer`: Verified against copy guidelines and non-negotiables. Verified zero clinical language, zero guilt/scores/streaks, no exclamation marks on low mood screens, proper support emergency notice, and resource-composed text.
- **Left:** nothing.
- **Deviations:** none.
- **Not run:** none.
- **Not fixed:** none.
- **Human checks:**
  - H2: Real GPS fresh fix in the app within ~10 s (can be tested on phone anytime; verified on emulator in E10).
- **Decisions needed:** none.

## M5 — Check-in notification and reminders → dogfood build 1
- **Status:** accepted (2026-11-07; claude-verified 2026-11-07, tag `m5-claude-verified`)
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
- **Built:**
  - `CheckInNotifier` (`app/src/main/java/com/example/smileproc/notify/CheckInNotifier.kt`): Notification channel `checkin`, single notification invariant (`ID_CHECKIN = 1`), strictly approved safe strings ("How are you right now?", "Saved ✓", "It works ✓", "Couldn't save. Open SmileProc."), lock screen visibility `VISIBILITY_PUBLIC`, ongoing control, 3 action buttons (Low, Okay, Good) via pending intents to unexported `CheckInReceiver`. 60s restore timer scheduling via `AlarmManager.set(ELAPSED_REALTIME, ...)` and 12s test check-in timer.
  - `CheckInReceiver` (`notify/CheckInReceiver.kt`): Unexported broadcast receiver saving ratings immediately while locked/unlocked without requiring device unlock (`source = EntrySource.NOTIFICATION`, `location = lastKnownLocation`). Dispatches machine transitions (`Tap`, `RestoreTimer`, `Dismissed`, `TestTimerFired`, `Booted`, `AppUpdated`).
  - `ReminderScheduler` (`notify/ReminderScheduler.kt`): Computes next reminder instant across up to 6 configured reminders in device's current zone. Schedules via inexact `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, ...)` with invariant of at most 1 pending reminder alarm. Cancels alarms when onboarding not completed or reminders empty.
  - `ReminderReceiver` (`notify/ReminderReceiver.kt`): Unexported receiver woken up by AlarmManager, handles `ACTION_REMINDER_FIRED`, cancels dismiss state, posts alerting check-in notification, and schedules the next reminder.
  - `SystemEventsReceiver` (`notify/SystemEventsReceiver.kt`): Exported receiver (the only exported receiver in dogfood APK), listens for `ACTION_BOOT_COMPLETED`, `ACTION_MY_PACKAGE_REPLACED`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`. Guarded by `onboardingCompleted` check, transitions machine state on boot/update, and reschedules reminder alarms.
  - `RemindersScreen` (`ui/settings/RemindersScreen.kt`): Lists up to 6 reminders, adds reminders with time picker and weekday selection, deletes reminders, validates unique times and non-empty weekdays.
  - `LockScreenCheckInScreen` (`ui/settings/LockScreenCheckInScreen.kt`): Standing notification toggle, readiness checks (notifications enabled, lock-screen visibility, channel importance) with direct problem rows and settings links, and "Send a test check-in" flow with 12s delay countdown.
  - Updated `SettingsScreen` (`ui/settings/SettingsScreen.kt`) and `OnboardingScreen` (`ui/onboarding/OnboardingScreen.kt`): Wired Step 3 (reminders pre-filled with 4 defaults) and Step 5 (lock-screen readiness and standing notification choice), with defaults stored and alarms scheduled on completion.
  - Vector drawables: `ic_face.xml`.
- **Evidence:**
  - `scripts/check.sh`: PASS (all 7 gates: privacy-guard, fixtures, json, gradle, dogfood-classpath, dogfood-permissions, dogfood-manifest).
  - Tests:
    - `:core:test`: 100% PASS (CheckInMachineTest, ModelsAndCodecTest, SealerTest, etc.).
    - `:app:testDebugUnitTest`: 23 tests PASS (CheckInNotifierTest, CheckInReceiverTest, ReminderSchedulerTest, SystemEventsReceiverTest, RemindersAndLockScreenTest, TodayScreenTest, LowRunCardTest, HistoryScreenTest, OnboardingAndSettingsTest, RecoveryScreenTest, RecoveryStateTest, RoomRepositoriesTest, PlatformLocationClientTest, BackupRulesTest).
  - Live Emulator Scenarios (all executed and verified with screenshots):
    - **E1:** Standing notification on lock screen; tap Okay saves immediately while locked without unlock; "Saved ✓" shown; +1 entry verified (`build/emu-shots/e1-lock-okay.png`).
    - **E2:** 60s restore timer returns to silent resting faces (`fire.sh restore`).
    - **E3:** Never more than one SmileProc notification across rapid triggers.
    - **E4:** API 34+ swipe-away while unlocked sets `dismissed: true`; next reminder restores notification and clears `dismissed`.
    - **E5:** Locked swipe cannot dismiss ongoing standing notification.
    - **E6:** Reboot: no notification before first unlock; standing notification restored and next reminder alarm scheduled after unlock.
    - **E7:** Standing off: reminder posts non-ongoing notification -> tap Good -> "Saved ✓" -> restore timer cancels/hides notification.
    - **E8:** API 33+ revoke `POST_NOTIFICATIONS` shows problem row with fix action on Lock-screen check-in screen; re-granting refreshes to `✓` (`build/emu-shots/e8-revoked.png`, `e8-granted.png`).
    - **E9:** "Send a test check-in" -> lock -> wait 12s -> tap Good -> "It works ✓" shown in notification and on screen; entry count unchanged (`build/emu-shots/e9-test-works.png`).
    - **E11:** Lock-screen tap with 1-min-old fix -> saved immediately without delay; location recorded.
    - **E12:** Location permission revoked: app and lock-screen taps both save immediately, located count unchanged.
    - **E13:** Reminder scheduled under forced idle / Doze (`idle.sh on`); alarm wakes device and fires out of Doze (~120s delay recorded).
    - **E19:** API 29 (`sp29`) verification: E1 (lock tap save) and E3 (single notification invariant) passed on Android 10 (`build/emu-shots/e19-sp29-lock-okay.png`).
  - Subagent reviews:
    - `copy-reviewer`: 0 issues found across all strings, notification copy, and dialogs.
    - `spec-reviewer`: Addressed all 7 review items with complete test coverage.
- **Left:** nothing.
- **Deviations:** none.
- **Not run:** none.
- **Not fixed:** none.
- **Human checks:**
  - H1: Lock screen notification & tap on real phone (Samsung/Pixel).
  - H3: Overnight reminder delivery delay.
  - H8: Daily use of dogfood build.
- **Decisions needed:** none.

## M6 — Detailed emotion wheel
- **Status:** accepted (2026-11-08; claude-verified 2026-11-08, tag `m6-claude-verified`)
- **Built:**
  - `EmotionWheel` (`app/src/main/java/com/example/smileproc/ui/tags/EmotionWheel.kt`):
    - Two concentric rings: inner core ring (7 wedges: Happy, Calm, Sad, Anxious, Angry, Tired, Surprised) and outer fine ring (35 fine wedges, 5 per core).
    - Custom Compose Canvas drawing with pastel palette per core family (Happy amber, Calm green, Sad blue, Anxious purple, Angry coral, Tired sand, Surprised sky).
    - Outer text ray-aligned along radial angle, flipped right-side up on bottom/left hemisphere (`angle > 90 && angle < 270`).
    - Polar tap hit detection (`EmotionWheelMath`): converts `(x, y)` to polar coordinates `(r, θ)`, accurately disambiguating inner core vs outer fine wedges, with null outside wheel boundary or inside center hole.
    - Wedge selection highlight with primary border and dot indicator. Max 3 tags enforced.
  - `EmotionListPicker` (`app/src/main/java/com/example/smileproc/ui/tags/EmotionListPicker.kt`):
    - Accessible list picker grouping tags by core emotion with minimum 48.dp touch targets (`heightIn(min = 48.dp)`).
    - Automatically used when TalkBack/touch exploration is active, at large font scales (>= 1.5x), or when manually toggled by user.
  - `TagPicker` (`app/src/main/java/com/example/smileproc/ui/tags/TagPicker.kt`):
    - Unified tag picker replacing `GradualTagPicker` while keeping backward compatibility wrapper.
    - Supports `TagPickerMode.GRADUAL` and `TagPickerMode.DETAILED` with mode toggle button.
    - Pinned selection chip echo at top: selected tags appear as `InputChip`s with remove action (`R.string.tags_remove_cd`).
    - Both modes produce identical canonical tag IDs (e.g. `calm.peaceful`).
  - Integration:
    - Wired into `FollowUpSheet` (Today) and `EntryDetailSheet` (History).
    - Persisted picker mode in `SettingsRepository.tagPickerMode`.
- **Evidence:**
  - `scripts/check.sh`: PASS (all 7 gates: privacy-guard, fixtures, json, gradle, dogfood-classpath, dogfood-permissions, dogfood-manifest).
  - Tests:
    - `:core:test`: 100% PASS.
    - `:app:testDebugUnitTest`: 24 tests PASS, including 7 tests in `TagPickerTest`:
      - `emotionWheelMath_resolvesAllCoresAndFineTagsAccurately`: verifies all 7 cores and 35 fine tags hit-tested correctly across polar coordinates.
      - `tagPicker_limitsSelectionToThreeTags`: verifies maximum 3 tags limit.
      - `emotionListPicker_selectionLimitsAndToggles`: verifies list picker toggle and selection limit.
      - `tagPicker_gradualMode_navigatesCoresAndFinerTags`: verifies 2-step navigation and input chip removal.
      - `tagPicker_detailedMode_switchesToEmotionListPickerAtLargeFontScale`: verifies automatic fallback to accessible list picker at 2.0x font scale without nested scroll crashes.
      - `tagPicker_gradualMode_producesCanonicalTagId` and `emotionListPicker_producesCanonicalTagId`: verifies both pickers output matching canonical tag IDs.
      - `tagPicker_modeToggle_invokesCallback`: verifies mode switch button invocation.
  - Live Emulator Scenarios (`sp36`):
    - **E17 (2.0x font scale):**
      - Screen font scale set to 2.0.
      - Sheet opens with accessible `EmotionListPicker` cleanly rendered without clipping or nested scroll crash.
      - Tapping tag pins selection chip at top.
      - Captured screenshots: `build/emu-shots/m6-e17-today.png`, `m6-e17-followup-sheet.png`, `m6-e17-followup-sheet-selected.png`, `m6-e17-followup-sheet-scrolled.png`, `m6-e17-week.png`, `m6-e17-settings.png`.
    - **E18 (Dark theme):**
      - Night mode enabled (`cmd uimode night yes`).
      - Wheel renders with high contrast against dark theme background; wedge highlights and chip selections verified.
      - Captured screenshots: `build/emu-shots/m6-e18-today.png`, `m6-e18-wheel.png`, `m6-e18-wheel-selected.png`.
  - Subagent Reviews:
    - `copy-reviewer`: "No copy issues".
    - `spec-reviewer`: "No gaps" (verified mode toggle test).
- **Left:** nothing.
- **Deviations:** none.
- **Not run:** H4 (TalkBack on real phone, human check).
- **Not fixed:** none.
- **Decisions needed:** none.

## M7 — Places UI
- **Status:** not started

## M8 — Weekly card and support
- **Status:** not started
- **Decided before M8:**
  1. **Support screen generic emergency line (human, 2026-11-07):** include a generic `"If you're in danger right now, contact your local emergency number."` line on the Support screen (naming no phone numbers or contacts; `screens.md` › Support updated; add string to `strings.xml` and run `copy-reviewer` in M8).

## M9 — Privacy and security
- **Status:** not started

## M10 — Polish and release prep
- **Status:** not started
