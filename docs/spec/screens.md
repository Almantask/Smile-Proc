# C5 · `docs/spec/screens.md`

**Tabs:** Today · Week · Settings (History opens from Today). Material 3: bottom navigation bar, sheets, snackbars.

- **Today:** "How are you right now?" with three large faces (`FaceView`, custom-drawn). A tap saves at once with a light haptic, then shows the follow-up sheet: "Saved" header, tag picker in the remembered mode with a mode switch, comment field, Done. Dismissing keeps the entry. Below: the low-run support card (when armed), today's entries, "All entries".
- **Gradual tag picker:** step 1 "What's closest?" (7 core chips) → step 2 finer chips plus "Just {core}". Up to 3 tags; selected chips stay visible at the top.
- **Detailed (wheel):** inner ring of 7 cores, outer ring of 35 fine tags, diameter `min(width − 32, 360)` dp. Tap selects; chips echo the selection. With TalkBack on or at the largest font sizes, show the list picker (sections by core).
- **History:** grouped by day; face, time, tags, place name, comment snippet. **Entry detail:** change mood/tags/comment, name this place, delete (confirmation).
- **Week:** latest card (7-day strip of face counts → optional low-week block → 2–3 items with title, observation, "many people notice…", tip, question, "Name this place" when unnamed) and past cards. Before the first card: "Your first weekly card arrives {Sunday at 19:00}." A dot on the tab until a new card is seen.
- **Places:** rename, change kind, delete label (entries fall back to unnamed clusters), **Remove location from all entries** (confirmation).
- **Support:** gentle copy, then `support.json` contacts for the device region (if any); taps open the phone, SMS or browser app.
- **Lock-screen check-in:** android.md › Lock-screen check-in.
- **Settings:** Reminders · Keep a check-in on the lock screen · Lock-screen check-in · Weekly card time · Places · Capture location · App lock · Export · Import · Privacy ("What's stored and where") · Delete all data · About.
- **Onboarding (5 screens):** (1) privacy promise, including "not in backups — export to keep a copy"; (2) location → permission (skippable; ratings still save without it); (3) reminders → notification permission, pick times, **Keep a check-in on the lock screen** (pre-selected); (4) App lock toggle (pre-selected ON); (5) Lock-screen check-in with **Send a test check-in**.

**Copy**
- Faces: **Low / Okay / Good**.
- Notification: title "SmileProc"; text "How are you right now?" / "Saved ✓" / "It works ✓" / "Couldn't save. Open SmileProc."
- Low-week block: "This week looked heavy. That's worth noticing, and it's okay." + "Talking to someone you trust can make it lighter." → **Talk to someone**.
- Low-run card: "Your last few check-ins have been low. If it would help, talking to someone can make it lighter." → **See support options** · **Not now**.
- Privacy: nothing leaves the phone unless the user exports a file or taps a support contact; history isn't in backups or phone-to-phone transfers; a lost phone means lost history without an export.
