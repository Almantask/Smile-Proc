---
paths:
  - "app/src/main/**/notify/**"
  - "app/src/main/**/*Receiver*.kt"
  - "app/src/main/**/*Notifier*.kt"
  - "app/src/main/AndroidManifest.xml"
---
# Notification rules
- Spec: docs/spec/android.md (The check-in notification; Saving from a notification tap; Reminders).
- One channel `checkin`, one ID `ID_CHECKIN`. Only CheckInNotifier.render(state) posts or cancels it.
- State comes from CheckInMachine (persisted in DataStore). Receivers send events; they never build notifications.
- Text is one of: "How are you right now?", "Saved ✓", "It works ✓", "Couldn't save. Open SmileProc."
- Face actions: explicit broadcast PendingIntents, FLAG_IMMUTABLE, setAuthenticationRequired(false), setShowsUserInterface(false).
- Receivers use goAsync(), never wait for a location fix, and always finish().
- No full-screen intents. No activity shows over the lock screen.
