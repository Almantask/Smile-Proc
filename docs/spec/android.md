# C4 · `docs/spec/android.md`

#### Stack and dependency allowlist
| Concern | Choice |
|---|---|
| Language | Kotlin 2.x, JVM toolchain 17 |
| UI | Jetpack Compose + Material 3, Navigation Compose |
| Min / target | minSdk 29 (Android 10); compileSdk and targetSdk = latest stable |
| Storage | Room (KSP), schema export committed |
| Crypto | Android Keystore, `javax.crypto` |
| Settings | DataStore Preferences |
| Location | platform `LocationManager` via `LocationManagerCompat` (no Play Services) |
| Biometrics | androidx.biometric |
| JSON | kotlinx.serialization |
| Time | `java.time` |
| Tests | JUnit (`:core`), Robolectric + Room testing, Compose UI test, AndroidX test (instrumented) |

**Allowed:** AndroidX (core, activity, lifecycle incl. lifecycle-process, navigation, compose BOM, room, biometric, datastore), kotlinx-coroutines, kotlinx-serialization. Test-only: JUnit, kotlinx-coroutines-test, Robolectric, AndroidX test libraries. Anything else needs the human's approval. Pin to the latest stable versions at M0. Identifiers are placeholders until the human sets them: `com.example.smileproc`, deep link `smileproc://`. Build types: see plan.md B.11.

#### Modules
- `:core` — pure Kotlin/JVM: models, canonical JSON, content loaders (take `String`), places, patterns, card builder, low mood, `CheckInMachine`, `Sealer` interface, export format and crypto. `./gradlew :core:test`, no emulator.
- `:app` — Compose UI, Room, `KeystoreSealer`, location, `CheckInNotifier`, receivers and alarms, biometric lock, `SafeLog`. `content/` is an assets source directory.
- `:spikes` — M1 only; own applicationId `com.example.smileproc.spikes`; never shipped.

#### Storage and encryption
```kotlin
@Entity(tableName = "entry", indices = [Index("createdAt")])
data class EntryRow(@PrimaryKey val id: String, val createdAt: Long, val updatedAt: Long, val payload: ByteArray)
@Entity(tableName = "place") data class PlaceRow(@PrimaryKey val id: String, val payload: ByteArray)
@Entity(tableName = "weekly_card") data class WeeklyCardRow(@PrimaryKey val periodStart: Long, val payload: ByteArray)
```
- `payload` = IV (12 bytes) ‖ ciphertext ‖ tag, `AES/GCM/NoPadding`, AAD = id bytes, over the canonical JSON. `createdAt` stays plaintext for ordering.
- Key: AES-256 in `AndroidKeyStore` (alias `smileproc-db-v1`, GCM, no padding, randomized encryption). **No `setUserAuthenticationRequired`, no `setUnlockedDeviceRequired`**, so notification buttons can save while locked (after the first unlock since boot).
- DB in default (credential-encrypted) app storage. The app is not direct-boot aware: before the first unlock after a restart it doesn't run, and old notifications are gone.
- Room `exportSchema = true`, schemas committed; **after M5, migrations only**, each with a migration test.
- Backups off: `android:allowBackup="false"` and `dataExtractionRules` excluding everything from cloud backup **and** device-to-device transfer.
- DB present but key missing → recovery screen (*Import an export* / *Start fresh*).

#### The check-in notification
- **One channel** `checkin` ("Check-ins"), `IMPORTANCE_DEFAULT`, lock-screen visibility public, `setShowBadge(false)`, created at app start.
- **One ID** `ID_CHECKIN = 1`. `CheckInNotifier.render(state)` posts that ID or cancels it. Nothing else calls `notify`.
- Every state: monochrome face small icon, title "SmileProc", the state's text, `VISIBILITY_PUBLIC`, `CATEGORY_REMINDER`, `setShowWhen(false)`, `setLocalOnly(true)`, `setAutoCancel(false)`, content intent → app on Today (normal unlock), delete intent → `ACTION_DISMISSED`.
- `Faces(alerting)` and `Test`: `setOnlyAlertOnce(false)`. Other states: `setSilent(true)`, `setOnlyAlertOnce(true)`.
- `setOngoing(true)` when `standing` is on. Android 14+ lets people swipe it away while unlocked (not while locked) → `Dismissed`.
- **Face buttons:** three `NotificationCompat.Action`s, order Low, Okay, Good; `PendingIntent.getBroadcast` with request codes 1–3 (test 11–13), explicit `Intent(context, CheckInReceiver::class.java)`, action `ACTION_RATE`, extras `mood` and `test`, `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`; `setAuthenticationRequired(false)` set explicitly; `setShowsUserInterface(false)`.
- No full-screen intent; `MainActivity` is not `showWhenLocked`.
- `POST_NOTIFICATIONS` is requested in onboarding (13+). Without it, rating works only in the app and the Lock-screen check-in screen says so.

