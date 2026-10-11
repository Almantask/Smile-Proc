# Device test checklist

These need a real phone. M0 has no behavior to check here yet. Each item is done in the milestone that builds it.

- [ ] Phone locked: each face button saves; the entry appears after unlocking; nothing about mood is visible on the lock screen. (M5)
- [ ] Standing notification visible on the lock screen with default settings, silent; “Saved ✓” for about a minute, then the faces again. (M5)
- [ ] A reminder alerts on the same notification; there is never a second SmileProc notification. (M5)
- [ ] Android 14+: swipe away while unlocked, gone until the next reminder; can’t be swiped while locked. (M5)
- [ ] Restart: after the first unlock the standing notification is back and reminders still fire. (M5)
- [ ] Notifications denied, channel off, or lock-screen notifications off: the Lock-screen check-in screen shows the problem and the fix; the test check-in confirms the fix. (M5)
- [ ] Samsung: buttons reachable with lock-screen notifications set to icons only and to details. (M5)
- [ ] Location: fresh fix in the app; notification-tap result recorded (fresh / cached / none); location off, ratings still save. (M4, M5)
- [ ] Airplane mode: everything works the same. (M5)
- [ ] App lock after 60 seconds in the background; recents show no content. (M9)
- [ ] Export, uninstall, reinstall, import: history restored. (M9)
- [ ] Delete all: key gone. Reinstall shows onboarding, not the recovery screen. (M9)
- [ ] TalkBack through check-in, tags (list mode), the weekly card, and the notification buttons. (M6, M10)
- [ ] Phone-to-phone transfer or backup does not carry SmileProc data. (M9)
- [ ] Reminders within a few minutes of the set time after an overnight idle. (M5, spike A4)
