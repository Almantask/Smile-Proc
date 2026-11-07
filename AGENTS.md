# SmileProc (Android) — Antigravity Workspace Rules

Private mood check-in app: Kotlin, Jetpack Compose, Room, zero network.

- **Plan:** `android-implementation-plan.md` (referred to inside the plan as `docs/plan.md`). Milestones are in **Part D**.
- **Spec:** `docs/spec/` — read only the spec files a task needs.
- **Progress:** `docs/progress.md` — **read at the start of every session**.
- **Customizations:** Antigravity customizations in `.agents/` (`skills/`, `agents/`, `rules/`, `hooks/`, `hooks.json`) mirror `.claude/` so workflows behave identically across Antigravity and Claude Code. Keep both in sync and never break `.claude/`.

## Shell & Commands (Antigravity on Windows vs Unix)

On Windows, Antigravity's `run_command` runs **PowerShell**, where bare `bash` invokes WSL (`C:\Windows\System32\bash.exe`) instead of Git Bash. Always run repo shell scripts via **Git Bash** using single-quoted strings (so PowerShell does not interpolate `$` variables):

- **Fast privacy guard (no Gradle):** `& "$env:ProgramFiles\Git\bin\bash.exe" -c 'scripts/privacy-guard.sh'` (Unix: `scripts/privacy-guard.sh`)
- **Core unit tests:** `& "$env:ProgramFiles\Git\bin\bash.exe" -c './gradlew :core:test'` (single class: `--tests "*PlaceResolverTest"`)
- **Full verification gate:** `& "$env:ProgramFiles\Git\bin\bash.exe" -c 'scripts/check.sh'` (privacy-guard, fixtures, JSON check, unit tests, lint, debug + dogfood APKs, classpath, permissions, and manifest checks)
- **Fixtures:** `& "$env:ProgramFiles\Git\bin\bash.exe" -c 'scripts/py tools/generate_fixtures.py --check'` (`scripts/py` resolves a working Python 3 interpreter, avoiding Windows Store `python3` stubs)
- **Goldens:** `WRITE_GOLDENS=1 ./gradlew :core:test` — **only when the human explicitly asks**
- **Emulator helpers:** `scripts/emu/*` — activate and follow the `android-emulator` skill (`.agents/skills/android-emulator/SKILL.md`)
- **File edits:** Always use `write_file` / `edit_file` (never Bash heredocs, which collapse `\\` to `\`).

## Non-negotiables (full list: `docs/spec/product.md`)

- **IMPORTANT — Zero network:** No `INTERNET` permission, HTTP clients (`okhttp3`, `retrofit2`, `ktor`, `HttpURLConnection`), `WebView`, `Geocoder`, Google Play Services, or Firebase. Blocked by `.agents/hooks/pre_tool_guard.py` and `scripts/privacy-guard.sh`.
- **Entry points:** Rating happens only through the check-in notification and the app. No widgets, Quick Settings tiles, or app shortcuts.
- **Single notification invariant:** At most one notification, ever — only `CheckInNotifier.render(state)` posts or cancels, always channel `checkin` and `ID_CHECKIN`.
- **Lock-screen privacy:** Notification text is strictly `"How are you right now?"`, `"Saved ✓"`, `"It works ✓"`, or `"Couldn't save. Open SmileProc."` — never mood data. No full-screen intents or `showWhenLocked` activities.
- **Immediate, reliable save:** A face tap saves immediately while locked (`goAsync()`, Keystore key without auth/unlock requirement); location is last-known only and never delays or fails a save; never a silent failure.
- **Sealed storage:** Every stored payload is AES-GCM sealed (`IV(12) || ciphertext || tag`, `AAD = row id bytes`).
- **Zero sensitive logging:** Never log moods, tags, comments, coordinates, or place names, even in debug. Use `SafeLog` (event names and exception class names only; `Log.*` and `println` are forbidden in `app/src/main`).
- **Supportive, non-clinical copy:** No streaks, scores, guilt copy, `"You should…"`, clinical terms, or `!` on low-mood screens. Support lives inside the app only. Never invent support contacts, phone numbers, links, or legal text (`content/support.json` is human-owned).

## Architecture & Scoped Rules

- **`:core` (`.agents/rules/core.md`):** Pure Kotlin/JVM — zero `android.*` or `androidx.*` imports. Inject `Clock`, `ZoneId`, `Random`, and `Sealer`. Compute dates/dayparts/ISO weekdays (`1 = Monday`) in each entry's `timeZoneID`. Thresholds come only from `content/pattern-config.json`.
- **`:app` (`.agents/rules/notifications.md`, `.agents/rules/storage.md`, `.agents/rules/ui.md`):** Compose UI (200% font scale, dark mode, content descriptions, all strings in `strings.xml`), Room (migrations required after M5), `KeystoreSealer`, `CheckInNotifier`, receivers, alarms. Debug-only receivers live in `app/src/debug`.
- **`content/` & `fixtures/`:** `content/` JSON loads at runtime as assets; `fixtures/` holds shared hand-checkable test data. Never hand-edit `.golden.json` files or `.claude/settings.json` / `.claude/hooks/` / `content/support.json`.

## Devices & Environment

- Debug builds use `com.example.smileproc.debug` (`"SmileProc (debug)"`). **Never install over, clear, or uninstall `com.example.smileproc`** (the human's daily dogfood app). Never read `*.jks` or `*.keystore`.
- `scripts/lib/env.sh` automatically resolves `ANDROID_HOME` and a JDK 17+ (`JAVA_HOME` / Android Studio JBR); `scripts/emu/env.sh` targets `$SMILEPROC_SERIAL` (never run plain `adb` without `-s`).
- Cloud / SDK-less sessions lack Android SDK, `adb`, and emulators: run only pure-Kotlin work (`:core`, `fixtures/`, `content/`, `docs/`), record `:app` builds and `E*` scenarios as `not run (cloud)` in `docs/progress.md`, and push before ending.

## Milestone Workflow

- One milestone per session via `/milestone M<n>` (`.agents/skills/milestone/SKILL.md`): check `docs/progress.md` → read scoped spec → plan and wait for human approval → commit small green steps directly on `main` → pass `scripts/check.sh` → run `emulator-tester`, `spec-reviewer`, and (when copy changed) `copy-reviewer` subagents (`.agents/agents/`) → update `docs/progress.md` & `docs/device-test-checklist.md` → tag `m<n>-claude-verified` when fully verified.
- Fix root causes; never weaken a hook, guard, test, threshold, or golden to pass. Ask before changing `content/pattern-config.json`.
- If an Android API differs from `docs/spec/android.md`, document the behaviour in `docs/spikes.md` and stop for human guidance.
