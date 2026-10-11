# Spikes

Filled in M1 (android-implementation-plan.md, Part D › M1), and whenever an Android API behaves differently from the spec (then: write it up here and stop).

The spike app is `:spikes` (`com.example.smileproc.spikes`), throwaway and never shipped.

## A1 — Faces notification on the lock screen
- **Question:** are the silent and alerting variants visible on the lock screen, and do the face buttons save without unlocking?
- **Steps:** —
- **Results:** —

## A2 — Location from a notification tap
- **Question:** (a) how often does last known location work inside the receiver; (b) what does a short location foreground service started from a button return (fresh / cached / none, accuracy, age)?
- **Steps:** —
- **Results:** —
- **Human decision:** last known only, or a short location foreground service (Appendix 3). —

## A3 — Swipe-away and reboot
- **Question:** can the ongoing notification be swiped away while locked; on API 34+ while unlocked (does the delete intent fire); does it come back after reboot via `BOOT_COMPLETED`?
- **Steps:** —
- **Results:** —

## A4 — Reminder under Doze (short form)
- **Question:** how late is an inexact `setAndAllowWhileIdle` reminder set 3 minutes ahead under forced idle?
- **Steps:** —
- **Results:** —

## Results table
| Spike | Device / API | Variant | Result | Notes |
|---|---|---|---|---|
