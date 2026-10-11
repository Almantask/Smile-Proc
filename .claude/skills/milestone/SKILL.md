---
name: milestone
description: Run one milestone of the SmileProc plan end to end — plan, build, verify, review, commit, tag. Started by the human with /milestone M<n>.
argument-hint: M<n>
disable-model-invocation: true
---
Run milestone $ARGUMENTS from android-implementation-plan.md, Part D.

1. Read docs/progress.md. If the previous milestone isn't marked "accepted", stop and say so. (M0 has no previous milestone, and before M0 there is no progress.md.)
2. Read the $ARGUMENTS section of Part D, then only the spec files it lists (docs/spec/; before M0 has created it, the matching sections of Part C).
3. Check where you're running. If `adb` or the Android SDK is missing, this is a cloud session and only pure-Kotlin work fits (M2, and the non-Android steps of M0). For any other milestone, say what can't be done here and ask whether to do the parts that can, or stop.
4. Enter plan mode. Present a short plan: files to create or change, tests to write, emulator scenarios to run, what can't run in this session, and anything that needs the human. Wait for approval.
5. Implement in small steps. After each green step: `git add -A && git commit -m "$ARGUMENTS: <what>"`.
6. Run `scripts/check.sh` until it passes. Keep the last lines of its output for the summary. In a cloud session, run what works there (`scripts/privacy-guard.sh`, `python3 tools/generate_fixtures.py --check`, `./gradlew :core:test`) and list the rest as not run.
7. If the milestone lists emulator scenarios, delegate them to the emulator-tester subagent with the scenario ids. Fix failures and rerun the failed ones. With no emulator, list them as "not run (cloud)".
8. Delegate the reviews in parallel, each with the milestone id and the git range since the previous milestone's tag (M0: since the first commit):
   - spec-reviewer, with the spec files listed for this milestone;
   - copy-reviewer, if the range touches strings.xml, content/pattern-library*.json or any other user-facing text.
   Fix findings that break the spec, a non-negotiable or a copy rule; list the rest under "Not fixed" in progress.md.
9. Update docs/progress.md (status, built, left, deviations, not run, human checks, decisions needed) and docs/device-test-checklist.md.
10. Commit. If everything this milestone asks Claude to verify passed, set the status to "claude-verified" and `git tag <milestone in lower case>-claude-verified`. If anything is "not run", set "partly verified" and don't tag. In a cloud session, push the branch and any tag.
11. Stop and report: what was built; evidence (commands and their results, screenshot paths); what was not run and why; the Human checks for this milestone; decisions needed.

When the human replies "M<n> accepted", mark it "accepted" in docs/progress.md and commit.
