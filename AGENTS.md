# SmileProc agent notes

These rules stay in force for every change. If the implementation plan and the idea doc disagree, the idea doc wins: stop and tell the human.

## Non-negotiables

1. **No network.** No `INTERNET` permission in the merged manifest. No `HttpURLConnection` / `java.net.URL` fetching, OkHttp, Retrofit, Ktor, `WebView`, `Geocoder`, Maps. Only exception: the user tapping a support contact hands off to the phone, SMS or browser app.
2. **Dependency allowlist only** (§4.2). No analytics, crash reporting, ads, Google Play Services or Firebase.
3. **Two rating paths only:** the check-in notification and the app. No app widgets, Quick Settings tiles, app shortcuts or other entry points.
4. **One notification at most.** SmileProc never shows more than one notification at a time: one channel for check-ins, one notification ID (§7.3). No weekly-card, low-mood or "missed check-in" notifications.
5. **The notification never shows mood data.** Its text is only one of: "How are you right now?", "Saved ✓", "It works ✓", "Couldn't save. Open SmileProc."
6. **Single tap saves.** Nothing required between the tap and the save. Location never delays it.
7. **Never a silent failure.** Every notification tap ends in "Saved ✓" or "Couldn't save. Open SmileProc."
8. **Encrypted at rest:** every stored payload is AES-256-GCM sealed with an Android Keystore key, on top of Android's file-based encryption.
9. **No streaks**, no "you missed a check-in", no scores, levels or badges, no guilt copy.
10. **Observations, not diagnoses.** Never use clinical words (depression, disorder, symptom, diagnosis, treatment) in user-facing copy. Support is gentle, dismissible and **in the app only**.
11. **No logging of user content.** Never log moods, tags, comments, coordinates or place names, even in debug builds.
12. **Honest privacy copy:** history lives only on this phone, is excluded from backups and phone-to-phone transfer, and is lost with the phone unless the user exports it.

## Conventions

- `:core` has **no Android imports**. Inject the clock, time zone, random generators and the `Sealer`; never read the system clock inside core logic.
- `ViewModel` + `StateFlow`, coroutines. No singletons except `AppContainer`, which owns one app-wide `CoroutineScope`.
- No `!!` outside tests.
- Every user-facing string lives in `res/values/strings.xml`.
- Every tappable element has a content description. Support font scaling up to 200% and reduced motion. Colour is never the only signal.
