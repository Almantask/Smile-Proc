# Progress

## Built

- Repo layout, `AGENTS.md`, and this doc set.
- Shared content: emotion tags, pattern config, pattern library, helpline seed.
- English strings and generators (`spec/tools/gen_strings.py`, `spec/tools/generate_fixtures.py`).
- Reference engine (`spec/tools/engine.py`) and fixtures, including goldens, periods, places, low-mood cases, library coverage, canonical JSON, and the export/seal vectors.
- Android SDK 37.1 and build-tools 37.0.0 are installed on the build machine. The apps themselves are in progress.

## Left

- Android app and Kotlin `:core`, then a debug APK.
- iOS app and `SmileCore`. This machine has no Xcode, so the iOS build is source-only until a Mac.
- Device spikes S1–S3 and A1–A4. Results table in `docs/spikes.md` is empty.
- Helpline verification. Every entry is still `verified: false`.

## Deviations

- Lead platform is Android, so the build you can install from this environment is the one that gets exercised. Change `AGENTS.md` if the everyday phone is an iPhone.
- Goldens are written by the shared Python engine, not by the first app that happens to pass. Both platforms match those files. Regenerate only by running `spec/tools/generate_fixtures.py` on purpose.
- The plan's JDK toolchain is 17. This machine has JDK 21, which satisfies AGP 9.4's minimum of 17. Kotlin bytecode target stays 17.
- `compileSdk` is API 37 minor 1, the stable platform installed here (`platforms;android-37.1`).
- Implementation continues through a usable v1 instead of stopping after the device spikes. Spike screens still belong in the debug builds, and the results are yours to fill in.
