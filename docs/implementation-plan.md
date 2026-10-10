# SmileProc — Implementation Plan (iOS + Android v1)

> **For:** an AI coding agent (Grok 4.7 in Cursor) working with a human who builds and tests on real phones.
> **Sources:** `claude/idea-panel-reviews.md` and `claude/design-decisions.md` (both 2026-10-10). Where they differ, **design-decisions wins**.
> **Approach:** two native apps (Swift/SwiftUI and Kotlin/Jetpack Compose) that share one `spec/` folder: content, thresholds, data formats, UI strings and conformance test fixtures. Why: see §4.

---

## 0. How to work through this plan

1. Read sections 1–10 once before writing code. Sections **2 (non-negotiables)**, **6 (shared spec)** and **7 (algorithms)** are binding on both platforms.
2. Work **one milestone at a time** (§11). From M2 on, each milestone has an iOS part and an Android part: do the **lead platform** first (set in `AGENTS.md`, see Appendix B), stop for review, then the other platform, stop again. Don't start the next part unprompted.
3. At the end of every part: run the checks for that platform (§5.3), update `docs/progress.md` (built / left / deviations), and summarise for the human.
4. Anything you can't verify from Cursor (lock screen, data protection, biometrics, real GPS, notifications while locked) is marked **[device]**. Write the code, add the step to `docs/device-test-checklist.md`, and tell the human exactly what to test.
5. If a platform API doesn't behave as this plan assumes, stop and write it up in `docs/spikes.md`. **Never work around a non-negotiable.**
6. **Port, don't redesign.** The second platform mirrors the first: same type names, same file split, same behaviour. Any behaviour change goes into `spec/` and both platforms in the same milestone.
7. Don't invent content the plan marks as human-owned (helpline numbers, legal/privacy text).
8. Product question → ask. Implementation detail → pick the simplest option that passes the tests and note it in `docs/progress.md`.

---

## 1. What we're building

SmileProc is a private mood check-in app for iPhone and Android.

- **3-face scale:** 1 = Low, 2 = Okay, 3 = Good. Every face is a valid answer.
- **One-tap logging** from wherever is fastest on each phone: lock-screen widget (iPhone), reminder notification buttons (both — the main lock-screen path on Android), home-screen widgets (both), or the app. **The tap saves immediately**; emotion tags and a comment are an optional follow-up, never a step before saving.
- **Location:** raw GPS on every entry (when permitted). Entries within ~100 m group into one place; naming a place names the whole cluster.
- **Emotion tags:** *Gradual* mode (core feeling → finer one) by default, *Detailed* mode (full wheel); the app remembers the last mode. Plain-list version for VoiceOver/TalkBack.
- **Weekly card (the heart of the app):** 2–3 patterns from time of day, weekday, place and tags, each with a "many people notice…" line and one small tip from a hand-written library shipped in the app. On-device rules only. No AI, no cloud.
- **Check-in schedule:** user-set reminder times (not a day planner).
- **Private:** no account, no server, no analytics. Encrypted at rest. Biometric lock. Encrypted export that also moves history **between** iPhone and Android.

---

## 2. Non-negotiables (both platforms)

1. **No network.** iOS: no `URLSession`, `URLRequest`, `NWConnection`, `WKWebView`, `CLGeocoder`, `MapKit`. Android: **no `INTERNET` permission in the merged manifest**, no `HttpURLConnection`/`java.net.URL` fetching, OkHttp, Retrofit, Ktor, `WebView`, `Geocoder`, Maps. Only exception: the user tapping a helpline number or link hands off to the phone/browser app.
2. **Dependency allowlist only** (§5.2). No analytics, crash reporting, ads, Google Play Services or Firebase.
3. **Lock screen, widgets and notifications never show mood data** — no last mood, average, count or streak. Notification text is always generic.
4. **Single tap saves.** Nothing required between the tap and the save.
5. **Encrypted at rest:** every stored payload is AES-256-GCM sealed with a hardware-backed key (iOS Keychain, Android Keystore), on top of the OS file encryption.
6. **No streaks**, no "you missed a check-in", no scores, levels or badges, no guilt copy.
7. **Observations, not diagnoses.** Never use clinical words (depression, disorder, symptom, diagnosis, treatment) in user-facing copy. Low-mood support is gentle, dismissible, and **never sent as a notification**.
8. **No logging of user content.** Never log moods, tags, comments, coordinates or place names, even in debug builds.
9. **Honest privacy copy:** history lives only on this phone, is excluded from backups and phone-to-phone transfer, and is lost with the phone unless the user exports it.
10. **Same behaviour on both platforms.** Pattern results for the same data must match the shared fixtures (§6.7).

Put this section verbatim into `AGENTS.md` at the repo root (M0) so it's always in context.

---

## 3. Scope

**In v1 (both platforms)**
- App: Today (check-in), History, Week (weekly cards), Places, Settings, Onboarding.
- Widgets with three face buttons: iPhone lock screen + home screen; Android home screen (also shown on the Android lock-screen widget area on phones that have it).
- Reminder notifications with three face buttons that save without unlocking.
- Location capture, place clustering and naming.
- Gradual + detailed emotion tagging, optional comment, edit/delete entries.
- Pattern engine, pattern library, weekly card, weekly "your week is ready" notification.
- Low-week card variant, low-run support card, helplines screen.
- Biometric/passcode app lock, app-switcher privacy, encrypted export/import (cross-platform), delete-all.
- English UI from one shared strings file, ready for Italian later.

**Out of v1 (do not build)**
- Reflection mode — deferred until the weekly card has been proven in a one-week self-trial (panel call).
- Sync, accounts, cloud; maps and reverse geocoding.
- iOS "Always" location and Android background location / location foreground service — only if the human approves after spikes S2/A2.
- Apple Watch, Wear OS, Siri/Shortcuts, Android Quick Settings tile, Health Connect/HealthKit, tablet-specific layouts, iOS Control Center controls.
- Skipping a reminder because the user logged recently.

---

## 4. Why two native apps with a shared spec

Most of what makes SmileProc work is platform-specific anyway: lock-screen widgets, writing while the phone is locked, notification actions, Keychain/Keystore, location permissions, biometric lock. A shared-code framework (Kotlin Multiplatform, Flutter, React Native) would still need native code for all of that, plus a bridge, a second build system inside Xcode, and slower agent build loops.

What must be identical on both platforms is the *logic and content*: thresholds, clustering, pattern rules, tip library, data formats, copy. That lives once in `spec/`:

- **Content and thresholds** are JSON files both apps load at runtime — tuning a threshold or a tip is one edit.
- **UI strings** are one `en.json` that a script turns into `Localizable.xcstrings` and `strings.xml`.
- **Conformance fixtures** (input data + expected results) run in both test suites, so the Swift and Kotlin engines can't drift.
- **The export format** is shared and test-vectored, so a user can move from iPhone to Android and back.

The pure logic is mirrored file-for-file in Swift (`SmileCore`) and Kotlin (`:core`). If you later want one codebase for it, `:core` is already the natural seed for a Kotlin Multiplatform module.

---

## 5. Repository, tooling and dependencies