#### Saving from a notification tap
`CheckInReceiver` (not exported) handles `ACTION_RATE`, `ACTION_RESTORE`, `ACTION_DISMISSED`:
1. `goAsync()`, then run in the `AppContainer` scope.
2. Load machine state and settings from DataStore.
3. `ACTION_RATE` (not a test): build the `Entry` (`source = notification`, location per logic.md › Location acceptance), seal and insert. Success → `Saved`; any exception → `Failed` (SafeLog the exception type only).
4. Render, persist the state, schedule the 60 s restore (`AlarmManager.set(ELAPSED_REALTIME, …)`, inexact).
5. `finish()` in `finally`. Nothing waits for a location fix.

An in-app rating sends `RatedInApp`.

#### Reminders
- Up to 6 times a day, each with its own ISO weekday selection, in DataStore.
- **Defaults:** 08:00, 12:00, 16:00 and 20:00, every day (ISO 1–7). Onboarding step 3 shows them pre-filled. They are stored, and the next one scheduled, when onboarding is finished or skipped: on first run, and again after Delete all (which returns to onboarding with no alarms). So skipping onboarding keeps them. They are ordinary reminders: the person can change or delete any of them, and deleting them all leaves none (not the defaults).
- One pending alarm: the next occurrence across all reminders, in the device's current zone. `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, …)`, inexact. On fire: `ReminderFired`, then schedule the next.
- `SystemEventsReceiver` (exported; `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`): reschedule; boot and update also send `Booted` / `AppUpdated`.
- A few minutes of drift is fine.

#### Lock-screen check-in (readiness)
One row per check, ✓ or a problem with a one-line fix and a button:

| Check | How | Fix button |
|---|---|---|
| Notifications allowed | `areNotificationsEnabled()`, plus `POST_NOTIFICATIONS` on 13+ | request, else `ACTION_APP_NOTIFICATION_SETTINGS` |
| Check-in channel on | importance ≠ `IMPORTANCE_NONE` | `ACTION_CHANNEL_NOTIFICATION_SETTINGS` |
| Shown on the lock screen | channel lock-screen visibility ≠ `VISIBILITY_SECRET` | `ACTION_CHANNEL_NOTIFICATION_SETTINGS` |
| Keep a check-in on the lock screen | the setting, with a toggle | — |

- **Send a test check-in:** `TestRequested`; the screen says "Lock your phone now"; after 10 s the notification becomes `Test`; a tap → `TestDone`, nothing saved; back in the app the screen shows "It works ✓".
- Returning without a recorded tap → one hint: check that the lock screen shows notifications, and on Samsung that they aren't set to icons only.
- Rows refresh on resume. Problems show only here and in onboarding.

#### Location
- `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`, asked in onboarding (skippable). Approximate-only is too coarse for places; Settings explains.
- In the app: save first, then `LocationManagerCompat.getCurrentLocation` (fused on 12+, else GPS, else network), 10 s timeout, then update the entry.
- From a notification tap: `getLastKnownLocation` across providers. Spike A2 measures how often that works and tries a short location foreground service; the default stays last known, else none, until the human decides.
- Capture location off or no permission → `location = null`, no request.

#### App lock and recents
- `BiometricPrompt` on launch and after 60 s in the background (`ProcessLifecycleOwner`). Android 11+: `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`. Android 10: `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`.
- No screen lock → toggle disabled with an explanation.
- Recents: `setRecentsScreenshotEnabled(false)` on 13+; `FLAG_SECURE` below that while the lock is on.
- The app lock never affects the notification buttons.

#### Export and import (`.smileproc`)
```json
{
  "format": "smileproc-export",
  "version": 1,
  "kdf": { "name": "PBKDF2-HMAC-SHA256", "iterations": 600000, "salt": "<base64, 16 bytes>" },
  "cipher": "AES-256-GCM",
  "payload": "<base64 of nonce(12) ‖ ciphertext ‖ tag(16)>"
}
```
- Plaintext: `{ "exportedAt", "app": {"platform": "android", "version"}, "entries": [Entry…], "places": [PlaceLabel…], "settings": { "reminders", "keepCheckIn", "weeklyCard": {"weekday", "hour", "minute"}, "tagPickerMode", "captureLocation" } }`, canonical format. Weekly cards are rebuilt after import.
- Key = PBKDF2-HMAC-SHA256(password, salt, iterations, 32 bytes) with a software AES key, in `:core`.
- Password ≥ 8 characters, entered twice, with "If you forget this password, the export can't be opened." Derivation off the main thread with progress.
- Storage Access Framework (`CreateDocument` / `OpenDocument`), filename `smileproc-<yyyy-mm-dd>.smileproc`.
- Import merges by id; an existing id is replaced only if the imported `updatedAt` is newer.

#### Delete all
Close and delete the database, delete the Keystore entry, clear DataStore, cancel all alarms, cancel `ID_CHECKIN`, back to onboarding.

#### Manifest (dogfood/release)
- Permissions: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (androidx.biometric adds `USE_BIOMETRIC`).
- Not present: `INTERNET`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE*`.
- Receivers: `CheckInReceiver`, `ReminderReceiver` (not exported); `SystemEventsReceiver` (exported). No app-widget provider, `TileService` or shortcuts.
- `allowBackup="false"`, `dataExtractionRules`, `MainActivity` without `showWhenLocked`.
