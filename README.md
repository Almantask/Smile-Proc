# SmileProc

A private mood check-in for Android. Three faces — Low, Okay, Good — from a notification or from the app. History stays on the phone: no account, no server, no network permission.

This repository is at **M0** (scaffold). The hello screen is only a wordmark. Check-in, storage, and notifications come in later milestones. See `docs/progress.md`.

## Checks

```bash
scripts/check-fixtures.sh
scripts/test.sh
scripts/privacy-guard.sh
```

`test.sh` runs `:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:lintDebug`. Compose UI tests on a device or emulator, later:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Fixtures are generated, not edited by hand:

```bash
python3 tools/generate_fixtures.py          # write fixtures/
python3 tools/generate_fixtures.py --check  # fail if they drift
```

## Build

- JDK 17 toolchain (Gradle can download it).
- Android SDK platform **37.1**, build-tools 37.
- `local.properties` with `sdk.dir`, or `ANDROID_HOME`.

`applicationId` is the placeholder `com.example.smileproc`.

## Layout

- `content/` — tag ids, pattern thresholds, the English pattern library, and human-owned `support.json`. Shipped as app assets. Do not add names, numbers, or links to `support.json`.
- `core/` — pure Kotlin. No Android imports.
- `app/` — Compose UI and, in later milestones, Room, Keystore, location, and notifications.
- `fixtures/` — generated inputs and expectations for the core rules.
- `AGENTS.md` — non-negotiables.

## Privacy

The debug APK does not request `INTERNET`. `scripts/privacy-guard.sh` fails the build if that changes, if a widget, Quick Settings tile, or shortcut appears, or if a notification is posted from anywhere but `CheckInNotifier`.