### 5.1 Layout
```
smileproc/
  AGENTS.md                     # §2 + §5.4 + lead platform
  README.md
  spec/
    content/                    # loaded at runtime by both apps
      emotion-tags.json
      pattern-library.en.json
      pattern-config.json
      helplines.json
    strings/en.json             # every UI string (source of truth)
    fixtures/
      patterns/<name>.input.json, <name>.expect.json, <name>.golden.json
      places/<name>.input.json, <name>.expect.json
      periods/periods.json
      export/vector-1.json      # produced in M9 by the lead platform
      inbox/*.json              # iOS inbox file samples
    tools/
      generate_fixtures.py      # stdlib-only, deterministic; --check mode
      gen_strings.py            # en.json → xcstrings + strings.xml
  ios/
    project.yml                 # XcodeGen
    App/  Widgets/  Shared/     # see §8
    SmileCore/                  # Swift package: pure logic + storage + crypto
  android/
    settings.gradle.kts  build.gradle.kts  gradle/libs.versions.toml
    core/                       # pure Kotlin/JVM: models, logic, export crypto
    app/                        # Compose UI, Room, Keystore, location, notifications, Glance widget
  scripts/
    test-ios.sh  test-android.sh  privacy-guard.sh  check-spec.sh
  docs/
    progress.md  spikes.md  device-test-checklist.md
```

### 5.2 Stack and dependency allowlist
| | iOS | Android |
|---|---|---|
| Language | Swift 6 language mode, strict concurrency | Kotlin 2.x, JVM toolchain 17 |
| UI | SwiftUI + Observation (`@Observable`) | Jetpack Compose + Material 3, Navigation Compose |
| Min OS | iOS 17.0 (interactive widget buttons) | minSdk 29 (Android 10); compile/target = latest stable |
| Storage | GRDB.swift 7.x | Room (KSP), schema export committed |
| Crypto | CryptoKit, Security, CommonCrypto | Android Keystore, `javax.crypto` |
| Widgets | WidgetKit + AppIntents | Jetpack Glance |
| Settings | `UserDefaults` | DataStore Preferences |
| Location | CoreLocation | platform `LocationManager` via `LocationManagerCompat` (no Play Services) |
| Biometrics | LocalAuthentication | androidx.biometric |
| JSON | `Codable` | kotlinx.serialization |
| Project | XcodeGen (`project.yml` checked in, `.xcodeproj` git-ignored) | Gradle Kotlin DSL + version catalog |
| Tests | `swift test` (SmileCore, runs on macOS), XCUITest | JUnit (`:core` on JVM), Robolectric + Room testing, Compose UI test |

**Allowed third-party code:** iOS — GRDB only. Android — AndroidX (core, activity, lifecycle, navigation, compose BOM, room, glance, biometric, datastore), kotlinx-coroutines, kotlinx-serialization; test-only: JUnit, kotlinx-coroutines-test, Robolectric, AndroidX test libraries. Anything else needs the human's approval.

Pin versions at M0 to the latest stable releases and record them in `libs.versions.toml` / `project.yml`. iOS work needs a Mac with Xcode; Android work runs anywhere.

**Identifiers (placeholders — the human sets final values):** iOS `com.example.smileproc`, widget `com.example.smileproc.widgets`, App Group `group.com.example.smileproc`; Android `applicationId com.example.smileproc`; deep link `smileproc://`.

### 5.3 Checks the agent runs
```bash
scripts/check-spec.sh      # fixtures match generator (--check), strings regenerated, JSON valid
scripts/privacy-guard.sh   # both platforms, see M0
scripts/test-ios.sh        # swift test --package-path ios/SmileCore; xcodegen generate; xcodebuild build
scripts/test-android.sh    # cd android && ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```
UI tests: pick an iOS simulator from `xcrun simctl list devices available`; Android Compose UI tests on any running emulator (`./gradlew :app:connectedDebugAndroidTest`).

### 5.4 Conventions (also go into `AGENTS.md`)
- **Same names on both platforms** for models and logic types (`Entry`, `PlaceResolver`, `PatternEngine`, `WeeklyCardBuilder`…). One logic file in Swift ↔ one file in Kotlin.
- Inject the clock, time zone/calendar, random generators and the sealer; never read the system clock inside core logic.
- Core logic (`SmileCore`, `:core`) has no UI, widget, location or notification imports.
- iOS: `@Observable` view models, `async/await`. Android: `ViewModel` + `StateFlow`, coroutines. No singletons except the app container (`AppEnvironment` / `AppContainer`).
- No force unwraps / `try!` / `!!` outside tests.
- Every user-facing string comes from `spec/strings/en.json`; generated string files are never edited by hand.
- Every tappable element has an accessibility label; support Dynamic Type / font scaling up to the largest sizes, and reduced motion.

---

## 6. Shared spec

### 6.1 Canonical data format
Used for export files, fixtures, the iOS inbox and the sealed database payloads on both platforms.

