# iOS progress

SmileCore, the SwiftUI app, the widget extension, the UI-test target, and `ios/project.yml` are written. Nothing here was compiled: this machine has no Swift toolchain and no Xcode. Goldens and `Localizable.xcstrings` were not edited. No new string keys were added.

## What a Mac should run

```bash
swift test --package-path ios/SmileCore
xcodegen generate --spec ios/project.yml
xcodebuild -project ios/SmileProc.xcodeproj -scheme SmileProc -destination 'generic/platform=iOS Simulator' build
```

`scripts/test-ios.sh` is those two build steps. The UI test is `SmileProcUITests/CheckInUITests.swift` (launch argument `-uiTesting`). It is not part of `test-ios.sh`.

## How tests find fixtures

`FixtureRoot.url()` starts at `#filePath` and walks parent directories (up to 12) until `spec/fixtures` is a directory. Content JSON is loaded from that same repo root (`spec/content/...`). The package does not copy fixtures.

## SmileCore

Port of `spec/tools/engine.py`: models, canonical JSON, content loaders (take `Data`), `Geo`, `PlaceResolver`, `CardPeriod`, `PatternEngine`, `LibraryMatcher`, `WeeklyCardBuilder`, `LowMoodMonitor`, `Sealer` (CryptoKit AES-GCM, AAD is `Data(id.utf8)`), `KeychainKeyStore` + `InMemoryKeyStore`, GRDB migrator v1, repositories, `InboxWriter` / `InboxIngestor`, `ExportFormat` (PBKDF2 via the `CCommonCrypto` target, AES-GCM via CryptoKit).

`SmileCoreTests` covers:

- every `spec/fixtures/patterns` expect + golden
- every `spec/fixtures/places` expect + golden
- `periods/periods.json` and `periods/build-gate.json` (Europe/Rome, Sunday 19:00, matching the generator)
- `lowmood/cases.json`
- `library/coverage.json` (every row has a library id)
- every `pattern-config.json` key is read, and a missing `sdFloor` throws
- noise guard: `PythonRandom` seeded `20261010`, first four draws locked to the CPython sequence, 100 weeks × 25 entries, average pattern-confidence items ≤ 0.3
- canonical entry and place vectors, compared without a trailing newline
- seal vector (exact combined base64, open, tamper, wrong id)
- export vector (plaintext bytes, payload base64, exact compact envelope, wrong password)
- import merge (newer `updatedAt` wins, older is ignored, ids are not duplicated)
- database CRUD, then missing key returns `needsRecovery` without wiping the file
- inbox ingest is idempotent; a recent partial file is kept; a corrupt file older than 24h is deleted
- `InMemoryKeyStore` round trip

## App and widget

- SwiftUI app, `@Observable` `AppEnvironment` is the only container.
- Tabs: Today, Week, Settings. History opens from Today.
- Follow-up sheet (gradual picker, detailed wheel of diameter `min(width − 32, 360)`, list picker when VoiceOver is on or Dynamic Type is at least `.accessibility3`). Dismissing the sheet keeps the entry.
- In-app save writes the database first with no location, then updates. Widget and notification actions only write inbox files.
- Inbox ingest on launch, when the scene becomes active, and on `protectedDataDidBecomeAvailable`. Files are deleted only after commit.
- Database at Application Support/SmileProc/smileproc.sqlite, opened only when protected data is available. If the file exists and the key does not, the recovery screen offers import or start fresh.
- Keychain service `smileproc.db`, account `master-v1`, this device only, not synchronizable.
- App Group `group.com.example.smileproc`. Widget families `accessoryRectangular` and `systemSmall`. Three `Button(intent:)` faces. `openAppWhenRun = false`, `authenticationPolicy = .alwaysAllowed`, `isDiscoverable = false`. Saved and unlock states last 60 seconds and do not highlight a face. A tap outside the buttons opens `smileproc://entry/latest`.
- Notifications category `CHECKIN`, three actions, no foreground and no authentication requirement. Repeating calendar triggers. Weekly body is the existing "Your week is ready" string.
- Location is When In Use only. The code never requests Always. Widget and notification timeout is 4 seconds; in-app is 10 seconds.
- App lock uses `LAContext.deviceOwnerAuthentication` on launch and after 60 seconds in the background. An opaque cover hides the UI while the scene is not active.
- Delete all closes the database, removes the database files, the inbox, the keychain item, both defaults suites, notifications, and reloads widgets, then returns to onboarding.
- Export and import follow the shared envelope. Production PBKDF2 iterations are 600000. The share sheet is `ShareLink`. Password length is at least 8, entered twice.
- Onboarding is four screens. Helpline taps use `UIApplication.shared.open` with `tel:`, `sms:`, and `https` only.
- Debug seed (four weeks) and the pattern debugger are `#if DEBUG` only.
- `FaceView` is a custom `Canvas` using the foreground style so monochrome widgets still draw.
- Privacy manifests: no tracking, no collected data. The app declares UserDefaults `CA92.1` and file timestamps `C617.1` (inbox corrupt files use the modification date). The widget declares UserDefaults only.

## Deviations

- Entry ids are `String`. Generated ids are a lowercased UUID. Fixtures use ids such as `dst-a`, so the model is not `UUID`.
- Merging a place name weights the existing anchor as one point and the cluster centroid by its member count: `(anchor + centroid * memberCount) / (memberCount + 1)`.
- The `InboxWriter` type lives in SmileCore so tests can use it. `ios/Shared/InboxWriter.swift` is `LockedCheckIn`, the locked-write helper compiled into the app and the widget. A second `InboxWriter` type would clash.
- Settings that the widget must see (`captureLocation`, saved/failed timestamps) live in the app-group defaults suite. Delete-all also clears `UserDefaults.standard`.
- Info.plist usage strings copy existing English: `onboarding.location.body` for location, `lock.reason` for Face ID. They are not new catalog keys.
- The debug number labels (`nSeg`, `nRest`, `meanSegment`, and the rest) are not catalog keys. They appear only in DEBUG.
- `project.yml` sets `DEVELOPMENT_TEAM` to an empty string, `CODE_SIGN_STYLE` Manual, and `CODE_SIGN_IDENTITY` `-` so a generic simulator build does not need a portal team.
- The widget extension links SmileCore, which links GRDB, because the shared inbox writer imports SmileCore. The widget never opens the database.

## Unfinished / not verified here

- `swift test` and `xcodebuild` have not been run. The first Mac test run downloads GRDB 7.11.1 and writes `Package.resolved`.
- If the `CCommonCrypto` module map is rejected, or `CCKeyDerivationPBKDF` rejects the raw `kCCPBKDF2` / `kCCPRFHmacAlgSHA256` constants, that call is the place to adjust. Do not add another package.
- If GRDB 7.11 write/read closures still fail Swift 6 checking, the imports are already `@preconcurrency import GRDB`.
- `AppDelegate` is not `@MainActor`. If the SDK's `UIApplicationDelegate` is main-actor isolated, add `@MainActor` to that class only.
- Device signing, the real keychain, complete file protection, a locked-widget write, biometrics, and GPS were not exercised.
- There is no app icon. A generic simulator build should still succeed; an App Store archive will want an icon.
- Europe/Rome DST behavior follows the Mac tzdata. The period fixture expects the 25 Oct 2026 fallback.
- Canonical numbers use Swift's shortest `Double` description, with `.0` added for integrals and the exponent normalized to Python's style. If `9.19` or `12.0` does not match the golden bytes, fix `CanonicalJson.formatDouble` only.
- The UI test is written to tap "Log okay", dismiss "Done", open "All entries", and find "Okay". It was not run.
