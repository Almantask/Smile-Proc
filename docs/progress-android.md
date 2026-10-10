# Android progress

Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`

Last Gradle run from `/workspace/android` with `ANDROID_HOME=/home/ubuntu/android-sdk`:

```
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

BUILD SUCCESSFUL.

| Suite | Tests | Failures | Errors |
| --- | --- | --- | --- |
| `:core:test` (`FixtureTest`) | 12 | 0 | 0 |
| `:app:testDebugUnitTest` (`AppBehaviorTest`) | 4 | 0 | 0 |
| Total | 16 | 0 | 0 |

`:core:test` loads every `patterns/`, `places/`, `periods/`, `lowmood/`, and `library/coverage.json` fixture, the canonical and export vectors, the seal vector, and the noise-guard protocol (seed `20261010`, 100 weeks). Goldens were not edited. Floats matched after the 12-digit-then-half-up rounding rule.

## What works

- `:core` is pure Kotlin/JVM. It ports Geo, PlaceResolver, CardPeriod, PatternEngine, LibraryMatcher, WeeklyCardBuilder, LowMoodMonitor, canonical JSON (Python float formatting), ExportFormat, and JcaSealer.
- `:app` is Compose Material 3 with Navigation, Room (`exportSchema` true, schema in `android/app/schemas/`), DataStore settings, and a Keystore sealer (`smileproc-db-v1`, AES-256-GCM, no user-auth and no unlocked-device requirement). Row payloads are nonce || ciphertext || tag with AAD = id UTF-8.
- One tap on a face writes immediately with location null, then attaches a fix if one arrives. Notification and widget saves use last-known location only.
- Check-in notifications are public, use generic title/body, `setAuthenticationRequired(false)`, and immutable broadcasts. The action receiver uses `goAsync()`, inserts, cancels the check-in, and posts a short Saved notification.
- Glance widget: three faces, Saved for about 60 seconds, source `lockScreenWidget` when the host category is keyguard, otherwise `homeScreenWidget`. No face is shown after save. `widgetCategory` is `home_screen|keyguard`.
- Reminders use `AlarmManager.setAndAllowWhileIdle` and reschedule on boot, time change, timezone change, package replace, and settings changes.
- App lock offers `BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL` on cold start and after 60 seconds in the background. The toggle is disabled, with the existing unavailable string, when the device has no screen lock. Recents screenshots are disabled on API 33+. `FLAG_SECURE` is set while the lock cover is showing on older APIs.
- Export uses PBKDF2-HMAC-SHA256 and AES-GCM with 600000 iterations, off the main thread, password at least 8 characters entered twice. Import merges by id. A newer `updatedAt` wins for entries. Places have no `updatedAt`; a newer `createdAt` wins, and an equal `createdAt` keeps the existing row, so importing twice does not duplicate.
- Onboarding, privacy, helplines (`ACTION_DIAL` / `ACTION_SENDTO` / `ACTION_VIEW` only), delete-all, and a debug-only seed plus pattern debugger (including `debug.spikeNotes`).
- English copy comes from generated `strings.xml`. Tag picking switches to the list when touch exploration is on or the font scale is at least 1.3. Screens scroll.
- Merged manifest has `allowBackup="false"`, full data-extraction excludes, and no `INTERNET` permission (`tools:node="remove"`). No `HttpURLConnection`, `java.net.URL`, WebView, or Geocoder.

## Version pins

`spec/versions.json` android `composeBom` is `2026.09.00`. The requested stable coordinate `androidx.compose:compose-bom:2026.10.00` is not published (HTTP 404). An alpha artifact `compose-bom-alpha:2026.10.00` exists; the nearest stable BOM that resolves is `2026.09.00`. AGP 9.4.1, Gradle 9.6.0, Kotlin 2.4.21, KSP 2.3.12, Room 2.8.5, and Glance 1.2.0 stayed as pinned. Kotlin `jvmTarget` is 17. `jvmToolchain(17)` is not used because this machine has JDK 21 only.

## New string

`debug.spikeNotes` was added to `spec/strings/en.json` and `python3 spec/tools/gen_strings.py` was run. That regenerated `android/app/src/main/res/values/strings.xml` and added the same key to `ios/App/Resources/Localizable.xcstrings` (12 inserted lines). `strings.xml` was not hand-edited.

## Notes

- Named-place merge uses the arithmetic mean of the cluster member coordinates plus the existing label anchor as one extra point.
- Glance 1.2 pulls WorkManager, which adds `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, and `FOREGROUND_SERVICE`, plus Glance's own remote-views service. There is no location foreground service and no `INTERNET`.
- `LocationManagerCompat.getCurrentLocation` is used for an in-app fix. Last-known reads use `LocationManager.getLastKnownLocation` because `LocationManagerCompat` in the resolved AndroidX core does not expose that method.
- UI strings inside composables go through a non-composable `text()` helper so configuration changes still recompose via `LocalContext`, without calling `Locale.getDefault()` for the helpline region (`LocalLocale` is used there).

## Not finished on a device

This environment has no phone or emulator, so these were not exercised on hardware:

- Saving from a lock-screen notification after one unlock since boot (spike A1).
- Keyguard versus home-screen widget host category, and the 60-second Saved state on a real launcher (A3).
- BiometricPrompt and device-credential unlock, including a device with no screen lock.
- A real GPS fix after a tap, and last-known location from a notification or widget (A2).
- How late an inexact reminder is after sitting overnight (A4).
- TalkBack, the largest font, and animator-scale reduced motion. The UI does not add its own animations, and it does not read the animator duration scale.