```json
{
  "id": "3f0c2b9e-7d1a-4a52-9a8e-0c6f1d2e4b10",
  "createdAt": "2026-10-12T13:05:00.000Z",
  "updatedAt": "2026-10-12T13:06:10.000Z",
  "timeZoneID": "Europe/Rome",
  "mood": 2,
  "tags": ["anxious.stressed"],
  "comment": null,
  "location": {
    "latitude": 45.4642, "longitude": 9.1900, "horizontalAccuracy": 12.0,
    "capturedAt": "2026-10-12T13:05:03.000Z", "kind": "fresh"
  },
  "source": "lockScreenWidget"
}
```
```json
{ "id": "…", "name": "Office", "kind": "work", "anchorLatitude": 45.48, "anchorLongitude": 9.21, "createdAt": "…" }
```
**Rules (both platforms)**
- UUIDs are **lower-case** (Swift's `uuidString` is upper-case — lower-case it).
- Timestamps are UTC ISO 8601 with milliseconds and `Z`.
- **Weekdays use ISO numbering everywhere in spec, settings and fixtures: 1 = Monday … 7 = Sunday.** Swift `Calendar` uses 1 = Sunday — convert with `iso = ((calendarWeekday + 5) % 7) + 1`. Kotlin `DayOfWeek.value` is already ISO.
- `mood` 1–3; `tags` max 3, ordered, ids from `emotion-tags.json`; `comment` trimmed, `null` if empty, max 1000 chars.
- `location.kind`: `fresh` | `cached`. `source`: `app` | `lockScreenWidget` | `homeScreenWidget` | `notification`.
- `timeZoneID` is the IANA zone where the entry was made. Daypart, weekday and "calendar date" for an entry are always computed in **its own** zone (travel, and DST — Europe's clocks go back on 25 Oct 2026).
- Place label `kind`: `home` | `work` | `other`.

### 6.2 Shared type names
`Mood`, `EntrySource`, `LocationSample`, `Entry`, `PlaceLabel`, `ClusterID`, `PlaceAssignment`, `PatternConfig`, `Detector`, `Family`, `Direction`, `Confidence`, `DetectedPattern`, `CardItem`, `WeeklyCard`, `Variant`, `WindowKind`, `PatternLibrary`, `LibraryMatcher`, `PlaceResolver`, `PatternEngine`, `WeeklyCardBuilder`, `LowMoodMonitor`, `CardPeriod`, `Sealer`, `ExportFormat`.

```swift
// Swift (SmileCore)
public struct Entry: Identifiable, Codable, Equatable, Sendable {
    public let id: UUID; public var createdAt: Date; public var updatedAt: Date
    public var timeZoneID: String; public var mood: Mood; public var tags: [String]
    public var comment: String?; public var location: LocationSample?; public var source: EntrySource
}
```
```kotlin
// Kotlin (:core)
@Serializable data class Entry(
    val id: String, val createdAt: Instant, val updatedAt: Instant, val timeZoneID: String,
    val mood: Mood, val tags: List<String>, val comment: String?, val location: LocationSample?, val source: EntrySource
)
```

### 6.3 `emotion-tags.json` (ids and colours; labels live in `spec/strings/en.json` as `tag.<id>`)
| Core | Fine tags |
|---|---|
| `happy` | joyful, grateful, proud, hopeful, excited |
| `calm` | relaxed, content, peaceful, focused, safe |
| `sad` | down, lonely, disappointed, hurt, nostalgic |
| `anxious` | stressed, worried, overwhelmed, nervous, insecure |
| `angry` | frustrated, irritated, resentful, jealous, impatient |
| `tired` | drained, sleepy, bored, unmotivated, numb |
| `surprised` | amazed, confused, curious, startled, moved |

Fine ids are `<core>.<fine>` (e.g. `anxious.stressed`). Format:
```json
{ "version": 1, "cores": [ { "id": "happy", "color": "#F2C14E", "fine": ["happy.joyful", "happy.grateful", "happy.proud", "happy.hopeful", "happy.excited"] } ] }
```
Choose a soft, distinguishable colour per core that meets contrast in light and dark mode; colour is never the only signal.

### 6.4 `pattern-config.json`
Both apps load thresholds from this file; there are no defaults in code. A test on each platform asserts every key is read.
```json
{
  "version": 1,
  "minSegment": 3, "minRest": 3, "patternMinSegment": 5, "patternMinRest": 5,
  "minAbsDiff": 0.4, "zHint": 1.5, "zPattern": 2.0, "sdFloor": 0.3,
  "weekdayMinSegment": 4, "weekdayMinDistinctDates": 2, "weekdayMinAbsDiff": 0.5, "weekdayMinDataDays": 14,
  "tagMinLowTagged": 4, "tagMinCount": 3, "tagMinShare": 0.5, "tagMinShareGap": 0.25, "tagPatternCount": 5,
  "tagAtPlaceMinShare": 0.4, "fineTagDominance": 0.7,
  "shortWeekThreshold": 10, "maxItems": 3, "maxItemsLowWeek": 2,
  "notEnoughDataBelow": 5, "firstWeekDays": 14, "firstCardMinEntries": 5,
  "lowWeekMinEntries": 4, "lowWeekShare": 0.6, "lowRunLength": 5, "lowRunMinDays": 2,
  "placeRadiusMeters": 100, "placeMaxAccuracyMeters": 150
}
```

### 6.5 `pattern-library.en.json`
Placeholders: `{segment}` (morning/afternoon/evening/night), `{weekday}`, `{place}` (current name or "an unnamed place"), `{tag}` (lower-case label). Ship exactly this seed; the human may extend it. Note `"weekday": "7"` = Sunday (ISO).

```json
[
  { "id": "afternoon-dip", "match": { "detector": "timeOfDay", "direction": "lower", "segment": "afternoon" },
    "title": "Afternoons run lower",
    "common": "Lots of people notice their mood and energy dip after lunch.",
    "tip": "Try a 10-minute walk or a few minutes of daylight shortly before the dip usually starts.",
    "question": "Where could a 10-minute break fit tomorrow afternoon?" },

  { "id": "sunday-evening", "match": { "detector": "weekdayDaypart", "direction": "lower", "weekday": "7", "segment": "evening" },
    "title": "Sunday evenings feel heavier",
    "common": "A dip before the week starts is something many people recognise.",
    "tip": "Plan one small thing to look forward to on Monday.",
    "question": "What's one small thing you could put into Monday?" },

  { "id": "out-lifts", "match": { "detector": "awayFromHome", "direction": "higher" },
    "title": "You feel better when you're out",
    "common": "Many people find a change of scene lifts their mood.",
    "tip": "Put one outdoor or out-of-the-house block into your week.",
    "question": "Which day has room for it?" },

  { "id": "stress-at-place", "match": { "detector": "tagAtPlace", "tag": "anxious.stressed" },
    "title": "Stress tends to show up at {place}",
    "common": "It's common for one place to carry most of a week's stress.",
    "tip": "Take a 2-minute pause when you arrive at {place}, or just before you leave.",
    "question": "Arriving or leaving — which pause is easier to try?" },

  { "id": "tired-on-low", "match": { "detector": "tagOnLow", "tag": "tired" },
    "title": "Tiredness shows up on most low check-ins",
    "common": "Low mood and tiredness often travel together.",
    "tip": "Look at sleep before anything else: pick one night this week to start winding down earlier.",
    "question": "Which night could start 30 minutes earlier?" },

  { "id": "time-lower", "match": { "detector": "timeOfDay", "direction": "lower" },
    "title": "Your {segment}s run lower",
    "common": "Many people have a time of day that's harder than the rest.",
    "tip": "Give your {segment}s one small buffer this week: a short break, something to eat, or a few minutes outside.",
    "question": "What would make the next {segment} a little easier?" },

  { "id": "time-higher", "match": { "detector": "timeOfDay", "direction": "higher" },
    "title": "Your best stretch is the {segment}",
    "common": "Most people have a time of day when they feel more like themselves.",
    "tip": "If you can, put something you care about into your {segment}s.",
    "question": "What could you move into that time?" },

  { "id": "weekday-lower", "match": { "detector": "weekday", "direction": "lower" },
    "title": "{weekday}s run lower",
    "common": "Many people have one day of the week that's harder than the others.",
    "tip": "Add one small, kind thing to your next {weekday}.",
    "question": "What would make next {weekday} lighter?" },

  { "id": "weekday-higher", "match": { "detector": "weekday", "direction": "higher" },
    "title": "{weekday}s go well for you",
    "common": "Noticing your good days is as useful as noticing the hard ones.",
    "tip": "Look at what's different about {weekday}s and borrow one piece of it for another day.",
    "question": "What makes {weekday}s work?" },

  { "id": "weekday-segment-lower", "match": { "detector": "weekdayDaypart", "direction": "lower" },
    "title": "{weekday} {segment}s run lower",
    "common": "Many people notice one stretch of the week that's harder.",
    "tip": "Plan something small and easy for your next {weekday} {segment}.",
    "question": "What could you plan for it?" },

  { "id": "weekday-segment-higher", "match": { "detector": "weekdayDaypart", "direction": "higher" },
    "title": "{weekday} {segment}s go well",
    "common": "Noticing what goes well is half of repeating it.",
    "tip": "Think about what makes that time work and borrow one piece for another day.",
    "question": "Which piece would you borrow?" },

  { "id": "place-lower", "match": { "detector": "place", "direction": "lower" },
    "title": "Check-ins at {place} run lower",
    "common": "Places often carry their own mood.",
    "tip": "Try one small change at {place}: a short pause, a different spot, or a moment outside.",
    "question": "Which change is easiest to try?" },

  { "id": "place-higher", "match": { "detector": "place", "direction": "higher" },
    "title": "You feel better at {place}",
    "common": "Many people have a place that steadies them.",
    "tip": "If you can, spend a little more time at {place} this week.",
    "question": "When could you get there?" },

  { "id": "home-higher", "match": { "detector": "awayFromHome", "direction": "lower" },
    "title": "Home feels better than being out",
    "common": "Being out takes more energy for a lot of people.",
    "tip": "Plan a few quiet minutes to recharge after you get back.",
    "question": "When do you usually need that most?" },

  { "id": "tag-on-low", "match": { "detector": "tagOnLow" },
    "title": "Feeling {tag} shows up on most low check-ins",
    "common": "Putting a name to what's behind a low moment is a useful step.",
    "tip": "Pick one small thing that would ease feeling {tag} this week.",
    "question": "What usually helps, even a little?" },

  { "id": "tag-at-place", "match": { "detector": "tagAtPlace" },
    "title": "Feeling {tag} tends to show up at {place}",
    "common": "It's common for one place to bring out one feeling.",
    "tip": "Take a 2-minute pause at {place} this week and notice what changes.",
    "question": "When would that pause fit?" }
]
```

### 6.6 `helplines.json` — **human-owned**
The agent creates the file with exactly this seed and must not add numbers from memory. Every entry starts `"verified": false`; **release is blocked until the human has checked each one against its official source** and set it to true.
```json
{
  "regions": {
    "US": [ { "name": "988 Suicide & Crisis Lifeline", "phone": "988", "sms": "988", "verified": false } ],
    "GB": [ { "name": "Samaritans", "phone": "116 123", "verified": false } ],
    "IE": [ { "name": "Samaritans", "phone": "116 123", "verified": false } ],
    "IT": [ { "name": "Telefono Amico Italia", "phone": "02 2327 2327", "verified": false } ]
  },
  "emergency": { "US": "911", "CA": "911", "GB": "999", "default": "112" },
  "directory": { "name": "Find A Helpline", "url": "https://findahelpline.com", "verified": false }
}
```

### 6.7 Strings and conformance fixtures
**Strings.** `spec/strings/en.json` maps dotted keys to text with named placeholders, e.g. `"week.observation.timeOfDay.lower": "Your {segment} check-ins were lower than the rest of your day ({count} check-ins)."`. `gen_strings.py` writes `ios/App/Resources/Localizable.xcstrings` (placeholders → `%@`/`%lld`, keys unchanged) and `android/app/src/main/res/values/strings.xml` (dots → underscores, placeholders → `%1$s`, `%2$d`). Generated files carry a "do not edit" header. Tag labels (`tag.<id>`), face labels, notification text and all copy in §10 live here.

**Fixtures.** `generate_fixtures.py` (Python 3 standard library only, fixed seeds) writes input data and hand-checkable expectations. Both platforms' test suites load every fixture from `spec/fixtures/` (iOS tests locate the repo via `#filePath`; Android `:core` tests via a Gradle-provided path).

| Fixture set | Scenarios (each `name.input.json` + `name.expect.json`) |
|---|---|
| `patterns/` | `afternoon-dip`, `sunday-evening` (28 days), `tired-on-low`, `stressed-at-work` (with a Work label), `out-lifts` (with a Home label), `low-week`, `not-enough-data`, `first-week`, `dst-week` (entries across 25 Oct 2026, Europe/Rome), `travel` (some entries in America/New_York), `noise-1..3` |
| `places/` | `three-places-noise` (±40 m), `accuracy-filter`, `label-capture`, `label-deleted` |
| `periods/` | card-period boundaries for several "now" values, including the DST week |
| `inbox/` | valid, partial and corrupt iOS inbox files |

`expect.json` holds assertions such as `firstItemLibraryID`, `itemsIncludeLibraryIDs`, `variant`, `isLowWeek`, `maxItemCount`, `candidatesInclude` (`{id, confidence}`), `dayparts` (`{entryID: daypart}`), `sameCluster` / `differentCluster` / `unclustered`.

**Goldens.** When the lead platform's engine passes all `expect` files (M2), it writes `<name>.golden.json` (run tests with `WRITE_GOLDENS=1`): the full sorted candidate list (`id`, `confidence`, `nSegment`, `nRest`, `diff`, `z`, `score`, numbers rounded to 6 decimals), the selected items with library ids, `variant`, `isLowWeek`, `windowKind`, `entryCount`, `moodCounts`. The human reviews and commits them. From then on **both** platforms must reproduce every golden exactly. Goldens are regenerated only on purpose, never to make a failing test pass.

---

## 7. Core algorithms (identical on both platforms)

### 7.1 Location acceptance
`capture(timeout) -> LocationSample?` (platform code in §8/§9 does the actual request):
1. nil if location capture is off in settings or permission isn't granted.
2. Ask for one fix at ~10 m accuracy. Accept the first fix with accuracy ≤ 100 m (`kind = fresh`). At timeout, use the best fix seen if ≤ 200 m.
3. Otherwise use the system's last known location if ≤ 10 min old and ≤ 200 m accurate (`kind = cached`); else nil.
- Timeouts: in-app 10 s; widget/notification 4 s (spikes may change these).
- **In-app taps save the entry first with `location = nil`, then update it when the fix arrives.** Never block the save.
- Approximate-only permission (Android "approximate", iOS "Precise Location" off) gives accuracy too coarse for places; Settings explains this.

### 7.2 Place clustering (`PlaceResolver`)
Places are **computed, not stored per entry**, so renames and new labels apply to all history.
```
resolve(entries, labels, config) -> PlaceAssignment
  // entryID -> ClusterID; ClusterID -> Cluster(centroid, memberIDs, label?)

1. eligible = entries with location.horizontalAccuracy <= placeMaxAccuracyMeters (150), sorted by (createdAt, id).
2. Named pass: assign each eligible entry to the nearest label anchor within placeRadiusMeters (100)
   → ClusterID "p:<labelID>".
3. Unnamed pass, in order: join the nearest unnamed cluster whose running centroid is within 100 m
   (centroid = arithmetic mean of members' lat/lon); otherwise start a new cluster "u:<firstEntryID>".
4. Stabilise once: recompute unnamed centroids, reassign each unnamed member to the nearest centroid
   within 100 m (or its own cluster), drop empty clusters, keep "u:<earliest member id>" as the id.
5. Entries without an eligible location have no cluster.
Distance: haversine, R = 6,371,000 m. Ties: lower ClusterID string wins.
```
- **Naming:** chips *Home*, *Work* and a free-text name. Creates a `PlaceLabel` with anchor = cluster centroid. If another label's anchor is within 100 m, ask "Is this {name}?" — if yes, don't create a new label; move that label's anchor to the combined centroid.
- **Unnamed place descriptions** (no maps): "Unnamed place · 8 check-ins · mostly weekday mornings" (most common daypart + weekday/weekend split).
- Places screen: rename, change kind, delete label (entries fall back to unnamed clusters), **"Remove location from all entries"** (confirmation).

### 7.3 Pattern engine
**Dayparts** (entry's local time): `morning` 05:00–11:59, `afternoon` 12:00–16:59, `evening` 17:00–21:59, `night` 22:00–04:59.

**Card period** (`CardPeriod`): `periodEnd` = the latest occurrence of the weekly card time (default Sunday 19:00, ISO weekday 7) that is ≤ now, in the device's current time zone; `periodStart` = the same local time 7 calendar days earlier (not 7 × 24 h — DST). Fixture set `periods/` pins this.

**Windows**
- *Card window* = `[periodStart, periodEnd)`. If it has fewer than `shortWeekThreshold` entries, use the 14 calendar days ending at `periodEnd` and set `windowKind = twoWeeks`.
- *Weekday window* = the 28 calendar days ending at `periodEnd`. Weekday detectors are skipped if the first entry ever is less than `weekdayMinDataDays` before `periodEnd`.

**Statistics**
```
var(x)    = Σ(x − mean)² / (n − 1)        (0 when n = 1)
diff      = mean(seg) − mean(rest)
sdPooled  = sqrt(((nSeg−1)·var(seg) + (nRest−1)·var(rest)) / (nSeg + nRest − 2))
z         = diff / (max(sdPooled, sdFloor) · sqrt(1/nSeg + 1/nRest))
pattern   : nSeg ≥ patternMinSegment, nRest ≥ patternMinRest, |diff| ≥ minAbsDiff, |z| ≥ zPattern
earlyHint : nSeg ≥ minSegment,        nRest ≥ minRest,        |diff| ≥ minAbsDiff, |z| ≥ zHint
score     = |diff| · min(nSeg, 10)/10 + (pattern ? 0.2 : 0)
direction = diff < 0 ? lower : higher
```
Weekday detectors use `weekdayMinSegment`, `weekdayMinAbsDiff`, and require the segment to span ≥ `weekdayMinDistinctDates` local dates. The z-guard matters: on a 1–3 scale random noise easily produces a 0.4 difference in a small segment.

**Mean-comparison detectors**
| Detector | Family | Segment | Rest | Window | Pattern id |
|---|---|---|---|---|---|
| `timeOfDay` | time | one daypart | all other entries | card | `timeOfDay.<dir>.<daypart>` |
| `weekday` | weekday | one ISO weekday | other days | weekday | `weekday.<dir>.<1-7>` |
| `weekdayDaypart` | weekday | weekday × {morning, afternoon, evening} | all other entries | weekday | `weekdayDaypart.<dir>.<1-7>.<daypart>` |
| `place` | place | one cluster | other located entries | card | `place.<dir>.<clusterID>` |
| `awayFromHome` | place | located entries not in the Home label's cluster | entries in it | card | `awayFromHome.<dir>` |

`awayFromHome` needs a label with `kind == home`.

**Tag detectors.** An entry "has core c" if any of its tags is c or starts with `c.`. Counts are at core level; if one fine tag appears in ≥ `fineTagDominance` of the segment hits, report that fine id instead.
| Detector | Family | Rule | Pattern when | Pattern id |
|---|---|---|---|---|
| `tagOnLow` | tag | Low entries with tags ≥ `tagMinLowTagged`; core appears in ≥ `tagMinShare` of them and ≥ `tagMinCount`; its share among Okay/Good tagged entries is ≥ `tagMinShareGap` lower | count ≥ `tagPatternCount` | `tagOnLow.<tag>` |
| `tagAtPlace` | tag | At one cluster: core appears in ≥ `tagMinCount` entries and ≥ `tagAtPlaceMinShare` of the cluster's tagged entries; its share elsewhere is ≥ `tagMinShareGap` lower | count ≥ `tagPatternCount` | `tagAtPlace.<tag>.<clusterID>` |

Tag score = (shareSegment − shareRest) · min(count, 10)/10 + (pattern ? 0.2 : 0). Tag patterns have no direction.

**Determinism:** sort by confidence (pattern first), then score **rounded to 9 decimals** descending, then id ascending. Rounding avoids last-bit differences between Swift and Kotlin maths.

```
DetectedPattern { id, detector, family, direction?, params {segment, weekday, clusterID, placeKind, tag},
                  nSegment, nRest, meanSegment?, meanRest?, diff?, z?, score, confidence, supportingEntryIDs }
```

### 7.4 Library matching and card building
**Matching** (`LibraryMatcher`): an entry matches when its `detector` equals the pattern's and every other key it specifies (`direction`, `segment`, `weekday`, `placeKind`, `tag`) equals the pattern's param; `tag` also matches the pattern tag's core. Values are strings. Most matched keys wins; ties go to file order. A test enumerates every detector × direction × segment/weekday/placeKind/tag-core combination and asserts something matches.

**Variant and limits** (period entries = entries in `[periodStart, periodEnd)`):
- `notEnoughData`: period entries < `notEnoughDataBelow` → max 1 item (hints allowed), copy "patterns need a few more check-ins".
- else `firstWeek`: first entry ever is within `firstWeekDays` of `periodEnd` → max 1 item, copy "patterns get clearer after two or three weeks".
- else `normal`: max `maxItems`.
- `isLowWeek` (independent): period entries ≥ `lowWeekMinEntries` and Low share ≥ `lowWeekShare` → max items capped at `maxItemsLowWeek`, and the card **leads with acknowledgment and *Talk to someone* before any tips**.

**Selection** (`WeeklyCardBuilder`):
1. Use the deterministic order above.
2. Pick greedily, **max one per family**, up to the limit.
3. Balance (limit ≥ 2): if nothing picked is `higher` but a `higher` candidate with `pattern` confidence exists, it replaces the last pick.
4. Early hints only fill slots patterns didn't; the UI labels them "Early hint".

```
WeeklyCard { periodStart, periodEnd, windowKind, entryCount, moodCounts [low, okay, good],
             days [7 × counts per face], isLowWeek, variant, items [CardItem {patternID, libraryID, pattern}] }
```
- **Observation line** comes from strings (`week.observation.<detector>.<dir>`), e.g. "Your afternoon check-ins were lower than the rest of your day (6 check-ins)." Never show raw averages like 1.6.
- Store the structured card; render text at display time so renamed places show current names. A cluster that no longer exists renders as "one of your places".

**Card timing**
- The first card appears at the first card time with ≥ `firstCardMinEntries` entries in total, even if that's fewer than 7 days after install.
- The card is built and stored the first time the app opens after the card time. A repeating local notification says only "Your week is ready".

### 7.5 Low-mood support (`LowMoodMonitor`)
- **Low run (Today screen card):** the last `lowRunLength` entries are all Low **and** span ≥ `lowRunMinDays` local dates. Dismissing stores the latest entry id; the card re-arms only after a non-Low entry.
- **Low week:** §7.4.
- Never a notification, never on a widget, never in an export filename.

---

## 8. iOS platform design

### 8.1 Targets and modules
- `SmileProc` app (SwiftUI), `SmileProcWidgets` extension, `SmileProcUITests`.
- `ios/SmileCore` Swift package (iOS 17, macOS 14; imports Foundation, GRDB, CryptoKit, Security, CommonCrypto only): models, canonical JSON, content loaders (take `Data`; the app bundles `spec/content/*.json` as resources via `project.yml`), places, patterns, card builder, low mood, storage, sealer, inbox, export.
- `ios/Shared/` compiled into app **and** widget: `LogMoodIntent`, `FaceView`, `AppGroup` (paths, shared defaults keys), `QuickLocation`, `InboxWriter`.
- **The widget extension never opens the database.** It only writes inbox files.

### 8.2 Storage and encryption
```sql
CREATE TABLE entry (id TEXT PRIMARY KEY NOT NULL, createdAt DOUBLE NOT NULL, updatedAt DOUBLE NOT NULL, payload BLOB NOT NULL);
CREATE INDEX entry_createdAt ON entry(createdAt);
CREATE TABLE place (id TEXT PRIMARY KEY NOT NULL, payload BLOB NOT NULL);
CREATE TABLE weekly_card (periodStart DOUBLE PRIMARY KEY NOT NULL, payload BLOB NOT NULL);
```
- `payload` = `AES.GCM.seal(canonicalJSON, using: key, authenticating: Data(id.utf8)).combined` (nonce ‖ ciphertext ‖ tag). Binding the id stops payloads being swapped between rows. `createdAt` stays plaintext for ordering.
- GRDB `DatabaseMigrator`; **after the dogfood build (M5), never edit `v1` — add migrations.**
- DB at `Application Support/SmileProc/smileproc.sqlite` in the **app** container.
- Key: 256-bit `SymmetricKey` in the Keychain (generic password, service `smileproc.db`, account `master-v1`, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, not synchronizable).
- App entitlement `com.apple.developer.default-data-protection = NSFileProtectionComplete`; also set `.complete` on the DB directory and files.
- Open the DB **only** when `UIApplication.shared.isProtectedDataAvailable`. The app may be launched in the background while locked (notification actions) — then it must not touch the DB or the key.
- If the DB exists but the key is missing: recovery screen (*Import an export* / *Start fresh*). Never crash, never silently wipe.
- `isExcludedFromBackup = true` on the DB directory and the Inbox directory.

### 8.3 Inbox (writes while locked)
The widget and the background notification handler can't read the key or the `.complete` DB while locked, so they write small files the app merges later.
- Directory `<AppGroup>/Inbox/`. `<id>.entry.json` = `{"v":1,"id","createdAt","timeZoneID","mood","source"}`; `<id>.loc.json` = `{"v":1,"id", …LocationSample}` (separate, so the entry is saved even if location never arrives).
- Write with `[.completeFileProtectionUnlessOpen, .withoutOverwriting]`: such files can be **created while locked** but not read until unlock (spike S1 confirms).
- `InboxIngestor` runs on launch, on `scenePhase == .active` and on `protectedDataDidBecomeAvailableNotification`: insert entries not yet in the DB (idempotent), attach `.loc.json` to entries with no location, delete files only after commit; unparseable files are skipped, and deleted after 24 h with a count (no content) shown in the debug inspector.
- If a write fails (e.g. before the first unlock after a restart), the widget shows "Unlock to save" for 60 s.
- `LogMoodIntent` stores `lastSavedAt` (timestamp only) in App Group defaults for the widget's "Saved" state.

### 8.4 Widgets, notifications, location, lock
- **Widgets:** `accessoryRectangular` (lock screen) and `systemSmall` (home screen), three `Button(intent: LogMoodIntent(mood:))`. After a tap: "✓ Saved" for 60 s (timeline: saved entry now, normal at +60 s), never highlighting which face. Tapping outside the buttons opens `smileproc://entry/latest`. `LogMoodIntent`: `openAppWhenRun = false`, `authenticationPolicy = .alwaysAllowed` (the default — set explicitly), `isDiscoverable = false`, compiled into app and widget.
- **Notifications:** category `CHECKIN`, three actions, **no** `.foreground` and **no** `.authenticationRequired`. Handler → `InboxWriter` + `QuickLocation` (4 s) → completion handler promptly. Reminders: repeating `UNCalendarNotificationTrigger` per time × weekday (6 × 7 + weekly card stays under the 64 pending limit).
- **Location:** `requestLocation()` in a continuation; When In Use requested in onboarding. Widget extension: `NSWidgetWantsLocation = YES`, check `isAuthorizedForWidgetUpdates`.
- **App lock:** `LAContext.evaluatePolicy(.deviceOwnerAuthentication)` on launch and after 60 s in background; `PrivacyCover` blurs whenever the scene isn't active.
- **Delete all:** close DB, delete DB + Inbox, delete Keychain item, clear both defaults suites, remove notifications, reload widgets, back to onboarding.
- **Privacy manifest:** `PrivacyInfo.xcprivacy` for app and widget (no tracking, no collected data; required-reason APIs actually used — check Apple's current list).

---

## 9. Android platform design

### 9.1 Modules
- `:core` — **pure Kotlin/JVM** (no Android imports): models, canonical JSON (kotlinx.serialization), content loaders (take `String`), places, patterns, card builder, low mood, `Sealer` interface, export format and export crypto (`javax.crypto`, works on JVM and Android). Mirrors `SmileCore` file for file. Tests run with `./gradlew :core:test` — no emulator needed.
- `:app` — Compose UI, Room data layer, `KeystoreSealer`, location, notifications and alarms, Glance widget, biometric lock. `spec/content/` is added as an assets source directory in Gradle, so there's no copying.

### 9.2 Storage and encryption
```kotlin
@Entity(tableName = "entry", indices = [Index("createdAt")])
data class EntryRow(@PrimaryKey val id: String, val createdAt: Long, val updatedAt: Long, val payload: ByteArray)
@Entity(tableName = "place") data class PlaceRow(@PrimaryKey val id: String, val payload: ByteArray)
@Entity(tableName = "weekly_card") data class WeeklyCardRow(@PrimaryKey val periodStart: Long, val payload: ByteArray)
```
- `payload` = IV (12 bytes) ‖ ciphertext ‖ tag, `AES/GCM/NoPadding`, AAD = id bytes, over the canonical JSON.
- Key: AES-256 in `AndroidKeyStore` (alias `smileproc-db-v1`, GCM, no padding, randomized encryption; IV from the cipher). **No `setUserAuthenticationRequired` and no `setUnlockedDeviceRequired`**, so notification actions can save while the phone is locked (after the first unlock since boot). This is the deliberate trade-off; the at-rest protection is the hardware key plus file-based encryption.
- Room with `exportSchema = true`, schemas committed; **after M5, migrations only.**
- DB in default app storage (credential-encrypted, available after the first unlock). Android doesn't need an inbox: the app process can write directly while locked. Before the first unlock after a reboot the app doesn't run at all (not direct-boot aware), so nothing is lost.
- Backups off: `android:allowBackup="false"`, and `dataExtractionRules` excluding everything from cloud backup **and** device transfer.
- If the DB exists but the key is missing: same recovery screen as iOS.

### 9.3 Lock-screen logging, widget, notifications
- **Lock screen reality:** most Android phones have no lock-screen widgets; Android 16-era phones can show widgets on the lock screen in a hub that appears while charging/docked, and it varies by maker. So on Android **the reminder notification with three face buttons is the primary lock-screen path**; the widget is a bonus wherever the phone supports it.
- **Notifications:** channel "Check-in reminders" (default importance) and "Weekly card" (low). `VISIBILITY_PUBLIC` (text is generic) so the buttons show on the lock screen. Three actions → `PendingIntent.getBroadcast` (`FLAG_IMMUTABLE`) with `setAuthenticationRequired(false)`. `CheckInActionReceiver` uses `goAsync()`, inserts the entry via the repository, then replaces the notification with a brief "Saved" or cancels it. `POST_NOTIFICATIONS` requested in onboarding (Android 13+).
- **Reminders:** `AlarmManager.setAndAllowWhileIdle` (inexact — no exact-alarm permission) for each reminder's next occurrence; on fire → post notification → schedule the next. Reschedule on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED` and settings changes. Same for the weekly card notification. A few minutes of drift is acceptable (spike A4 measures it).
- **Widget (Glance):** three buttons → `actionRunCallback<LogMoodCallback>(actionParametersOf(moodKey to n))`, which saves through the repository. Source = `lockScreenWidget` when the host category is keyguard, else `homeScreenWidget`. "✓ Saved" state via Glance state with an alarm at +60 s that calls `updateAll`. Don't add the `not_keyguard` category. Widget body opens the app (normal unlock; `MainActivity` is **not** `showWhenLocked`). Support dynamic colour (`GlanceTheme`) and resizing.
- **Location:** `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (user may pick approximate). `LocationManagerCompat.getCurrentLocation` with the platform fused provider where available, else GPS/network; last known from `getLastKnownLocation`. No Play Services. From a notification action or widget tap the app is in the background, so a fresh fix probably isn't allowed without background permission — **spike A2** tries (a) last known location in the receiver and (b) a short location foreground service started from the action; the human decides. Default until then: (a), else no location.
- **App lock:** `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`; on launch and after 60 s in background (`ProcessLifecycleOwner`). If the phone has no screen lock, the toggle is disabled with an explanation. App-switcher privacy: `setRecentsScreenshotEnabled(false)` on Android 13+, `FLAG_SECURE` while locked below that.
- **Delete all:** `deleteDatabase`, delete the Keystore entry, clear DataStore, cancel alarms and notifications, update widgets, back to onboarding.
- **Play Store:** Data safety form — no data collected or shared.

---

## 10. Screens & flows (same structure on both; native controls)

**Tabs:** Today · Week · Settings (History opens from Today). iOS uses SwiftUI conventions; Android uses Material 3 (bottom navigation bar, sheets, snackbars) — same screens, same copy.

- **Today:** "How are you right now?" with three large faces (`FaceView`, custom-drawn shapes so they render in monochrome widget modes too). A tap saves at once with a light haptic, then shows the follow-up sheet: "Saved" header, tag picker in the remembered mode with a mode switch, comment field, Done. Dismissing keeps the entry. Below: the low-run support card (when armed), today's entries, "All entries".
- **Gradual tag picker:** step 1 "What's closest?" (7 core chips) → step 2 finer chips plus "Just {core}". Up to 3 tags; selected chips stay visible at the top.
- **Detailed (wheel):** inner ring of 7 cores, outer ring of 35 fine tags, diameter `min(width − 32, 360)` pt/dp. Tap selects; chips echo the selection. When VoiceOver/TalkBack is on or text is at the largest sizes, show the list picker instead (sections by core).
- **History:** grouped by day; face, time, tags, place name, comment snippet. Detail: change mood/tags/comment, name this place, delete (confirmation).
- **Week:** latest card (7-day strip of face counts → optional low-week block → 2–3 items with title, observation, "many people notice", tip, question, "Name this place" when unnamed) and past cards. Before the first card: "Your first weekly card arrives {Sunday at 19:00}."
- **Settings:** Reminders · Weekly card time · Places · Capture location · App lock · Export · Import · Privacy ("What's stored and where") · Delete all data · About.
- **Onboarding (4 screens):** (1) privacy promise in plain words, including "not in backups — export to keep a copy"; (2) location explanation → permission (skippable); (3) reminders → notification permission + pick times (skippable); (4) how to add the widget — on iPhone the lock-screen widget, on Android the home-screen widget plus "you can also answer straight from the reminder" — and the App lock toggle (preselected ON).
- **Copy rules:** plain, warm, short; "Many people notice…", not "Studies show…"; "Try…", "If you can…", never "You should…"; no clinical terms; no numbers that grade the user; no exclamation marks on low-mood screens. Faces: **Low / Okay / Good**. Reminder: title "SmileProc", body "How are you right now?". Weekly: "Your week is ready". Low-week block: "This week looked heavy. That's worth noticing, and it's okay." + "Talking to someone you trust, or a support line, can make it lighter." → **Talk to someone**. Low-run card: "Your last few check-ins have been low. If it would help, talking to someone can make it lighter." → **See support options** · **Not now**.
- **Helplines:** region from the device locale; entries, emergency number, directory link; taps open the phone/SMS/browser app.

---

## 11. Milestones

Every part ends with: that platform's checks pass (§5.3), `docs/progress.md` updated, and a short summary including **[device]** steps. "Lead" / "Other" refer to the lead platform in `AGENTS.md`.

### M0 — Repo, spec and scaffolds (both)
- Monorepo layout (§5.1), `AGENTS.md` (§2, §5.4, lead platform), `docs/`, `.gitignore`.
- `spec/content/*` (§6.3–6.6 verbatim; colours chosen), `spec/strings/en.json` with all copy from §10, `gen_strings.py`, `generate_fixtures.py` + generated inputs and expectations (§6.7).
- iOS scaffold: `project.yml` (app, widget, UI tests, local `SmileCore` with GRDB, App Group on both, data-protection entitlement, Swift 6, iOS 17.0, `DEVELOPMENT_TEAM` empty), bundles `spec/content` as resources. Builds.
- Android scaffold: Gradle KTS + version catalog, `:core` (JVM) + `:app` (Compose "hello" screen), assets source = `spec/content`, minSdk 29, no `INTERNET`. `assembleDebug` passes.
- Scripts: `check-spec.sh`; `test-ios.sh`; `test-android.sh`; `privacy-guard.sh` — iOS: grep for forbidden symbols (§2.1) and `Package.resolved` contains only GRDB; Android: grep for forbidden classes/packages, `:app:dependencies` (release runtime classpath) contains no `com.google.android.gms`, `com.google.firebase`, `com.squareup.okhttp3`, `com.squareup.retrofit2`, `io.ktor`, and `apkanalyzer manifest permissions` on the debug APK shows no `android.permission.INTERNET`.

**Done when:** both scaffolds build, `check-spec.sh` and `privacy-guard.sh` pass.

### M1 — Device spikes (both, [device])
Spike code lives behind a Debug screen on each platform; `docs/spikes.md` has step-by-step instructions and a results table.
- **iOS S1** locked writes: lock-screen widget intent and a notification action each write an inbox file with `completeUnlessOpen`; Debug lists them after unlock.
- **iOS S2** location while locked: widget intent and notification action record fresh/cached/none, accuracy, age.
- **iOS S3** widget UX: three buttons fit and work in `accessoryRectangular`; Saved state appears and reverts.
- **Android A1** notification buttons on the lock screen save without unlocking (Keystore + DB usable while locked after first unlock).
- **Android A2** location from a notification action: (a) last known in the receiver; (b) short location foreground service started from the action. Record fresh/cached/none.
- **Android A3** Glance widget buttons and Saved state; on a phone with lock-screen widgets, whether buttons work without unlocking.
- **Android A4** reminder drift with the phone idle overnight.

**Done when:** results recorded; the human has decided iOS "Always" (yes/no), Android location approach (a/b), and any fallback if S1 fails. **Stop.**

### M2 — Shared logic (Lead, then Other)
**Build (each platform):** models + canonical JSON codec, `PatternConfig` loader, content loaders, `Geo`, `PlaceResolver`, `CardPeriod`, the 7 detectors, `PatternEngine`, `PatternLibrary`, `LibraryMatcher`, `WeeklyCardBuilder`, `LowMoodMonitor`; a synthetic data generator for local tests.
**Tests (each platform):** every `places/`, `patterns/`, `periods/` fixture's `expect` assertions; library coverage; all config keys read; noise guard (100 random weeks of 25 entries with the platform's seeded RNG → average `pattern` items per card ≤ 0.3 — if it fails, tune `pattern-config.json` and tell the human); low-run cases (5 Low on one day → no card; across 2 days → card; dismissal and re-arming).
**Lead extra:** write goldens (`WRITE_GOLDENS=1`), human reviews and commits.
**Other extra:** must match every golden exactly.
**Done when:** both platforms pass all fixtures and goldens. No UI yet.

### M3 — Storage, crypto, locked writes (Lead, then Other)
- **iOS:** `Sealer` (CryptoKit), `KeychainKeyStore` + `InMemoryKeyStore`, `AppDatabase` + `v1`, repositories, `InboxWriter`/`InboxIngestor`, backup exclusion and protection helpers. Tests: seal/open, tamper and wrong-id failure, CRUD, ingest idempotency, `.loc.json` before/after entry, `inbox/` fixtures.
- **Android:** `Sealer` interface in `:core` with a JVM `JcaSealer` for tests; `KeystoreSealer` in `:app`; Room DB + DAOs + repositories; backup rules. Tests: seal/open with `JcaSealer`, tamper and wrong-id failure, Room CRUD (Robolectric), canonical JSON round trip. **[device]** Keystore seal/open and a write while locked.

### M4 — Check-in in the app (Lead, then Other)
App container, Today with `FaceView`, single-tap save + async location update, follow-up sheet with **gradual** picker and comment, History and Entry detail (edit/delete), minimal onboarding (location permission), generated strings wired in. iOS: inbox ingest on launch/active/protected-data-available.
**Done when:** UI test taps a face → entry in History with no follow-up required; edits persist across relaunch; **[device]** fresh location within ~10 s.

### M5 — Widgets and reminders (Lead, then Other) → **dogfood build 1**
- **iOS:** final widgets (lock + home, Saved and "Unlock to save" states, deep link); `NotificationService`; Reminders screen (up to 6 times, weekday selection); notification action via inbox.
- **Android:** Glance widget (Saved state, keyguard source detection, deep link); notification channels, action receiver, alarm scheduling and rescheduling receivers; Reminders screen.
**Done when:** **[device]** with the phone locked, notification buttons (both) and the iPhone lock-screen widget each create an entry that appears after unlocking; reminders fire at the set times.
**Then:** the human starts logging for real on their own phone. From here on, schema changes only through migrations.

### M6 — Detailed emotion wheel (Lead, then Other)
Wheel (two rings, largest possible tap targets, chip echo), list picker for screen readers / large text, persisted mode.
**Done when:** both modes produce the same tag ids; the remembered mode opens next time; VoiceOver/TalkBack can select any tag from the list.

### M7 — Places UI (Lead, then Other)
Naming sheet (Home/Work/custom, "Is this {name}?" merge), Places screen (rename, kind, delete label, remove all locations), place names in History and Entry detail, unnamed descriptions.
**Done when:** naming a place from one entry names every entry in that cluster on both platforms.

### M8 — Weekly card UI and support (Lead, then Other)
Week tab (latest, past, pre-first-card state), card view with Early hint label and "Name this place", low-week block, low-run card on Today, Helplines screen, weekly card time setting and "Your week is ready" notification, card built and stored on first open after the card time; Debug "seed 4 weeks of demo data" and pattern debugger (every candidate with nSeg, nRest, means, diff, z, score, confidence).
**Done when:** demo data renders all variants (normal, low week, not enough data, first week); renaming a place updates an existing card; **[device]** weekly notification arrives with generic text.
**Then:** the human runs the one-week self-trial with the real card before any reflection-mode work is considered.

### M9 — Privacy and security (Lead, then Other)
App lock and app-switcher privacy (§8.4 / §9.3), export/import (§13.2), Delete all data, full onboarding, Privacy screen, permission strings, iOS privacy manifest.
**Lead extra:** write `spec/fixtures/export/vector-1.json` with fixed password, salt, nonce, 1,000 iterations and a plaintext (stored as base64 bytes) → expected envelope.
**Other extra:** decrypts the vector to the same plaintext **and** produces the identical envelope from the same inputs.
**Tests:** round trip into an empty DB, wrong password fails cleanly, importing twice doesn't duplicate, newer `updatedAt` wins.
**Done when:** tests pass; **[device]** export from one platform imports on the other; lock and delete-all behave as specified.

### M10 — Polish and release prep (both)
Accessibility pass (screen readers on every screen and widget, largest text sizes, reduced motion, contrast in light/dark); empty and error states (location denied, notifications denied, write failed); debug tools behind debug builds only; full device checklist on both platforms; helplines verified (release blocker); App Store privacy answers and Play Data safety form; TestFlight and Play internal testing builds.

---

## 12. Testing

**Shared conformance (both, no device):** all `spec/fixtures/` sets and goldens; library coverage; config completeness; noise guard; time zones and DST via `dst-week`, `travel` and `periods/`.

**Platform unit tests:** iOS `swift test` for SmileCore (crypto, storage, inbox, export); Android `:core:test` (logic, export crypto) and `:app:testDebugUnitTest` (Room with Robolectric, repositories).

**UI tests:** onboarding skip path; tap face → saved → follow-up optional; edit and delete; reminders CRUD; Week tab with demo data (launch argument / intent extra `seedDemoData`).

**Device checklist (`docs/device-test-checklist.md`), each platform where it applies:**
1. Notification buttons while locked → entry appears after unlock; nothing about mood visible on the lock screen.
2. iPhone lock-screen widget tap while locked → same; widget never shows which face.
3. iPhone: tap right after a restart, before the first unlock → "Unlock to save", no crash.
4. Location: fresh in-app; widget/notification fresh vs cached vs none recorded.
5. Airplane mode → everything works identically.
6. App lock after 60 s in background; app-switcher shows no content.
7. Export → uninstall → reinstall → import → history restored; **iPhone export imports on Android and back**.
8. Delete all → key gone (reinstall shows onboarding, no recovery screen).
9. Weekly notification at the set time with generic text; Android reminders within a few minutes after overnight idle.
10. VoiceOver and TalkBack through check-in, tags (list mode), weekly card and widgets.
11. Android: phone-to-phone transfer / backup doesn't carry SmileProc data.

---

## 13. Privacy, export and stores

### 13.1 What leaves the device
Nothing, unless the user exports a file or taps a helpline link/number. The Privacy screen says so, and that history isn't in phone backups or phone-to-phone transfers, so a lost phone means lost history without an export.

### 13.2 Export format (`.smileproc`, shared)
```json
{
  "format": "smileproc-export",
  "version": 1,
  "kdf": { "name": "PBKDF2-HMAC-SHA256", "iterations": 600000, "salt": "<base64, 16 bytes>" },
  "cipher": "AES-256-GCM",
  "payload": "<base64 of nonce(12) ‖ ciphertext ‖ tag(16)>"
}
```
- Plaintext: UTF-8 JSON `{ "exportedAt", "app": {"platform": "ios"|"android", "version"}, "entries": [Entry…], "places": [PlaceLabel…], "settings": { "reminders", "weeklyCard": {"weekday", "hour", "minute"}, "tagPickerMode" } }` in the canonical format (§6.1). Weekly cards aren't exported; past cards are rebuilt from entries after import.
- Key = PBKDF2-HMAC-SHA256(password UTF-8, salt, iterations, 32 bytes). iOS: CommonCrypto + CryptoKit. Android: `PBKDF2WithHmacSHA256` + `AES/GCM/NoPadding` with a software key (in `:core`).
- Password ≥ 8 characters, entered twice, with "If you forget this password, the export can't be opened." Derivation runs off the main thread with a progress indicator.
- iOS: share sheet + `UTExportedTypeDeclaration` for `.smileproc`. Android: Storage Access Framework (`CreateDocument` / `OpenDocument`).
- Import merges by id: new ids inserted; existing ids replaced only if the imported `updatedAt` is newer.

### 13.3 Stores
- App Store: privacy label **Data Not Collected**; `PrivacyInfo.xcprivacy` as in §8.4.
- Google Play: Data safety **no data collected or shared**; no background location declaration unless the human approved it after A2.

---

## Appendix A — Platform mapping (quick reference)

| Concern | iOS | Android |
|---|---|---|
| Fastest lock-screen check-in | Lock-screen widget + notification buttons | Notification buttons (widget on lock screens that support it) |
| Saving while locked | Inbox files (`completeUnlessOpen`), merged on unlock | Direct DB write (works after first unlock) |
| Key storage | Keychain, `WhenUnlockedThisDeviceOnly` | Keystore AES-256-GCM, no auth/unlocked requirement |
| Database | GRDB, file protection `.complete` | Room, credential-encrypted storage |
| Backups | `isExcludedFromBackup` | `allowBackup=false` + extraction rules |
| Reminders | Repeating calendar triggers | Inexact alarms, rescheduled on boot/time change |
| Location | CoreLocation, When In Use | `LocationManagerCompat`, fine + coarse, no Play Services |
| App lock | LocalAuthentication | BiometricPrompt (strong biometric or device credential) |
| App switcher | Blur cover | `setRecentsScreenshotEnabled(false)` / `FLAG_SECURE` |
| Screen reader | VoiceOver list picker | TalkBack list picker |
| No-network proof | Privacy guard script | No `INTERNET` permission in the manifest |

---

## Appendix B — Decisions for the human

1. **Lead platform:** the phone you carry every day, so the dogfood build after M5 is one you'll actually use. Write it in `AGENTS.md` before M0.
2. **Devices:** you need an iPhone and an Android phone for the [device] steps (the Android emulator covers most Android checks; spike A3's lock-screen widget needs a phone that supports it).
3. **Face labels and look:** Low / Okay / Good with custom-drawn faces — change before M4 if you prefer other words.
4. **Location for lock-screen check-ins:** after spikes S2/A2, decide on iOS "Always" and the Android option (b), knowing both add store-review scrutiny.
5. **Helplines:** verify every entry in `helplines.json` before release; add your countries.
6. **Weekly card default time:** Sunday 19:00 (fits the Sunday-evening tip).
7. **App lock default:** preselected ON in onboarding.
8. **Identifiers:** bundle IDs, App Group, `applicationId`, Apple team and Play account.
9. **Shared-code option:** if keeping two logic implementations in sync becomes a burden, `:core` can become a Kotlin Multiplatform module used by iOS too; the fixtures make that switch safe.
