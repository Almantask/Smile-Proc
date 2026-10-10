# Device spikes

Code for these checks lives behind the debug screen on each platform. Record what the phone actually did. Do not work around a non-negotiable if a spike fails; write the result here and stop that path.

| Spike | What to do | Result |
|---|---|---|
| iOS S1 | Lock the phone. Tap a lock-screen face and a reminder action. Unlock. Debug → Inbox should list the new files, written with complete-unless-open protection. | |
| iOS S2 | From the lock screen, log once from the widget and once from a reminder. After unlock, record fresh / cached / none, accuracy, and age for each. | |
| iOS S3 | Confirm three faces fit and save on the rectangular lock-screen widget. "✓ Saved" shows for about 60 seconds, then the widget returns to the three faces. It must not highlight which face was tapped. | |
| Android A1 | Lock the phone after it has been unlocked once since boot. Tap a reminder face. The entry is in History after unlock. The lock screen text stays generic. | |
| Android A2 | From a reminder action, record (a) last-known location in the receiver and (b) a short location foreground service. Note fresh / cached / none. Default until you choose is (a). | |
| Android A3 | Home-screen widget: three faces save, then "✓ Saved" for about 60 seconds. If the phone can show lock-screen widgets, note whether they work while locked. | |
| Android A4 | Set a reminder, leave the phone idle overnight, and note how many minutes late it fired. | |

## Decisions still open

- iOS location "Always": not approved. When In Use only.
- Android background location / foreground service: not approved. Notification and widget taps use last-known location, or no location.
- Helplines: not verified.
