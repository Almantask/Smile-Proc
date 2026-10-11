# SmileProc (Android)

Private mood check-in app: Kotlin, Jetpack Compose, Room, no network.
- Plan: android-implementation-plan.md (its text calls it docs/plan.md). Milestones are Part D.
- Spec: docs/spec/ — read only the files a task needs. Until M0 has split it out, read the matching section of plan Part C.
- Progress: docs/progress.md (created in M0) — read it at the start of every session.
- CLAUDE.md, .claude/rules, .claude/skills and .claude/agents already exist and replace plan B.2 and B.6–B.9; M0 keeps them. M0 still creates the hooks and settings.json (B.3–B.5).

## Commands
- Fast guard (no Gradle): `scripts/privacy-guard.sh`
- Core tests: `./gradlew :core:test` (one class: `--tests '*PlaceResolverTest'`)
- Everything: `scripts/check.sh` (guard, fixtures, unit tests, lint, debug + dogfood builds, manifest and dependency checks)
- Fixtures: `scripts/py tools/generate_fixtures.py` (`--check` to verify). `scripts/py` runs python3, or `py -3` where python3 is a Windows Store stub.
- Goldens: `WRITE_GOLDENS=1 ./gradlew :core:test` — only when the human asks
- Emulator: `scripts/emu/*` — see the android-emulator skill

## Non-negotiables (full list: docs/spec/product.md)
- IMPORTANT: the app has no network. No INTERNET permission, HTTP clients, WebView, Geocoder, Play Services or Firebase. From M0 a hook blocks these.
- Rating only through the check-in notification and the app. No widgets, tiles or shortcuts.
- At most one notification, ever: only `CheckInNotifier` posts, always `ID_CHECKIN`.
- Notification text never shows mood data.
- A tap saves immediately; location never delays a save; never a silent failure.
- Every stored payload is sealed (AES-GCM, Keystore key).
- No logging of moods, tags, comments, coordinates or place names, even in debug. Use `SafeLog` (event names and exception types only).
- No streaks, guilt copy or clinical words. Support lives in the app only.
- Never invent support contacts or legal text.

## Architecture
- `:core` pure Kotlin/JVM: no android.* or androidx.* imports; inject Clock, ZoneId, Random and Sealer.
- `:app`: Compose UI, Room, KeystoreSealer, CheckInNotifier, receivers, alarms. Debug-only tools live in app/src/debug.
- `content/` JSON is loaded at runtime (assets source dir). `fixtures/` is shared test data.

## Devices
- Debug builds are `com.example.smileproc.debug` ("SmileProc (debug)"). Never install over, clear or uninstall `com.example.smileproc`: it is the human's daily app.
- Scripts in scripts/emu target `$SMILEPROC_SERIAL` (default: the running emulator). Don't call plain `adb` without `-s`.
- Local Windows machine: scripts run in Git Bash; `JAVA_HOME` (JDK 17+) and `ANDROID_HOME` come from `.claude/settings.local.json` (not committed). In Bash heredocs `\\` collapses to `\`: write files with backslashes through Write/Edit.
- Cloud sessions have no Android SDK, adb or emulator. There, do only pure-Kotlin work (`:core`, fixtures, content, docs): M2 and the non-Android steps of M0. Record every `:app` build and emulator scenario you couldn't run as "not run (cloud)" in docs/progress.md. Push before stopping: the container is discarded.

## Workflow
- One milestone per session, started with `/milestone M<n>`. Plan first, commit in small green steps, finish with check.sh, the emulator-tester, spec-reviewer and (when copy changed) copy-reviewer subagents, a progress.md update and a tag.
- Use a subagent for open-ended API research so the main context stays on implementation.
- Fix root causes. Never weaken a hook, test, threshold or golden to make something pass. Ask before changing content/pattern-config.json.
- After M5, Room schema changes only through migrations.
- If an Android API behaves differently from the spec: write it up in docs/spikes.md and stop.
- When compacting, keep: the current milestone, files changed, failing tests and the exact commands used.
