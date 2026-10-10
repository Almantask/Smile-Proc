# SmileProc agent notes

Lead platform: **Android**.

The installable build produced in this environment is the Android debug APK. Switch the lead to iOS here if the phone you actually carry is an iPhone; from M2 onward the lead platform is implemented first.

## Non-negotiables

1. **No network.** iOS: no `URLSession`, `URLRequest`, `NWConnection`, `WKWebView`, `CLGeocoder`, `MapKit`. Android: **no `INTERNET` permission in the merged manifest**, no `HttpURLConnection`/`java.net.URL` fetching, OkHttp, Retrofit, Ktor, `WebView`, `Geocoder`, Maps. Only exception: the user tapping a helpline number or link hands off to the phone/browser app.
2. **Dependency allowlist only** (see `docs/implementation-plan.md` §5.2 and `spec/versions.json`). No analytics, crash reporting, ads, Google Play Services or Firebase.
3. **Lock screen, widgets and notifications never show mood data** — no last mood, average, count or streak. Notification text is always generic.
4. **Single tap saves.** Nothing required between the tap and the save.
5. **Encrypted at rest:** every stored payload is AES-256-GCM sealed with a hardware-backed key (iOS Keychain, Android Keystore), on top of the OS file encryption.
6. **No streaks**, no "you missed a check-in", no scores, levels or badges, no guilt copy.
7. **Observations, not diagnoses.** Never use clinical words (depression, disorder, symptom, diagnosis, treatment) in user-facing copy. Low-mood support is gentle, dismissible, and **never sent as a notification**.
8. **No logging of user content.** Never log moods, tags, comments, coordinates or place names, even in debug builds.
9. **Honest privacy copy:** history lives only on this phone, is excluded from backups and phone-to-phone transfer, and is lost with the phone unless the user exports it.
10. **Same behaviour on both platforms.** Pattern results for the same data must match the shared fixtures (`spec/fixtures/`).

## Conventions

- **Same names on both platforms** for models and logic types (`Entry`, `PlaceResolver`, `PatternEngine`, `WeeklyCardBuilder`…). One logic file in Swift corresponds to one file in Kotlin.
- Inject the clock, time zone/calendar, random generators and the sealer; never read the system clock inside core logic.
- Core logic (`SmileCore`, `:core`) has no UI, widget, location or notification imports.
- iOS: `@Observable` view models, `async/await`. Android: `ViewModel` + `StateFlow`, coroutines. No singletons except the app container (`AppEnvironment` / `AppContainer`).
- No force unwraps / `try!` / `!!` outside tests.
- Every user-facing string comes from `spec/strings/en.json`; generated string files are never edited by hand.
- Every tappable element has an accessibility label; support Dynamic Type / font scaling up to the largest sizes, and reduced motion.

## Where the rules live

- Product plan: `docs/implementation-plan.md`
- Engine behavior: `spec/engine-rules.md` and `spec/tools/engine.py`
- Pinned versions: `spec/versions.json`

Helpline numbers in `spec/content/helplines.json` stay `verified: false` until a person checks each one against its official source. Do not add numbers from memory.
