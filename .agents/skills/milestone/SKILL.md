---
name: milestone
description: Execute one milestone (M0–M10) of the SmileProc implementation plan end to end — plan, implement, verify with check.sh, delegate emulator and spec/copy reviews, update progress, commit, and tag. Use when the human invokes /milestone M<n> or explicitly asks to run a SmileProc milestone.
---

# SmileProc Milestone Runner

Run target milestone `M<n>` (from `/milestone M<n>`) in `android-implementation-plan.md`, Part D. If no `M<n>` identifier was supplied, ask the human which milestone (`M0`–`M10`) to run before starting.

## Quick start (Windows Git Bash & Git)

On Windows, `run_command` runs PowerShell where bare `bash` is WSL. Always invoke repo scripts via **Git Bash** with single quotes:

```powershell
& "$env:ProgramFiles\Git\bin\bash.exe" -c 'scripts/check.sh'
git add -A; if ($LASTEXITCODE -eq 0) { git commit -m "M2: implement PlaceResolver" }
```

## Workflow

1. **Check prerequisite status:** Read `docs/progress.md` with `read_file`. Unless running `M0`, verify the previous milestone is marked `accepted` — if not, stop and tell the human. Check open `Decisions needed` affecting `M<n>`.
2. **Load scoped requirements:** Read only the `M<n>` section of `android-implementation-plan.md` (Part D) and the specific `docs/spec/*.md` files it lists (plus matching `.agents/rules/*.md`).
3. **Detect environment:** Check whether `adb` and `ANDROID_HOME` resolve under `scripts/lib/env.sh`. Without Android SDK/`adb` (cloud session), only pure-Kotlin work fits (`M2` and non-Android steps of `M0`); for other milestones, state what cannot run and ask whether to run the pure-Kotlin subset or stop.
4. **Plan and wait for approval:** Present a concise plan (or `implementation_plan.md` artifact with `RequestFeedback: true`) covering: files to create/modify, tests to add, `E*` emulator scenarios, session limitations, and human inputs needed. **Stop and wait for explicit human approval before coding.**
5. **Implement in small green steps:** Edit files via `write_file` / `edit_file` (never Bash heredocs, which mangle `\` on Windows). Respect all non-negotiables (`docs/spec/product.md`). After each green step, commit directly on `main` (`M<n>: <summary>`). Never create feature branches or PRs unless asked.
6. **Run full verification:** Execute `scripts/check.sh` (`WaitMsBeforeAsync: 10000`) until `check.sh: PASS` and keep its summary lines. In a cloud session, run `scripts/privacy-guard.sh`, `scripts/py tools/generate_fixtures.py --check`, and `./gradlew :core:test`, recording the rest as `not run (cloud)`.
7. **Delegate emulator scenarios:** If `M<n>` lists `E*` scenarios and an emulator is available, delegate them via `invoke_subagent` to `emulator-tester` (if not yet registered in subagents, read `.agents/agents/emulator-tester.md` and register it with `define_subagent` using `EnableWriteTools: true` first). Fix any failures and rerun failed scenarios. Without an emulator, record `not run (cloud)`.
8. **Delegate reviews in parallel:** Compute the git range from the previous milestone tag (`m<n-1>-claude-verified..HEAD`; for `M0`, from root commit) and launch in parallel via `invoke_subagent` (registering via `define_subagent` from `.agents/agents/*.md` if needed):
   - `spec-reviewer` with `M<n>`, the git range, and `M<n>`'s listed spec files;
   - `copy-reviewer` whenever the range touches `strings.xml`, `content/pattern-library*.json`, notification copy, or any human-visible text (and always in `M8` and `M10`).
   Fix anything breaking the spec, a non-negotiable, or a copy rule (re-running `scripts/check.sh`); record remaining notes under `Not fixed` in `docs/progress.md`.
9. **Update tracking docs:** Update `docs/progress.md` (`Status`, `Built`, `Evidence`, `Left`, `Deviations`, `Not run`, `Not fixed`, `Human checks`, `Decisions needed`) and `docs/device-test-checklist.md`.
10. **Commit and tag:** Commit on `main`. To stay consistent with existing tags (`m0-claude-verified`, `m1-claude-verified`) and `docs/progress.md`, set status to `claude-verified` and run `git tag <m<n>-lowercase>-claude-verified` when all milestone checks pass; if anything is `not run`, set `partly verified` and do not tag. Push `main` and any new tag in cloud sessions or when asked.
11. **Report and await acceptance:** Summarize what was built, verification outputs, screenshot paths, `not run` items, `Human checks`, and `Decisions needed`. When the human replies `"M<n> accepted"`, set status to `accepted` in `docs/progress.md` and commit.
