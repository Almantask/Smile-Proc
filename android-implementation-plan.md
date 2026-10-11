# SmileProc — Android Plan for Claude Code (v1)

> **Product:** the same decisions as `claude/android-implementation-plan.md` and the *SmileProc — idea* doc (https://claude.ai/code/artifact/b09816d2-dad0-462e-b1c1-1b0e4c04ffef), as of 2026-10-11. If anything here disagrees with the idea doc, the idea doc wins: stop and tell the human.
> **Built for Claude Code:** the repo keeps a short `CLAUDE.md`, enforces the non-negotiables with hooks, loads the detailed spec only when a task needs it, and has Claude verify its own work on an Android emulator. The human is needed for real-phone checks and product decisions.
> **Run Claude Code locally**, on the machine with Android Studio. Emulator steps need local hardware acceleration; a cloud session can only do the pure-Kotlin milestones.

**How this file is organized**
- **Part A — Start here** (the human, about 10 minutes)
- **Part B — Claude Code setup** (files M0 creates verbatim)
- **Part C — Spec** (binding; M0 splits it into `docs/spec/`)
- **Part D — Milestones** (each says what to read, build, and how Claude and the human verify it)
- **Appendix — Decisions for the human**

---

# Part A — Start here (human)

1. Install Android Studio (latest stable) with SDK Platform-Tools and the Emulator, JDK 17, Python 3 and Claude Code.
2. Create the repo: `mkdir smileproc-android && cd smileproc-android && git init`. Save this file as `docs/plan.md` and commit it.
3. Run `claude` in the repo root and send: **`Read docs/plan.md and run Milestone M0 exactly as written.`**
4. From M1 on, start each milestone in a fresh session (`/clear`, then `/milestone M1`, `/milestone M2`, …).
5. At the end of each milestone, read Claude's summary, do the **Human checks**, then reply **"M<n> accepted"** or say what's wrong.
6. **Your real phone is safe.** Claude's test app is *SmileProc (debug)* (`com.example.smileproc.debug`). Your daily app is the *dogfood* build (`com.example.smileproc`). Claude never installs, clears or uninstalls the dogfood app, and every adb command aimed at a real phone asks for your approval.

---

# Part B — Claude Code setup (created in M0)

### B.1 Repository layout
```
smileproc-android/
  CLAUDE.md                       # B.2 — always loaded, kept short
  .claude/
    settings.json                 # B.3 — permissions + hooks (written last in M0)
    hooks/guard_edit.py           # B.4 — PreToolUse: blocks non-negotiable violations
    hooks/stop_check.sh           # B.5 — Stop: fast guard + core tests before Claude stops
    rules/core.md                 # B.6 — path-scoped rules, load only for matching files
    rules/notifications.md
    rules/ui.md
    rules/storage.md
    skills/milestone/SKILL.md     # B.7 — /milestone M<n>
    skills/android-emulator/SKILL.md   # B.8 — how to drive the emulator
    agents/spec-reviewer.md       # B.9 — reviews a milestone diff against the spec
    agents/emulator-tester.md     # B.9 — runs emulator scenarios, reports evidence
  docs/
    plan.md                       # this file
    spec/product.md data.md logic.md android.md screens.md testing.md   # Part C, split in M0
    progress.md  spikes.md  device-test-checklist.md
  content/                        # runtime JSON (data.md)
  fixtures/                       # shared test data (data.md)
  tools/generate_fixtures.py
  scripts/
    check.sh  privacy-guard.sh
    emu/ env.sh start.sh install.sh lock.sh wake.sh unlock.sh notifs.py tap.py shot.sh
         probe.sh fire.sh geo.sh idle.sh reboot.sh
  settings.gradle.kts  build.gradle.kts  gradle/libs.versions.toml
  core/                           # pure Kotlin/JVM
  app/                            # Android app (src/main, src/debug)
  spikes/                         # M1 only: throwaway spike app, own applicationId
```

### B.2 `CLAUDE.md`
Create exactly this (keep it under ~80 lines; detail lives in `docs/spec/`, rules and skills):

````markdown
# SmileProc (Android)

Private mood check-in app: Kotlin, Jetpack Compose, Room, no network.
- Plan: docs/plan.md. Spec: docs/spec/ — read only the files a task needs.
- Progress: docs/progress.md — read it at the start of every session.

## Commands
- Fast guard (no Gradle): `scripts/privacy-guard.sh`
- Core tests: `./gradlew :core:test` (one class: `--tests '*PlaceResolverTest'`)
- Everything: `scripts/check.sh` (guard, fixtures, unit tests, lint, debug + dogfood builds, manifest and dependency checks)
- Fixtures: `python3 tools/generate_fixtures.py` (`--check` to verify)
- Goldens: `WRITE_GOLDENS=1 ./gradlew :core:test` — only when the human asks
- Emulator: `scripts/emu/*` — see the android-emulator skill

## Non-negotiables (full list: docs/spec/product.md)
- IMPORTANT: the app has no network. No INTERNET permission, HTTP clients, WebView, Geocoder, Play Services or Firebase. A hook blocks these.
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

## Workflow
- One milestone per session, started with `/milestone M<n>`. Plan first, commit in small green steps, finish with check.sh, the emulator-tester and spec-reviewer subagents, a progress.md update and a tag.
- Use a subagent for open-ended API research so the main context stays on implementation.
- Fix root causes. Never weaken a hook, test, threshold or golden to make something pass. Ask before changing content/pattern-config.json.
- After M5, Room schema changes only through migrations.
- If an Android API behaves differently from the spec: write it up in docs/spikes.md and stop.
- When compacting, keep: the current milestone, files changed, failing tests and the exact commands used.
````

### B.3 `.claude/settings.json`
Write this **last** in M0, after the scripts and hooks exist. Replace `com.example.smileproc` in the two deny rules when the human sets the real `applicationId`.

```json
{
  "permissions": {
    "allow": [
      "Bash(./gradlew *)",
      "Bash(scripts/*)",
      "Bash(python3 tools/generate_fixtures.py *)",
      "Bash(python3 scripts/emu/*)",
      "Bash(adb -s emulator-* *)",
      "Bash(adb devices *)",
      "Bash(emulator *)",
      "Bash(git status *)",
      "Bash(git diff *)",
      "Bash(git log *)",
      "Bash(git add *)",
      "Bash(git commit *)",
      "Bash(git tag *)"
    ],
    "ask": [
      "Bash(adb * uninstall *)",
      "Bash(adb * pm clear *)",
      "Bash(git reset *)",
      "Bash(git checkout -- *)"
    ],
    "deny": [
      "Bash(*uninstall com.example.smileproc)",
      "Bash(*pm clear com.example.smileproc)",
      "Bash(git push *)",
      "Read(**/*.jks)",
      "Read(**/*.keystore)"
    ]
  },
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write|MultiEdit",
        "hooks": [
          { "type": "command", "command": "python3 \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/guard_edit.py" }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          { "type": "command", "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/stop_check.sh", "timeout": 300 }
        ]
      }
    ]
  }
}
```

### B.4 `.claude/hooks/guard_edit.py` (PreToolUse)
Blocks an edit before it lands. Exit 2 blocks the tool call and shows the reason to Claude. Edits made through Bash bypass it, which is why `stop_check.sh` and `check.sh` run the same rules again.

```python
#!/usr/bin/env python3
"""PreToolUse guard for Edit/Write/MultiEdit. Exit 2 = block, reason on stderr."""
import json
import re
import sys

data = json.load(sys.stdin)
ti = data.get("tool_input") or {}
path = (ti.get("file_path") or "").replace("\\", "/")
parts = [ti.get("content") or "", ti.get("new_string") or ""]
parts += [e.get("new_string") or "" for e in ti.get("edits") or [] if isinstance(e, dict)]
text = "\n".join(parts)


def block(reason):
    print(f"Blocked by .claude/hooks/guard_edit.py: {reason}. See docs/spec/product.md.", file=sys.stderr)
    sys.exit(2)


# Human-owned files
for owned in (".claude/settings.json", ".claude/hooks/", "content/support.json"):
    if owned in path:
        block(f"{owned} is human-owned; ask the human to change it")
if re.search(r"fixtures/.+\.golden\.json$", path):
    block("goldens are written only by a WRITE_GOLDENS=1 test run the human asked for")

# The M1 spike app is throwaway and exempt from product rules
if "/spikes/" in "/" + path:
    sys.exit(0)

if path.endswith((".kt", ".kts", ".xml", ".toml")):
    forbidden = {
        r"okhttp3|retrofit2|io\.ktor": "HTTP clients",
        r"java\.net\.(URL|HttpURLConnection)\b|HttpsURLConnection": "network APIs",
        r"android\.webkit\.WebView|<WebView": "WebView",
        r"android\.location\.Geocoder": "Geocoder",
        r"com\.google\.android\.gms|com\.google\.firebase|firebase-": "Play Services and Firebase",
        r"android\.permission\.INTERNET": "the INTERNET permission",
        r"android\.appwidget|appwidget-provider|androidx\.glance": "widgets",
        r"android\.service\.quicksettings|TileService": "Quick Settings tiles",
        r"ShortcutManager|android\.app\.shortcuts": "app shortcuts",
        r"ACCESS_BACKGROUND_LOCATION|SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM": "permissions outside the spec",
        r"FOREGROUND_SERVICE": "foreground services (only if the human approves spike A2)",
        r"showWhenLocked\s*=\s*\"true\"|setShowWhenLocked\(\s*true": "activities over the lock screen",
    }
    for pattern, what in forbidden.items():
        if re.search(pattern, text):
            block(f"not allowed: {what}")

if path.endswith(".kt"):
    if "/core/src/" in "/" + path and re.search(r"^\s*import\s+(android|androidx)\.", text, re.M):
        block(":core must stay pure Kotlin/JVM (no android.* or androidx.* imports)")
    if "/app/src/main/" in "/" + path and not path.endswith("/SafeLog.kt"):
        if re.search(r"android\.util\.Log\b|\bLog\.[vdiwe]\(|\bprintln\(", text):
            block("logging in main sources; use SafeLog with event names and exception types only")
    if not path.endswith("/CheckInNotifier.kt") and re.search(r"\.notify\(\s*[\w.]+\s*,", text):
        block("only CheckInNotifier may post notifications (one notification, one ID)")

sys.exit(0)
```

### B.5 `.claude/hooks/stop_check.sh` (Stop)
Runs when Claude is about to stop. Exit 2 keeps Claude working with the reason. It runs the fast guard always, and `:core` tests only when core code, content or fixtures have uncommitted changes. Claude Code caps consecutive Stop blocks, so a problem Claude can't fix ends with a report, not a loop.

```bash
#!/usr/bin/env bash
set -uo pipefail
cd "$CLAUDE_PROJECT_DIR" || exit 0
[ -x scripts/privacy-guard.sh ] || exit 0

if ! out=$(scripts/privacy-guard.sh 2>&1); then
  echo "privacy-guard failed. Fix this before stopping:" >&2
  echo "$out" | tail -n 30 >&2
  exit 2
fi

if git status --porcelain -- core/ content/ fixtures/ 2>/dev/null | grep -q .; then
  if ! out=$(./gradlew -q :core:test 2>&1); then
    echo ":core tests fail. Fix them before stopping (don't weaken tests or goldens):" >&2
    echo "$out" | tail -n 40 >&2
    exit 2
  fi
fi
exit 0
```

### B.6 `.claude/rules/` (path-scoped; each loads only when Claude touches matching files)

`.claude/rules/core.md`
```markdown
---
paths:
  - "core/**"
  - "fixtures/**"
  - "content/**"
---
# Core rules
- Spec: docs/spec/logic.md and docs/spec/data.md.
- Pure Kotlin/JVM. Inject Clock, ZoneId, Random and Sealer; never read the system clock in core logic.
- Day parts, weekdays and dates are computed in each entry's own timeZoneID. Weekdays are ISO (1 = Monday).
- Sort candidates by confidence, then score rounded to 9 decimals descending, then id ascending.
- Thresholds come only from content/pattern-config.json; no defaults in code.
- Fixture expectations are hand-checkable. Never edit a golden by hand; never regenerate one to make a test pass.
- CheckInMachine is a pure reducer: every effect list holds at most one post-or-cancel of ID_CHECKIN.
```

`.claude/rules/notifications.md`
```markdown
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
```

`.claude/rules/ui.md`
```markdown
---
paths:
  - "app/src/main/**/ui/**"
  - "app/src/main/res/**"
---
# UI rules
- Spec: docs/spec/screens.md.
- Every string in res/values/strings.xml. Every tappable element has a content description.
- Works at 200% font scale and in dark mode; colour is never the only signal; respect reduced motion.
- Copy: plain, warm, short. "Many people notice…", "Try…", "If you can…". Never "You should…".
- No clinical words, no numbers that grade the user, no exclamation marks on low-mood screens.
- Faces are Low / Okay / Good and every face is shown as a valid answer.
```

`.claude/rules/storage.md`
```markdown
---
paths:
  - "app/src/main/**/data/**"
  - "app/schemas/**"
  - "core/src/main/**/crypto/**"
---
# Storage rules
- Spec: docs/spec/android.md (Storage and encryption; Export and import).
- Every payload: IV(12) ‖ ciphertext ‖ tag, AES/GCM/NoPadding, AAD = row id bytes, over canonical JSON.
- Keystore key: no user-authentication or unlocked-device requirement (notification taps save while locked).
- After M5 (dogfood build): never edit an existing Room schema version; add a migration and a migration test.
- Missing key with an existing DB → recovery screen. Never crash, never wipe silently.
```

### B.7 `.claude/skills/milestone/SKILL.md`
````markdown
---
name: milestone
description: Run one milestone of docs/plan.md end to end — plan, build, verify, review, commit. Started by the human with /milestone M<n>.
argument-hint: M<n>
disable-model-invocation: true
---
Run milestone $ARGUMENTS from docs/plan.md, Part D.

1. Read docs/progress.md. If the previous milestone isn't marked "accepted", stop and say so.
2. Read the $ARGUMENTS section of docs/plan.md Part D, then only the spec files it lists.
3. Enter plan mode. Present a short plan: files to create or change, tests to write, emulator scenarios to run, and anything that needs the human. Wait for approval.
4. Implement in small steps. After each green step: `git add -A && git commit -m "$ARGUMENTS: <what>"`.
5. Run `scripts/check.sh` until it passes. Keep the last lines of its output for the summary.
6. If the milestone lists emulator scenarios, delegate them to the emulator-tester subagent with the scenario ids. Fix failures and rerun the failed ones.
7. Delegate a review to the spec-reviewer subagent with: the milestone id, the git range since the previous milestone's tag, and the spec files listed for this milestone. Fix gaps that break the spec or a non-negotiable; list the rest under "Not fixed" in progress.md.
8. Update docs/progress.md (status "claude-verified", built, left, deviations, human checks, decisions needed) and docs/device-test-checklist.md.
9. Commit and tag: `git tag <milestone in lower case>-claude-verified`.
10. Stop and report: what was built; evidence (commands and their results, screenshot paths); the Human checks for this milestone; decisions needed.

When the human replies "M<n> accepted", mark it "accepted" in docs/progress.md and commit.
````

### B.8 `.claude/skills/android-emulator/SKILL.md`
````markdown
---
name: android-emulator
description: Boot the SmileProc emulators and drive the lock screen, notifications, location, biometrics, idle mode and reboots with adb; take screenshots and read them. Use whenever a task runs or checks the app on an emulator or a connected phone.
---
# Driving the emulator

All scripts read scripts/emu/env.sh: `SMILEPROC_SERIAL` (default: the first running emulator), `PKG=com.example.smileproc.debug`, `PIN=1111`. Never target the dogfood package.

## Emulators (created in M0)
- `sp36`: latest stable API, AOSP x86_64/arm64 image without Google Play (proves nothing depends on Play Services). Default.
- `sp29`: API 29, for minSdk behaviour (no POST_NOTIFICATIONS, Android 10 biometric rules, no swipe-away of ongoing notifications).

## Scripts
- `scripts/emu/start.sh [sp36|sp29]` boots headless if needed, waits for boot, turns animations off, sets the lock-screen PIN once.
- `scripts/emu/install.sh` installs the debug build.
- `scripts/emu/lock.sh`, `wake.sh` (screen on, still locked), `unlock.sh` (swipe up, type PIN).
- `python3 scripts/emu/notifs.py [--expect-max 1]` prints SmileProc notifications as JSON (id, channel, ongoing, text, action titles) from `dumpsys notification --noredact`; exits 1 if more than the expected maximum.
- `python3 scripts/emu/tap.py "<text>" [--shade]` finds a node by text or content description with uiautomator (opens the notification shade first with --shade) and taps its centre. Works on the lock screen.
- `scripts/emu/shot.sh <name>` saves build/emu-shots/<name>.png and prints the path. Read the PNG to see the screen.
- `scripts/emu/probe.sh` prints the debug probe JSON: entry count, counts per mood, per source, located count, notification machine state, last test result. No content.
- `scripts/emu/fire.sh reminder|restore|seed-demo|clear-demo` sends debug broadcasts that run the same code paths as the real alarms.
- `scripts/emu/geo.sh <lat> <lon>` sets the emulator GPS (`adb emu geo fix <lon> <lat>`).
- `scripts/emu/idle.sh on|off` forces or leaves Doze (`dumpsys deviceidle force-idle` / `unforce`).
- `scripts/emu/reboot.sh` reboots and waits for boot (the phone is then locked before its first unlock).

## Gotchas
- Before the first unlock after a reboot, the app doesn't run and has no notification. That is correct.
- uiautomator dumps can be stale right after a tap: sleep 1 s before the next dump.
- Inexact alarms can be late; for logic checks use `fire.sh`, and run the real-alarm scenario (E13) only where the milestone asks.
- Screenshots are evidence: name them by scenario (e1-locked-faces.png) and list the paths in your report.
- Real phone: only if the human connected it and set SMILEPROC_SERIAL. Every adb command to it asks for approval. Don't type the human's PIN; ask them to unlock.
````

### B.9 `.claude/agents/`

`.claude/agents/spec-reviewer.md`
```markdown
---
name: spec-reviewer
description: Reviews a milestone's changes against docs/spec and the non-negotiables before the milestone is tagged. Use after a milestone's checks pass.
tools: Read, Grep, Glob, Bash
---
You review SmileProc changes. You receive a milestone id, a git range and a list of spec files.

1. Read docs/spec/product.md (non-negotiables) and the listed spec files.
2. Run `git diff --stat <range>` and `git diff <range>`; read changed files in full where the diff isn't enough.
3. Report only:
   - non-negotiable violations;
   - requirements of this milestone that are missing or wrong;
   - rules the spec states that have no test;
   - changes outside the milestone's scope.
4. For each finding: file:line, the spec line it breaks, a one-line fix. No style preferences.
5. If there is nothing to report, answer "No gaps".

Never edit files or run commands that change the repo or a device.
```

`.claude/agents/emulator-tester.md`
```markdown
---
name: emulator-tester
description: Runs SmileProc emulator scenarios (lock screen, notification buttons, reminders, reboot, location, app lock, screenshots) with adb and reports pass/fail with evidence. Use for any milestone step that lists E-scenarios.
tools: Bash, Read, Grep, Glob
model: sonnet
---
You run emulator scenarios for SmileProc. Read .claude/skills/android-emulator/SKILL.md first, then the scenarios you were given in docs/spec/testing.md (section "Emulator scenarios").

For each scenario:
1. Start from a known state: emulator booted, debug build installed, phone unlocked unless the scenario says otherwise.
2. Run the steps with the scripts in scripts/emu. Take the screenshots the scenario names and look at them.
3. Compare with the expected result.

Report a table: scenario id, pass/fail, what you observed, evidence (command output lines, screenshot paths). For a failure, add the smallest reproduction you found.

Never touch com.example.smileproc (the human's app). Never change code: report, don't fix.
```

### B.10 Scripts (Claude writes them in M0)
- `scripts/privacy-guard.sh` — fast, no Gradle. Applies the same rules as `guard_edit.py` to every file under `core/`, `app/src/main/`, `content/`, Gradle files and the version catalog (skips `spikes/`, `build/` and `app/src/debug/`). Prints each hit as `file:line rule` and exits 1 on any hit.
- `scripts/check.sh` — in order: `privacy-guard.sh`; `python3 tools/generate_fixtures.py --check`; every JSON in `content/` and `fixtures/` parses; `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDogfood`; the dogfood runtime classpath contains none of `com.google.android.gms`, `com.google.firebase`, `com.squareup.okhttp3`, `com.squareup.retrofit2`, `io.ktor`, `androidx.glance`; `apkanalyzer manifest permissions` on the dogfood APK shows none of `INTERNET`, `FOREGROUND_SERVICE`, `ACCESS_BACKGROUND_LOCATION`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`; the dogfood manifest has no debug components, no app-widget provider, no `TileService`, and `SystemEventsReceiver` is its only exported receiver. Prints a one-line PASS/FAIL per step.
- `scripts/emu/*` — as listed in B.8. `env.sh` defines `adb() { command adb -s "$SMILEPROC_SERIAL" "$@"; }` so scripts never hit the wrong device.

### B.11 Build types and debug-only tools
- `debug`: `applicationIdSuffix ".debug"`, app name "SmileProc (debug)", includes `app/src/debug`.
- `dogfood`: `initWith(release)`, no suffix, signed with the debug signing config, `matchingFallbacks += "release"`. The human installs this one for daily use from M5.
- `release`: no suffix, release signing set up by the human in M10.
- `app/src/debug` only (never in dogfood or release):
  - `ProbeReceiver` (exported, debug only): writes `files/probe.json` with counts only — entries, per mood, per source, located, machine state, last test result. No text, tags, comments or coordinates.
  - `DebugEventsReceiver` (exported, debug only): `FIRE_REMINDER`, `FIRE_RESTORE`, `SEED_DEMO`, `CLEAR_DEMO`, each calling the same code path as the real trigger.
  - A Debug screen: seed demo data, pattern debugger, spike readouts.

---

# Part C — Spec (binding)

In M0, copy each C section **verbatim** into the named file under `docs/spec/`, with its title as the file's `#` heading. Cross-references name the file and section, e.g. *(logic.md › Location acceptance)*.

---

### C1 · `docs/spec/product.md`

#### What we're building
- **3-face scale:** 1 = Low, 2 = Okay, 3 = Good. Every face is a valid answer.
- **Two ways to rate, and only these:** the **check-in notification** (three face buttons, usable from the lock screen without unlocking) and **the app**. A tap saves immediately; emotion tags and a comment are an optional follow-up in the app.
- **One notification at most, ever.** Its text is "How are you right now?". Reminders at user-set times use it. With **Keep a check-in on the lock screen** on (pre-selected), it stays there silently between reminders.
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

---

### C2 · `docs/spec/data.md`

#### Canonical data format
Used for export files, fixtures and the sealed database payloads.

```json
{
  "id": "3f0c2b9e-7d1a-4a52-9a8e-0c6f1d2e4b10",
  "createdAt": "2026-10-12T13:05:00.000Z",
  "updatedAt": "2026-10-12T13:06:10.000Z",
  "timeZoneID": "Europe/Rome",
  "mood": 2,
  "tags": ["anxious.stressed"],
  "comment": null,
  "location": {
    "latitude": 45.4642, "longitude": 9.1900, "horizontalAccuracy": 12.0,
    "capturedAt": "2026-10-12T13:05:03.000Z", "kind": "fresh"
  },
  "source": "notification"
}
```
```json
{ "id": "…", "name": "Office", "kind": "work", "anchorLatitude": 45.48, "anchorLongitude": 9.21, "createdAt": "…" }
```
- UUIDs lower-case. Timestamps UTC ISO 8601 with milliseconds and `Z`.
- **Weekdays are ISO: 1 = Monday … 7 = Sunday** (`DayOfWeek.value`).
- `mood` 1–3; `tags` max 3, ordered, ids from `emotion-tags.json`; `comment` trimmed, `null` if empty, max 1000 characters.
- `location` is `null` when location is off, permission is missing or no acceptable fix exists. The rating is saved either way.
- `location.kind`: `fresh` | `cached`. `source`: `app` | `notification`.
- `timeZoneID` is the IANA zone where the entry was made. Daypart, weekday and calendar date are always computed in **its own** zone (travel; DST: Europe's clocks go back on 25 Oct 2026).
- Place label `kind`: `home` | `work` | `other`.

```kotlin
// :core
@Serializable data class Entry(
    val id: String,
    @Serializable(with = IsoInstantSerializer::class) val createdAt: Instant,
    @Serializable(with = IsoInstantSerializer::class) val updatedAt: Instant,
    val timeZoneID: String, val mood: Mood, val tags: List<String>,
    val comment: String?, val location: LocationSample?, val source: EntrySource
)
```
**Type names:** `Mood`, `EntrySource`, `LocationSample`, `Entry`, `PlaceLabel`, `ClusterID`, `PlaceAssignment`, `PatternConfig`, `Detector`, `Family`, `Direction`, `Confidence`, `DetectedPattern`, `CardItem`, `WeeklyCard`, `Variant`, `WindowKind`, `PatternLibrary`, `LibraryMatcher`, `PlaceResolver`, `PatternEngine`, `WeeklyCardBuilder`, `LowMoodMonitor`, `CardPeriod`, `CheckInMachine`, `Sealer`, `ExportFormat`.

#### `content/emotion-tags.json` (ids and colours; labels in `strings.xml` as `tag_<core>_<fine>`)
| Core | Fine tags |
|---|---|
| `happy` | joyful, grateful, proud, hopeful, excited |
| `calm` | relaxed, content, peaceful, focused, safe |
| `sad` | down, lonely, disappointed, hurt, nostalgic |
| `anxious` | stressed, worried, overwhelmed, nervous, insecure |
| `angry` | frustrated, irritated, resentful, jealous, impatient |
| `tired` | drained, sleepy, bored, unmotivated, numb |
| `surprised` | amazed, confused, curious, startled, moved |

Fine ids are `<core>.<fine>` (e.g. `anxious.stressed`).
```json
{ "version": 1, "cores": [ { "id": "happy", "color": "#F2C14E", "fine": ["happy.joyful", "happy.grateful", "happy.proud", "happy.hopeful", "happy.excited"] } ] }
```
Choose a soft, distinguishable colour per core that meets contrast in light and dark mode.

#### `content/pattern-config.json`
Thresholds come only from this file; a test asserts every key is read.
```json
{
  "version": 1,
  "minSegment": 3, "minRest": 3, "patternMinSegment": 5, "patternMinRest": 5,
  "minAbsDiff": 0.4, "zHint": 1.5, "zPattern": 2.0, "sdFloor": 0.3,
  "weekdayMinSegment": 4, "weekdayMinDistinctDates": 2, "weekdayMinAbsDiff": 0.5, "weekdayMinDataDays": 14,
  "tagMinLowTagged": 4, "tagMinCount": 3, "tagMinShare": 0.5, "tagMinShareGap": 0.25, "tagPatternCount": 5,
  "tagAtPlaceMinShare": 0.4, "fineTagDominance": 0.7,
  "shortWeekThreshold": 10, "maxItems": 3, "maxItemsLowWeek": 2,
  "notEnoughDataBelow": 5, "firstWeekDays": 14, "firstCardMinEntries": 5,
  "lowWeekMinEntries": 4, "lowWeekShare": 0.6, "lowRunLength": 5, "lowRunMinDays": 2,
  "placeRadiusMeters": 100, "placeMaxAccuracyMeters": 150
}
```

#### `content/pattern-library.en.json`
Placeholders: `{segment}` (morning/afternoon/evening/night), `{weekday}`, `{place}` (current name or "an unnamed place"), `{tag}` (lower-case label). Ship exactly this seed; the human may extend it. `"weekday": "7"` = Sunday.

```json
[
  { "id": "afternoon-dip", "match": { "detector": "timeOfDay", "direction": "lower", "segment": "afternoon" },
    "title": "Afternoons run lower",
    "common": "Lots of people notice their mood and energy dip after lunch.",
    "tip": "Try a 10-minute walk or a few minutes of daylight shortly before the dip usually starts.",
    "question": "Where could a 10-minute break fit tomorrow afternoon?" },

  { "id": "sunday-evening", "match": { "detector": "weekdayDaypart", "direction": "lower", "weekday": "7", "segment": "evening" },
    "title": "Sunday evenings feel heavier",
    "common": "A dip before the week starts is something many people recognise.",
    "tip": "Plan one small thing to look forward to on Monday.",
    "question": "What's one small thing you could put into Monday?" },

  { "id": "out-lifts", "match": { "detector": "awayFromHome", "direction": "higher" },
    "title": "You feel better when you're out",
    "common": "Many people find a change of scene lifts their mood.",
    "tip": "Put one outdoor or out-of-the-house block into your week.",
    "question": "Which day has room for it?" },

  { "id": "stress-at-place", "match": { "detector": "tagAtPlace", "tag": "anxious.stressed" },
    "title": "Stress tends to show up at {place}",
    "common": "It's common for one place to carry most of a week's stress.",
    "tip": "Take a 2-minute pause when you arrive at {place}, or just before you leave.",
    "question": "Arriving or leaving — which pause is easier to try?" },

  { "id": "tired-on-low", "match": { "detector": "tagOnLow", "tag": "tired" },
    "title": "Tiredness shows up on most low check-ins",
    "common": "Low mood and tiredness often travel together.",
    "tip": "Look at sleep before anything else: pick one night this week to start winding down earlier.",
    "question": "Which night could start 30 minutes earlier?" },

  { "id": "time-lower", "match": { "detector": "timeOfDay", "direction": "lower" },
    "title": "Your {segment}s run lower",
    "common": "Many people have a time of day that's harder than the rest.",
    "tip": "Give your {segment}s one small buffer this week: a short break, something to eat, or a few minutes outside.",
    "question": "What would make the next {segment} a little easier?" },

  { "id": "time-higher", "match": { "detector": "timeOfDay", "direction": "higher" },
    "title": "Your best stretch is the {segment}",
    "common": "Most people have a time of day when they feel more like themselves.",
    "tip": "If you can, put something you care about into your {segment}s.",
    "question": "What could you move into that time?" },

  { "id": "weekday-lower", "match": { "detector": "weekday", "direction": "lower" },
    "title": "{weekday}s run lower",
    "common": "Many people have one day of the week that's harder than the others.",
    "tip": "Add one small, kind thing to your next {weekday}.",
    "question": "What would make next {weekday} lighter?" },

  { "id": "weekday-higher", "match": { "detector": "weekday", "direction": "higher" },
    "title": "{weekday}s go well for you",
    "common": "Noticing your good days is as useful as noticing the hard ones.",
    "tip": "Look at what's different about {weekday}s and borrow one piece of it for another day.",
    "question": "What makes {weekday}s work?" },

  { "id": "weekday-segment-lower", "match": { "detector": "weekdayDaypart", "direction": "lower" },
    "title": "{weekday} {segment}s run lower",
    "common": "Many people notice one stretch of the week that's harder.",
    "tip": "Plan something small and easy for your next {weekday} {segment}.",
    "question": "What could you plan for it?" },

  { "id": "weekday-segment-higher", "match": { "detector": "weekdayDaypart", "direction": "higher" },
    "title": "{weekday} {segment}s go well",
    "common": "Noticing what goes well is half of repeating it.",
    "tip": "Think about what makes that time work and borrow one piece for another day.",
    "question": "Which piece would you borrow?" },

  { "id": "place-lower", "match": { "detector": "place", "direction": "lower" },
    "title": "Check-ins at {place} run lower",
    "common": "Places often carry their own mood.",
    "tip": "Try one small change at {place}: a short pause, a different spot, or a moment outside.",
    "question": "Which change is easiest to try?" },

  { "id": "place-higher", "match": { "detector": "place", "direction": "higher" },
    "title": "You feel better at {place}",
    "common": "Many people have a place that steadies them.",
    "tip": "If you can, spend a little more time at {place} this week.",
    "question": "When could you get there?" },

  { "id": "home-higher", "match": { "detector": "awayFromHome", "direction": "lower" },
    "title": "Home feels better than being out",
    "common": "Being out takes more energy for a lot of people.",
    "tip": "Plan a few quiet minutes to recharge after you get back.",
    "question": "When do you usually need that most?" },

  { "id": "tag-on-low", "match": { "detector": "tagOnLow" },
    "title": "Feeling {tag} shows up on most low check-ins",
    "common": "Putting a name to what's behind a low moment is a useful step.",
    "tip": "Pick one small thing that would ease feeling {tag} this week.",
    "question": "What usually helps, even a little?" },

  { "id": "tag-at-place", "match": { "detector": "tagAtPlace" },
    "title": "Feeling {tag} tends to show up at {place}",
    "common": "It's common for one place to bring out one feeling.",
    "tip": "Take a 2-minute pause at {place} this week and notice what changes.",
    "question": "When would that pause fit?" }
]
```

#### `content/support.json` — human-owned
**Talk to someone** and **See support options** open the Support screen. Its gentle copy comes from `strings.xml`; optional contacts come from this file. The agent creates exactly this seed and **never adds names, numbers or links from memory** (the edit hook blocks changes to it):
```json
{ "version": 1, "entries": [] }
```
Entry shape, for the human: `{ "region": "IT", "name": "…", "phone": "…", "sms": "…", "url": "…", "verified": false }`. With no entries for the device's region, the screen shows only its copy. Entries with `"verified": false` never show in dogfood or release builds.

#### Strings
Every UI string lives in `app/src/main/res/values/strings.xml`: tag labels, face labels, notification text, observation lines (`week_observation_timeOfDay_lower`: "Your %1$s check-ins were lower than the rest of your day (%2$d check-ins).") and all copy in screens.md. Italian later goes into `values-it/`; library text gets a sibling `pattern-library.it.json`.

#### Fixtures
`tools/generate_fixtures.py` (Python 3 standard library only, fixed seeds) writes inputs and hand-checkable expectations; `:core` tests load them via a Gradle-provided path.

| Fixture set | Scenarios (each `name.input.json` + `name.expect.json`) |
|---|---|
| `patterns/` | `afternoon-dip`, `sunday-evening` (28 days), `tired-on-low`, `stressed-at-work` (with a Work label), `out-lifts` (with a Home label), `low-week`, `not-enough-data`, `first-week`, `dst-week` (across 25 Oct 2026, Europe/Rome), `travel` (some entries in America/New_York), `no-location`, `noise-1..3` |
| `places/` | `three-places-noise` (±40 m), `accuracy-filter`, `label-capture`, `label-deleted` |
| `periods/` | card-period boundaries for several "now" values, including the DST week |
| `export/` | `vector-1.json` (written in M9) |

`expect.json` assertions: `firstItemLibraryID`, `itemsIncludeLibraryIDs`, `variant`, `isLowWeek`, `maxItemCount`, `candidatesInclude` (`{id, confidence}`), `dayparts` (`{entryID: daypart}`), `sameCluster` / `differentCluster` / `unclustered`.

**Goldens:** once all `expect` files pass (M2), a `WRITE_GOLDENS=1` run writes `<name>.golden.json` (full sorted candidate list with `id`, `confidence`, `nSegment`, `nRest`, `diff`, `z`, `score` rounded to 6 decimals; selected items with library ids; `variant`, `isLowWeek`, `windowKind`, `entryCount`, `moodCounts`). The human reviews and accepts them. From then on tests reproduce every golden exactly. Goldens are regenerated only on the human's request.

---

### C3 · `docs/spec/logic.md`

#### Location acceptance
Platform code requests the fix; `:core` holds the rules.
- **In the app:** accept the first fix with accuracy ≤ 100 m (`fresh`). At the 10 s timeout, use the best fix seen if ≤ 200 m. Otherwise the last known location if ≤ 10 min old and ≤ 200 m (`cached`). Otherwise `null`.
- **From a notification tap:** last known location only, if ≤ 10 min old and ≤ 200 m (`cached`); otherwise `null`. Spike A2 may change this, only with the human's approval.
- Location off or permission missing → `null`, no request made.
- **In-app ratings are saved first with `location = null`, then updated when the fix arrives.**

#### Place clustering (`PlaceResolver`)
Places are **computed, not stored per entry**, so renames and new labels apply to all history.
```
resolve(entries, labels, config) -> PlaceAssignment
  // entryID -> ClusterID; ClusterID -> Cluster(centroid, memberIDs, label?)

1. eligible = entries with location.horizontalAccuracy <= placeMaxAccuracyMeters (150), sorted by (createdAt, id).
2. Named pass: assign each eligible entry to the nearest label anchor within placeRadiusMeters (100)
   → ClusterID "p:<labelID>".
3. Unnamed pass, in order: join the nearest unnamed cluster whose running centroid is within 100 m
   (centroid = arithmetic mean of members' lat/lon); otherwise start a new cluster "u:<firstEntryID>".
4. Stabilise once: recompute unnamed centroids, reassign each unnamed member to the nearest centroid
   within 100 m (or its own cluster), drop empty clusters, keep "u:<earliest member id>" as the id.
5. Entries without an eligible location have no cluster.
Distance: haversine, R = 6,371,000 m. Ties: lower ClusterID string wins.
```
- **Naming:** chips *Home*, *Work* and a free-text name. Creates a `PlaceLabel` with anchor = cluster centroid. If another label's anchor is within 100 m, ask "Is this {name}?" — if yes, don't create a new label; move that label's anchor to the combined centroid.
- **Unnamed place descriptions** (no maps): "Unnamed place · 8 check-ins · mostly weekday mornings".

#### Pattern engine
**Dayparts** (entry's local time): `morning` 05:00–11:59, `afternoon` 12:00–16:59, `evening` 17:00–21:59, `night` 22:00–04:59.

**Card period** (`CardPeriod`): `periodEnd` = the latest occurrence of the weekly card time (default Sunday 19:00, ISO weekday 7) that is ≤ now, in the device's current time zone; `periodStart` = the same local time 7 calendar days earlier (not 7 × 24 h).

**Windows**
- *Card window* = `[periodStart, periodEnd)`. Fewer than `shortWeekThreshold` entries → use the 14 calendar days ending at `periodEnd` and set `windowKind = twoWeeks`.
- *Weekday window* = the 28 calendar days ending at `periodEnd`. Weekday detectors are skipped if the first entry ever is less than `weekdayMinDataDays` before `periodEnd`.

**Statistics**
```
var(x)    = Σ(x − mean)² / (n − 1)        (0 when n = 1)
diff      = mean(seg) − mean(rest)
sdPooled  = sqrt(((nSeg−1)·var(seg) + (nRest−1)·var(rest)) / (nSeg + nRest − 2))
z         = diff / (max(sdPooled, sdFloor) · sqrt(1/nSeg + 1/nRest))
pattern   : nSeg ≥ patternMinSegment, nRest ≥ patternMinRest, |diff| ≥ minAbsDiff, |z| ≥ zPattern
earlyHint : nSeg ≥ minSegment,        nRest ≥ minRest,        |diff| ≥ minAbsDiff, |z| ≥ zHint
score     = |diff| · min(nSeg, 10)/10 + (pattern ? 0.2 : 0)
direction = diff < 0 ? lower : higher
```
Weekday detectors use `weekdayMinSegment`, `weekdayMinAbsDiff`, and require the segment to span ≥ `weekdayMinDistinctDates` local dates.

**Mean-comparison detectors**
| Detector | Family | Segment | Rest | Window | Pattern id |
|---|---|---|---|---|---|
| `timeOfDay` | time | one daypart | all other entries | card | `timeOfDay.<dir>.<daypart>` |
| `weekday` | weekday | one ISO weekday | other days | weekday | `weekday.<dir>.<1-7>` |
| `weekdayDaypart` | weekday | weekday × {morning, afternoon, evening} | all other entries | weekday | `weekdayDaypart.<dir>.<1-7>.<daypart>` |
| `place` | place | one cluster | other located entries | card | `place.<dir>.<clusterID>` |
| `awayFromHome` | place | located entries not in the Home label's cluster | entries in it | card | `awayFromHome.<dir>` |

`awayFromHome` needs a label with `kind == home`. Place detectors use located entries only; entries without location still count for every other detector.

**Tag detectors.** An entry "has core c" if any of its tags is c or starts with `c.`. Counts are at core level; if one fine tag appears in ≥ `fineTagDominance` of the segment hits, report that fine id instead.
| Detector | Family | Rule | Pattern when | Pattern id |
|---|---|---|---|---|
| `tagOnLow` | tag | Low entries with tags ≥ `tagMinLowTagged`; core appears in ≥ `tagMinShare` of them and ≥ `tagMinCount`; its share among Okay/Good tagged entries is ≥ `tagMinShareGap` lower | count ≥ `tagPatternCount` | `tagOnLow.<tag>` |
| `tagAtPlace` | tag | At one cluster: core appears in ≥ `tagMinCount` entries and ≥ `tagAtPlaceMinShare` of the cluster's tagged entries; its share elsewhere is ≥ `tagMinShareGap` lower | count ≥ `tagPatternCount` | `tagAtPlace.<tag>.<clusterID>` |

Tag score = (shareSegment − shareRest) · min(count, 10)/10 + (pattern ? 0.2 : 0). Tag patterns have no direction.

**Determinism:** sort by confidence (pattern first), then score **rounded to 9 decimals** descending, then id ascending.
```
DetectedPattern { id, detector, family, direction?, params {segment, weekday, clusterID, placeKind, tag},
                  nSegment, nRest, meanSegment?, meanRest?, diff?, z?, score, confidence, supportingEntryIDs }
```

#### Library matching and card building
**Matching** (`LibraryMatcher`): a library entry matches when its `detector` equals the pattern's and every other key it specifies (`direction`, `segment`, `weekday`, `placeKind`, `tag`) equals the pattern's param; `tag` also matches the pattern tag's core. Values are strings. Most matched keys wins; ties go to file order. A test enumerates every detector × direction × segment/weekday/placeKind/tag-core combination and asserts something matches.

**Variant and limits** (period entries = entries in `[periodStart, periodEnd)`):
- `notEnoughData`: period entries < `notEnoughDataBelow` → max 1 item (hints allowed), copy "patterns need a few more check-ins".
- else `firstWeek`: first entry ever within `firstWeekDays` of `periodEnd` → max 1 item, copy "patterns get clearer after two or three weeks".
- else `normal`: max `maxItems`.
- `isLowWeek` (independent): period entries ≥ `lowWeekMinEntries` and Low share ≥ `lowWeekShare` → max items `maxItemsLowWeek`, and the card **leads with acknowledgment and *Talk to someone* before any tips**.

**Selection** (`WeeklyCardBuilder`): deterministic order; pick greedily, **max one per family**, up to the limit; if limit ≥ 2 and nothing picked is `higher` but a `higher` candidate with `pattern` confidence exists, it replaces the last pick; early hints only fill slots patterns didn't (UI label "Early hint").
```
WeeklyCard { periodStart, periodEnd, windowKind, entryCount, moodCounts [low, okay, good],
             days [7 × counts per face], isLowWeek, variant, items [CardItem {patternID, libraryID, pattern}] }
```
- Observation lines come from strings (`week_observation_<detector>_<dir>`). Never show raw averages.
- Store the structured card; render text at display time so renamed places show current names. A cluster that no longer exists renders as "one of your places".
- **Timing:** the first card appears at the first card time with ≥ `firstCardMinEntries` entries in total. A card is built and stored the first time the app opens after the card time. The Week tab shows a dot until a new card is seen. **No notification.**

#### Low-mood support (`LowMoodMonitor`)
- **Low run (support card on Today):** the last `lowRunLength` entries are all Low **and** span ≥ `lowRunMinDays` local dates. Dismissing stores the latest entry id; the card re-arms only after a non-Low entry.
- **Low week:** see Library matching and card building.
- Never a notification, never in notification text, never in an export filename.

#### Check-in notification state machine (`CheckInMachine`)
A pure reducer: `reduce(state, settings, event, now) -> Transition(state, effects)`.

**Settings:** `standing` (Keep a check-in on the lock screen), `dismissed` (the person swiped the standing notification away; cleared by the next reminder).
**Resting state** = `Faces(silent)` if `standing && !dismissed`, else `Hidden`.

| State | What the one notification shows |
|---|---|
| `Hidden` | nothing |
| `Faces(alerting)` | "How are you right now?" + Low · Okay · Good, with sound/vibration per channel |
| `Faces(silent)` | the same, no sound, no vibration |
| `Saved` | "Saved ✓", no buttons, silent |
| `Failed` | "Couldn't save. Open SmileProc.", no buttons, silent |
| `Test` | the faces, alerting; buttons save nothing |
| `TestDone` | "It works ✓", no buttons, silent |

| Event | Next state | Effects |
|---|---|---|
| `ReminderFired` | `Faces(alerting)` | `dismissed = false`; cancel any restore timer |
| `Tap(mood)` on `Faces` | `Saved` | insert entry (`source = notification`); restore timer 60 s |
| save throws | `Failed` | restore timer 60 s |
| `Tap(mood)` on `Test` | `TestDone` | record test success; no entry; restore timer 60 s |
| `RestoreTimer` | resting state | — |
| `RatedInApp` while `Faces(alerting)` | resting state | — |
| `Dismissed` (swipe) | `Hidden` | `dismissed = true` if `standing` |
| `StandingTurnedOn` | resting state if `Hidden` | `dismissed = false` |
| `StandingTurnedOff` | `Hidden` if `Faces(silent)`; else unchanged | — |
| `TestRequested` | unchanged; after 10 s → `Test` | remember the resting state |
| `Booted` / `AppUpdated` | resting state | reschedule alarms |

Every state renders to **the same notification ID** (or a cancel). A property test runs random event sequences and asserts each effect list holds at most one post-or-cancel of `ID_CHECKIN` and never posts another ID.

---

### C4 · `docs/spec/android.md`

#### Stack and dependency allowlist
| Concern | Choice |
|---|---|
| Language | Kotlin 2.x, JVM toolchain 17 |
| UI | Jetpack Compose + Material 3, Navigation Compose |
| Min / target | minSdk 29 (Android 10); compileSdk and targetSdk = latest stable |
| Storage | Room (KSP), schema export committed |
| Crypto | Android Keystore, `javax.crypto` |
| Settings | DataStore Preferences |
| Location | platform `LocationManager` via `LocationManagerCompat` (no Play Services) |
| Biometrics | androidx.biometric |
| JSON | kotlinx.serialization |
| Time | `java.time` |
| Tests | JUnit (`:core`), Robolectric + Room testing, Compose UI test, AndroidX test (instrumented) |

**Allowed:** AndroidX (core, activity, lifecycle incl. lifecycle-process, navigation, compose BOM, room, biometric, datastore), kotlinx-coroutines, kotlinx-serialization. Test-only: JUnit, kotlinx-coroutines-test, Robolectric, AndroidX test libraries. Anything else needs the human's approval. Pin to the latest stable versions at M0. Identifiers are placeholders until the human sets them: `com.example.smileproc`, deep link `smileproc://`. Build types: see plan.md B.11.

#### Modules
- `:core` — pure Kotlin/JVM: models, canonical JSON, content loaders (take `String`), places, patterns, card builder, low mood, `CheckInMachine`, `Sealer` interface, export format and crypto. `./gradlew :core:test`, no emulator.
- `:app` — Compose UI, Room, `KeystoreSealer`, location, `CheckInNotifier`, receivers and alarms, biometric lock, `SafeLog`. `content/` is an assets source directory.
- `:spikes` — M1 only; own applicationId `com.example.smileproc.spikes`; never shipped.

#### Storage and encryption
```kotlin
@Entity(tableName = "entry", indices = [Index("createdAt")])
data class EntryRow(@PrimaryKey val id: String, val createdAt: Long, val updatedAt: Long, val payload: ByteArray)
@Entity(tableName = "place") data class PlaceRow(@PrimaryKey val id: String, val payload: ByteArray)
@Entity(tableName = "weekly_card") data class WeeklyCardRow(@PrimaryKey val periodStart: Long, val payload: ByteArray)
```
- `payload` = IV (12 bytes) ‖ ciphertext ‖ tag, `AES/GCM/NoPadding`, AAD = id bytes, over the canonical JSON. `createdAt` stays plaintext for ordering.
- Key: AES-256 in `AndroidKeyStore` (alias `smileproc-db-v1`, GCM, no padding, randomized encryption). **No `setUserAuthenticationRequired`, no `setUnlockedDeviceRequired`**, so notification buttons can save while locked (after the first unlock since boot).
- DB in default (credential-encrypted) app storage. The app is not direct-boot aware: before the first unlock after a restart it doesn't run, and old notifications are gone.
- Room `exportSchema = true`, schemas committed; **after M5, migrations only**, each with a migration test.
- Backups off: `android:allowBackup="false"` and `dataExtractionRules` excluding everything from cloud backup **and** device-to-device transfer.
- DB present but key missing → recovery screen (*Import an export* / *Start fresh*).

#### The check-in notification
- **One channel** `checkin` ("Check-ins"), `IMPORTANCE_DEFAULT`, lock-screen visibility public, `setShowBadge(false)`, created at app start.
- **One ID** `ID_CHECKIN = 1`. `CheckInNotifier.render(state)` posts that ID or cancels it. Nothing else calls `notify`.
- Every state: monochrome face small icon, title "SmileProc", the state's text, `VISIBILITY_PUBLIC`, `CATEGORY_REMINDER`, `setShowWhen(false)`, `setLocalOnly(true)`, `setAutoCancel(false)`, content intent → app on Today (normal unlock), delete intent → `ACTION_DISMISSED`.
- `Faces(alerting)` and `Test`: `setOnlyAlertOnce(false)`. Other states: `setSilent(true)`, `setOnlyAlertOnce(true)`.
- `setOngoing(true)` when `standing` is on. Android 14+ lets people swipe it away while unlocked (not while locked) → `Dismissed`.
- **Face buttons:** three `NotificationCompat.Action`s, order Low, Okay, Good; `PendingIntent.getBroadcast` with request codes 1–3 (test 11–13), explicit `Intent(context, CheckInReceiver::class.java)`, action `ACTION_RATE`, extras `mood` and `test`, `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`; `setAuthenticationRequired(false)` set explicitly; `setShowsUserInterface(false)`.
- No full-screen intent; `MainActivity` is not `showWhenLocked`.
- `POST_NOTIFICATIONS` is requested in onboarding (13+). Without it, rating works only in the app and the Lock-screen check-in screen says so.

#### Saving from a notification tap
`CheckInReceiver` (not exported) handles `ACTION_RATE`, `ACTION_RESTORE`, `ACTION_DISMISSED`:
1. `goAsync()`, then run in the `AppContainer` scope.
2. Load machine state and settings from DataStore.
3. `ACTION_RATE` (not a test): build the `Entry` (`source = notification`, location per logic.md › Location acceptance), seal and insert. Success → `Saved`; any exception → `Failed` (SafeLog the exception type only).
4. Render, persist the state, schedule the 60 s restore (`AlarmManager.set(ELAPSED_REALTIME, …)`, inexact).
5. `finish()` in `finally`. Nothing waits for a location fix.

An in-app rating sends `RatedInApp`.

#### Reminders
- Up to 6 times a day, each with its own ISO weekday selection, in DataStore.
- One pending alarm: the next occurrence across all reminders, in the device's current zone. `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, …)`, inexact. On fire: `ReminderFired`, then schedule the next.
- `SystemEventsReceiver` (exported; `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`): reschedule; boot and update also send `Booted` / `AppUpdated`.
- A few minutes of drift is fine.

#### Lock-screen check-in (readiness)
One row per check, ✓ or a problem with a one-line fix and a button:

| Check | How | Fix button |
|---|---|---|
| Notifications allowed | `areNotificationsEnabled()`, plus `POST_NOTIFICATIONS` on 13+ | request, else `ACTION_APP_NOTIFICATION_SETTINGS` |
| Check-in channel on | importance ≠ `IMPORTANCE_NONE` | `ACTION_CHANNEL_NOTIFICATION_SETTINGS` |
| Shown on the lock screen | channel lock-screen visibility ≠ `VISIBILITY_SECRET` | `ACTION_CHANNEL_NOTIFICATION_SETTINGS` |
| Keep a check-in on the lock screen | the setting, with a toggle | — |

- **Send a test check-in:** `TestRequested`; the screen says "Lock your phone now"; after 10 s the notification becomes `Test`; a tap → `TestDone`, nothing saved; back in the app the screen shows "It works ✓".
- Returning without a recorded tap → one hint: check that the lock screen shows notifications, and on Samsung that they aren't set to icons only.
- Rows refresh on resume. Problems show only here and in onboarding.

#### Location
- `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`, asked in onboarding (skippable). Approximate-only is too coarse for places; Settings explains.
- In the app: save first, then `LocationManagerCompat.getCurrentLocation` (fused on 12+, else GPS, else network), 10 s timeout, then update the entry.
- From a notification tap: `getLastKnownLocation` across providers. Spike A2 measures how often that works and tries a short location foreground service; the default stays last known, else none, until the human decides.
- Capture location off or no permission → `location = null`, no request.

#### App lock and recents
- `BiometricPrompt` on launch and after 60 s in the background (`ProcessLifecycleOwner`). Android 11+: `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`. Android 10: `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`.
- No screen lock → toggle disabled with an explanation.
- Recents: `setRecentsScreenshotEnabled(false)` on 13+; `FLAG_SECURE` below that while the lock is on.
- The app lock never affects the notification buttons.

#### Export and import (`.smileproc`)
```json
{
  "format": "smileproc-export",
  "version": 1,
  "kdf": { "name": "PBKDF2-HMAC-SHA256", "iterations": 600000, "salt": "<base64, 16 bytes>" },
  "cipher": "AES-256-GCM",
  "payload": "<base64 of nonce(12) ‖ ciphertext ‖ tag(16)>"
}
```
- Plaintext: `{ "exportedAt", "app": {"platform": "android", "version"}, "entries": [Entry…], "places": [PlaceLabel…], "settings": { "reminders", "keepCheckIn", "weeklyCard": {"weekday", "hour", "minute"}, "tagPickerMode", "captureLocation" } }`, canonical format. Weekly cards are rebuilt after import.
- Key = PBKDF2-HMAC-SHA256(password, salt, iterations, 32 bytes) with a software AES key, in `:core`.
- Password ≥ 8 characters, entered twice, with "If you forget this password, the export can't be opened." Derivation off the main thread with progress.
- Storage Access Framework (`CreateDocument` / `OpenDocument`), filename `smileproc-<yyyy-mm-dd>.smileproc`.
- Import merges by id; an existing id is replaced only if the imported `updatedAt` is newer.

#### Delete all
Close and delete the database, delete the Keystore entry, clear DataStore, cancel all alarms, cancel `ID_CHECKIN`, back to onboarding.

#### Manifest (dogfood/release)
- Permissions: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (androidx.biometric adds `USE_BIOMETRIC`).
- Not present: `INTERNET`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE*`.
- Receivers: `CheckInReceiver`, `ReminderReceiver` (not exported); `SystemEventsReceiver` (exported). No app-widget provider, `TileService` or shortcuts.
- `allowBackup="false"`, `dataExtractionRules`, `MainActivity` without `showWhenLocked`.

---

### C5 · `docs/spec/screens.md`

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

---

### C6 · `docs/spec/testing.md`

#### Automated tests
- **`:core` (JVM):** every fixture's `expect` and every golden; library coverage; config completeness; noise guard (100 seeded random weeks of 25 entries → average `pattern` items per card ≤ 0.3; if it fails, tell the human, don't change thresholds); DST and time zones (`dst-week`, `travel`, `periods/`); `no-location`; low-run cases (5 Low on one day → no card; across 2 days → card; dismiss and re-arm); every `CheckInMachine` row plus the one-notification property test; export crypto and `vector-1.json`.
- **Robolectric (`:app`):** Room and repositories; seal/open, tamper and wrong-id failure; `CheckInReceiver` save / fail / restore / dismiss / test paths; `ShadowNotificationManager` never holds more than one SmileProc notification; alarm rescheduling on boot, time and time-zone change; building a weekly card posts no notification.
- **Compose UI:** onboarding skip path; tap face → saved → follow-up optional; edit and delete; reminders CRUD; Lock-screen check-in rows; Week tab with demo data.
- **Instrumented (emulator):** `KeystoreSealer` seal/open; migration tests after M5.

#### Emulator scenarios (Claude runs them; evidence = command output + screenshots)
| Id | Scenario | Expected |
|---|---|---|
| E1 | Standing on. Lock, wake. `notifs.py`; screenshot; `tap.py "Okay" --shade`; `notifs.py`; `probe.sh` | Exactly 1 notification, ongoing, "How are you right now?", actions Low/Okay/Good; after the tap "Saved ✓" and no actions; probe: +1 entry, okay +1, source notification; the screenshot shows no mood data |
| E2 | After E1: `fire.sh restore`, then once with a real wait of up to 3 min | Back to the faces, silent |
| E3 | `fire.sh reminder` twice, tap, `fire.sh reminder`, test check-in; `notifs.py --expect-max 1` after every step | Never more than 1 SmileProc notification |
| E4 | API 34+, unlocked: swipe the notification away; `probe.sh`; `fire.sh reminder` | Gone; `dismissed` true; back after the reminder |
| E5 | Locked: try to swipe it away | Still there |
| E6 | `reboot.sh`; `notifs.py` before unlock; `unlock.sh`; wait 30 s; `notifs.py`; `dumpsys alarm` for the package | None before unlock; standing back after; next reminder alarm scheduled |
| E7 | Turn standing off in Settings; `fire.sh reminder`; tap Good; `fire.sh restore` | Faces (not ongoing) → "Saved ✓" → gone |
| E8 | API 33+: `pm revoke … POST_NOTIFICATIONS`; open Lock-screen check-in; screenshot; `pm grant`; screenshot | Problem row with fix, then ✓ |
| E9 | Send a test check-in; lock; wait 12 s; tap Good on the lock screen; unlock; screenshot; `probe.sh` | Test faces → "It works ✓"; screen shows "It works ✓"; entry count unchanged |
| E10 | `geo.sh 45.4642 9.19`; tap a face in the app; wait 10 s; `probe.sh` | located +1 |
| E11 | Fix set 1 min earlier; lock; tap a face on the lock screen; `probe.sh` | Saved; record whether located (spike A2 data) |
| E12 | Revoke location; tap a face in the app and on the lock screen | Both saved, located unchanged |
| E13 | Reminder 3 min ahead; `idle.sh on`; wait up to 15 min | Reminder fires (record the delay) |
| E14 | App lock on; background 61 s; reopen; screenshot; enter PIN; open recents; screenshot | Prompt shown; app after PIN; recents show no content |
| E15 | `fire.sh seed-demo`; screenshots of Week (normal, low week, not enough data, first week) | Each variant renders; low week leads with acknowledgment and Talk to someone |
| E16 | Delete all; `probe.sh`; `notifs.py`; `dumpsys alarm` | 0 entries, no notification, no alarms |
| E17 | `settings put system font_scale 2.0`; screenshots of Today, the follow-up sheet, Week, Settings; reset | Nothing clipped or overlapping |
| E18 | `cmd uimode night yes`; the same screenshots; reset | Readable, sufficient contrast |
| E19 | On `sp29`: E1, E3, E14 | Same results on Android 10 |

#### Human checks (real phone)
| Id | Check |
|---|---|
| H1 | Real lock screen (Pixel and/or Samsung): standing notification visible with default settings and silent; each face saves without unlocking; on Samsung, with lock-screen notifications as icons only and as details |
| H2 | Real GPS: fresh fix in the app within ~10 s; result of a notification tap (fresh / cached / none) |
| H3 | Reminders within a few minutes of the set time after an overnight idle |
| H4 | TalkBack through check-in, the tag list, the weekly card and the notification buttons |
| H5 | Real fingerprint or face unlock for the app lock |
| H6 | Export → uninstall the debug app → reinstall → import (SAF picker) → history restored |
| H7 | Phone-to-phone transfer or backup doesn't carry SmileProc data |
| H8 | Daily use of the dogfood build from M5 on |

---

# Part D — Milestones

Each milestone: **Read** (spec files to load), **Build**, **Claude verifies**, **Human checks**, **Done when**. Every milestone also ends with the milestone skill's steps 5–10 (check.sh, emulator-tester, spec-reviewer, progress, tag, report).

### M0 — Repo, Claude Code setup, scaffold
**Read:** this whole file once.
**Build (in this order):**
1. Layout (B.1), `.gitignore` (build outputs, `.gradle/`, `local.properties`, `CLAUDE.local.md`, `build/emu-shots/`).
2. `docs/spec/*.md` from Part C, verbatim. `docs/progress.md` (one section per milestone: status, built, left, deviations, human checks, decisions), `docs/spikes.md`, `docs/device-test-checklist.md` (from testing.md).
3. `CLAUDE.md` (B.2), `.claude/rules/*` (B.6), `.claude/skills/*` (B.7, B.8), `.claude/agents/*` (B.9).
4. `content/*` verbatim from data.md (colours chosen; `support.json` seed), `strings.xml` with all copy from screens.md, `tools/generate_fixtures.py` + generated inputs and expectations.
5. Gradle KTS + version catalog; `:core` (JVM), `:app` (Compose "hello" screen, `SafeLog`), build types and debug-only probes (B.11, probe and events receivers as stubs); assets source = `content/`.
6. `scripts/` (B.10) including `scripts/emu/*`. Create AVDs `sp36` and `sp29` with `sdkmanager`/`avdmanager` (AOSP images without Google Play).
7. `.claude/hooks/guard_edit.py` and `.claude/hooks/stop_check.sh` (executable). Self-test the guard **before** step 8: piping `{"tool_name":"Write","tool_input":{"file_path":"app/src/main/java/X.kt","content":"import okhttp3.OkHttpClient"}}` into it exits 2, and a harmless Kotlin snippet exits 0. Once settings.json exists the guard protects its own files, so fix it now if needed.
8. `.claude/settings.json` **last**.

**Claude verifies:** `scripts/check.sh` passes; `scripts/emu/start.sh sp36 && scripts/emu/install.sh` and a screenshot of the hello screen; the guard self-test output from step 7.
**Human checks:** open `/hooks` and confirm both hooks are listed; set the identifiers (Appendix 7) and update the two deny rules in settings.json.
**Done when:** all of the above, committed and tagged `m0-claude-verified`.

### M1 — Spikes
**Read:** product.md, android.md (The check-in notification, Saving from a notification tap, Location, Reminders).
**Build:** the `:spikes` app (own id): post the faces notification in alerting and silent variants; a receiver that seals a record with a Keystore key into a tiny Room DB; a short location foreground service started from a button (A2b); a readout screen. `docs/spikes.md` with steps and a results table.
**Claude verifies (emulator, `sp36` and `sp29`):**
- **A1:** silent and alerting variants visible on the lock screen; buttons save without unlocking; "Saved ✓" shown.
- **A2:** (a) last known in the receiver, (b) foreground service from the button: fresh / cached / none, accuracy, age.
- **A3:** can't be swiped while locked; on API 34+ swipeable while unlocked and the delete intent fires; returns after reboot via `BOOT_COMPLETED`.
- **A4 (short form):** a reminder 3 min ahead under forced idle; record the delay.

**Human checks:** A1 on your phone(s) (H1 conditions, including Samsung icons only); A2 with real GPS; A4 overnight.
**Done when:** results recorded and the human has decided A2. **Stop.**

### M2 — Core logic
**Read:** data.md, logic.md.
**Build:** models + canonical JSON codec, `PatternConfig` loader, content loaders, `Geo`, `PlaceResolver`, `CardPeriod`, the 7 detectors, `PatternEngine`, `PatternLibrary`, `LibraryMatcher`, `WeeklyCardBuilder`, `LowMoodMonitor`, `CheckInMachine`, a synthetic data generator.
**Claude verifies:** `./gradlew :core:test` with every `:core` item in testing.md › Automated tests. Then stop and ask the human to approve writing goldens; after approval run `WRITE_GOLDENS=1 ./gradlew :core:test` and show `git diff --stat fixtures/`.
**Human checks:** skim two goldens (`afternoon-dip`, `low-week`) and reply "goldens accepted".
**Done when:** all fixtures and goldens pass; goldens committed.

### M3 — Storage and crypto
**Read:** data.md (Canonical data format), android.md (Storage and encryption).
**Build:** `Sealer` in `:core` with a JVM `JcaSealer`; `KeystoreSealer`; Room DB, DAOs, repositories; backup and data-extraction rules; recovery screen.
**Claude verifies:** Robolectric tests; instrumented `KeystoreSealer` test on `sp36`; a debug event that seals and stores a record while the emulator is locked → probe count +1.
**Human checks:** none.

### M4 — Check-in in the app
**Read:** screens.md (Today, Gradual tag picker, History), logic.md (Location acceptance), android.md (Location).
**Build:** `AppContainer`, Today with `FaceView`, single-tap save + async location update, follow-up sheet (gradual picker + comment), History, Entry detail (edit/delete), minimal onboarding (location), Capture location setting.
**Claude verifies:** Compose UI tests; E10, E12 (in-app part), E17 and E18 for Today and the sheet; screenshots reviewed.
**Human checks:** H2 (in-app part).

### M5 — Check-in notification and reminders → dogfood build 1
**Read:** android.md (The check-in notification, Saving from a notification tap, Reminders, Lock-screen check-in), logic.md (CheckInMachine), screens.md (Onboarding, Copy).
**Build:** channel, `CheckInNotifier`, `CheckInReceiver`, restore timer, Reminders screen (up to 6 times, weekdays), reminder alarms and `SystemEventsReceiver`, the Keep a check-in setting, the Lock-screen check-in screen with Send a test check-in, onboarding steps 3 and 5, debug events wired to the real paths.
**Claude verifies:** Robolectric one-notification tests; E1–E9, E11, E12 (notification part), E13, E19.
**Human checks:** H1; start H3; install the dogfood build yourself (`./gradlew :app:installDogfood` with your phone connected) and start using it (H8).
**Done when:** all pass and the human accepts. From here on, schema changes only through migrations.

### M6 — Detailed emotion wheel
**Read:** screens.md (Detailed wheel).
**Build:** wheel (two rings, largest possible tap targets, chip echo), list picker for TalkBack / large text, persisted mode.
**Claude verifies:** Compose tests (both modes produce the same tag ids; remembered mode); E17/E18 screenshots of the wheel and list.
**Human checks:** H4 for the tag list.

### M7 — Places UI
**Read:** logic.md (Place clustering), screens.md (Places, History).
**Build:** naming sheet (Home / Work / custom, "Is this {name}?" merge), Places screen, place names in History and Entry detail, unnamed descriptions.
**Claude verifies:** unit and UI tests; on the emulator, entries at three `geo.sh` points (two within 40 m, one 2 km away) → naming one place names its cluster (screenshot of History).
**Human checks:** none.

### M8 — Weekly card and support
**Read:** logic.md (Library matching and card building, Low-mood support), screens.md (Week, Support, Copy), data.md (support.json).
**Build:** Week tab (latest, past, pre-first-card state, unseen dot), card view with the Early hint label and "Name this place", low-week block, low-run card, Support screen, weekly card time setting, card built on the first open after the card time; Debug: seed demo data, pattern debugger.
**Claude verifies:** Robolectric (no notification on card build); E15; renaming a place updates an existing card (screenshot before/after).
**Human checks:** read one demo card of each variant for tone.
**Then:** the human runs the one-week self-trial with the real card.

### M9 — Privacy and security
**Read:** android.md (App lock and recents, Export and import, Delete all), screens.md (Onboarding, Privacy copy).
**Build:** app lock and recents privacy, export/import, Delete all, full onboarding, Privacy screen, permission rationale strings, `fixtures/export/vector-1.json` (fixed password, salt, nonce, 1,000 iterations, base64 plaintext → expected envelope).
**Claude verifies:** export tests (round trip into an empty DB, wrong password fails cleanly, importing twice doesn't duplicate, newer `updatedAt` wins, the vector decrypts and re-encrypts identically); E14, E16, E19.
**Human checks:** H5, H6, H7.

### M10 — Polish and release prep
**Read:** product.md, screens.md, testing.md.
**Build:** accessibility pass, empty and error states (location denied, notifications denied → rating in the app only, save failed), release signing (with the human), debug tools only in debug.
**Claude verifies:** `scripts/check.sh`; the full emulator set E1–E19; spec-reviewer over the whole repo against product.md.
**Human checks:** H1–H8 complete; `support.json` filled and verified (or left empty on purpose); Play Data safety form ("no data collected or shared"); internal testing build.

---

## Appendix — Decisions for the human

1. **Test phones:** at least one real Android phone; a Pixel and a Samsung if possible (M1, H1).
2. **Face labels:** Low / Okay / Good on the buttons and in the app. Change before M4 if you prefer other words.
3. **Location from notification taps (after M1):** last known only, or a short location foreground service. Choosing the service means you also remove the `FOREGROUND_SERVICE` rule from `.claude/hooks/guard_edit.py` and from `scripts/privacy-guard.sh` and `check.sh`.
4. **Support:** what goes into `support.json` (if anything) and when the support card appears. Default: after the last 5 entries are all Low across at least 2 days.
5. **Weekly card default time:** Sunday 19:00.
6. **App lock default:** pre-selected ON in onboarding.
7. **Identifiers:** `applicationId` (then update the two package deny rules in `.claude/settings.json`), Play account, release signing key (never committed; the deny rules keep Claude from reading key files).
