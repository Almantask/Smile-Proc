# C1 · `docs/spec/product.md`

#### What we're building
- **3-face scale:** 1 = Low, 2 = Okay, 3 = Good. Every face is a valid answer.
- **Two ways to rate, and only these:** the **check-in notification** (three face buttons, usable from the lock screen without unlocking) and **the app**. A tap saves immediately; emotion tags and a comment are an optional follow-up in the app.
- **One notification at most, ever.** Its text is "How are you right now?". Reminders use it: by default at 08:00, 12:00, 16:00 and 20:00 every day, and the person can change, add or delete them. With **Keep a check-in on the lock screen** on (pre-selected), it stays there silently between reminders.
- **Location:** raw GPS on every entry when permitted. Entries within ~100 m form one place the user can name. No permission or location off → the rating is saved without location.
- **Emotion tags:** *Gradual* (core feeling → finer one, default) and *Detailed* (full wheel, plus a plain list for TalkBack). The app remembers the last mode.
- **Weekly card:** 2–3 patterns from time of day, weekday, place and tags. Each has a "many people notice…" line, one small tip and a question, from a hand-written library shipped in the app. On-device rules only. Shown in the app; no notification.
- **Private:** no account, no server, no analytics, no `INTERNET` permission. Encrypted at rest. Biometric app lock. Encrypted export. Not in backups.

#### Non-negotiables
1. **No network.** No `INTERNET` permission in the merged manifest. No `HttpURLConnection` / `java.net.URL` fetching, OkHttp, Retrofit, Ktor, `WebView`, `Geocoder`, Maps. Only exception: the user tapping a support contact hands off to the phone, SMS or browser app.
2. **Dependency allowlist only** (android.md › Stack). No analytics, crash reporting, ads, Google Play Services or Firebase.
3. **Two rating paths only:** the check-in notification and the app. No app widgets, Quick Settings tiles, app shortcuts or other entry points.
4. **One notification at most.** One channel for check-ins, one notification ID. No weekly-card, low-mood or "missed check-in" notifications.
5. **The notification never shows mood data.** Its text is only one of: "How are you right now?", "Saved ✓", "It works ✓", "Couldn't save. Open SmileProc."
6. **Single tap saves.** Nothing required between the tap and the save. Location never delays it.
7. **Never a silent failure.** Every notification tap ends in "Saved ✓" or "Couldn't save. Open SmileProc."
8. **Encrypted at rest:** every stored payload is AES-256-GCM sealed with an Android Keystore key, on top of Android's file-based encryption.
9. **No streaks**, no "you missed a check-in", no scores, levels or badges, no guilt copy.
10. **Observations, not diagnoses.** Never use clinical words (depression, disorder, symptom, diagnosis, treatment) in user-facing copy. Support is gentle, dismissible and **in the app only**.
11. **No logging of user content.** Never log moods, tags, comments, coordinates or place names, even in debug builds.
12. **Honest privacy copy:** history lives only on this phone, is excluded from backups and phone-to-phone transfer, and is lost with the phone unless the user exports it.

#### Scope
**In v1:** Today (check-in), History, Entry detail, Week, Places, Support, Settings, Lock-screen check-in (readiness), Onboarding; the check-in notification (reminders, always-ready mode, Saved state, test check-in); location capture, place clustering and naming; gradual + detailed tags, comment, edit/delete; pattern engine, library, weekly card; low-week variant, low-run support card, Support screen; app lock, recents privacy, encrypted export/import, delete all; English UI ready for Italian.

**Out of v1 (do not build):** reflection mode; widgets, Quick Settings tile, app shortcuts, Wear OS; any second notification; sync, accounts, cloud, maps, reverse geocoding; background location and location foreground service (unless the human approves after spike A2); Health Connect; tablet layouts; skipping a reminder because the user rated recently.
