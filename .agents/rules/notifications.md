---
trigger: model_decision
description: Apply when working on notification rendering, BroadcastReceivers, alarms, or AndroidManifest.xml (app/src/main/**/notify/**, *Receiver*.kt, *Notifier*.kt, app/src/main/AndroidManifest.xml).
---

# Notification Rules (`notify/**`, `*Receiver*.kt`, `*Notifier*.kt`, `AndroidManifest.xml`)

- Spec: `docs/spec/android.md` (*The check-in notification*; *Saving from a notification tap*; *Reminders*).
- One channel `checkin`, one notification ID `ID_CHECKIN`. Only `CheckInNotifier.render(state)` posts or cancels notifications.
- State comes from `CheckInMachine` (persisted in DataStore). BroadcastReceivers dispatch events; they never construct or post notifications directly.
- Notification text is strictly one of: `"How are you right now?"`, `"Saved ✓"`, `"It works ✓"`, or `"Couldn't save. Open SmileProc."`
- Face action buttons use explicit broadcast `PendingIntent`s with `FLAG_IMMUTABLE`, `setAuthenticationRequired(false)`, and `setShowsUserInterface(false)`.
- BroadcastReceivers call `goAsync()`, never block waiting for a location fix, and always call `finish()` in `finally`.
- No full-screen intents and no activity shown over the lock screen (`showWhenLocked` is forbidden).
