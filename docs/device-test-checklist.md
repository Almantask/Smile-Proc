# Device test checklist

Mark each line on the phone you tried. Simulator and emulator results do not replace these.

1. Notification buttons while locked create an entry that appears after unlock. The lock screen does not show a mood, count, or streak.
2. iPhone lock-screen widget tap while locked does the same. The widget never shows which face was tapped.
3. iPhone, immediately after a restart and before the first unlock: the widget says "Unlock to save" and does not crash.
4. Location: a fresh fix arrives within about 10 seconds in the app. Widget and notification captures record fresh, cached, or none.
5. Airplane mode: logging, history, the weekly card, and export still work.
6. App lock engages after 60 seconds in the background. The app switcher does not show check-ins.
7. Export, uninstall, reinstall, import. History comes back. An iPhone export opens on Android, and the reverse.
8. Delete all data. Reinstall shows onboarding, not the recovery screen.
9. The weekly notification arrives at the chosen time and says only "Your week is ready". Android reminders after an idle night are within a few minutes.
10. VoiceOver and TalkBack can complete a check-in, pick a tag from the list, read a weekly card, and use the widget.
11. Android backup and phone-to-phone transfer do not carry SmileProc data.

Release stays blocked while any helpline in `spec/content/helplines.json` has `"verified": false`.
