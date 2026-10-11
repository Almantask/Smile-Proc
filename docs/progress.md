# SmileProc progress

M0 is ready for review. M1 has not started.

## Built

- Repo layout, `AGENTS.md` (non-negotiables and conventions), `.gitignore`, Gradle wrapper.
- `content/emotion-tags.json`, `pattern-config.json`, `pattern-library.en.json` (the seed text), `support.json` (empty entries only).
- `strings.xml` with the copy from the plan: faces, notifications, tags, observations, onboarding, settings, support, places, export, recovery.
- `tools/generate_fixtures.py` and the committed `fixtures/` inputs and expectations.
- `:core` (JVM, no Android) and `:app` (Compose hello screen). `content/` is an assets source directory. minSdk 29. compileSdk 37.1. targetSdk 37.
- `scripts/check-fixtures.sh`, `scripts/test.sh`, `scripts/privacy-guard.sh`.
- Backups off: `allowBackup="false"`, cloud-backup rules, and device-transfer rules.

## Left

M1 device spikes, then M2 core logic through M10. Do not start M1 until this scaffold is reviewed.

Goldens (`*.golden.json`) are not written yet. M2 writes them with `WRITE_GOLDENS=1` after the Kotlin engine matches every `expect.json`.

## Deviations and choices

- The idea-doc URL returned only the title “Claude Artifact”, not the document. M0 follows the plan. If the doc disagrees, stop and say so.
- Latest stable SDK platform is **37.1** (37.2 is still beta). compileSdk is 37 with minor API 1. targetSdk is 37, because the application DSL takes an integer.
- Latest stable pins, not all wired yet: Kotlin 2.4.21, AGP 9.4.1, Gradle 9.6, Compose BOM 2026.09.00, Room **3.0.3** (`androidx.room3`), KSP **2.3.12**. Room and KSP are in the version catalog only. KSP 2.3.12’s published range is Kotlin 2.3.x, while this project compiles with Kotlin 2.4.21. That pair gets proven when Room arrives in M3; if it does not compile, stop rather than swapping in an unapproved library.
- Biometric latest **stable** is 1.1.0. 1.4.x is still alpha, so it is not pinned.
- JVM toolchain 17, provisioned by the Foojay resolver. The machine running Gradle may be a newer JDK.
- Deep link `smileproc://` is not registered. Nothing in M0 needs a second entry point.
- Manifest permissions for notifications, boot, and location are declared. Receivers, the channel, and `USE_BIOMETRIC` arrive with the features that use them (the biometric library adds its permission).
- Tag colours are accents for chips and dots, with text drawn in `onSurface`, so contrast does not depend on the accent alone. Happy is `#F2C14E` as in the plan’s example. The others are calm `#3F8F86`, sad `#5E86B8`, anxious `#C47D8E`, angry `#D4724E`, tired `#8B84B5`, surprised `#D4923A`.
- The hello screen is a quiet wordmark. It is not a check-in control. No motion.
- Fixture engine choices the Kotlin port in M2 must keep (see the docstring in `tools/generate_fixtures.py`):
  - Sort key is `floor(score * 1e9 + 0.5) / 1e9`.
  - Weekday detectors use `weekdayMinSegment`, `weekdayMinAbsDiff`, and `weekdayMinDistinctDates` for a pattern, and `minSegment` plus those same diff and date thresholds for an early hint. `nRest` still uses `patternMinRest` / `minRest`.
  - “Less than 14 days before” is strict. “Within 14 days” includes the boundary.
  - Tag and place detectors read the card window (14 calendar days when the period has fewer than `shortWeekThreshold` entries). Weekday detectors read the 28-day window.
  - Place detectors skip entries that never joined a cluster.
  - `windowKind` is `week` or `twoWeeks`.
  - Balance replaces the last pick with the first not-yet-picked higher **pattern**, which is why `afternoon-dip` has no competing higher pattern.
  - Low-run span is the count of distinct local dates.
  - `expect.json` keys are exact, including `candidatesInclude` and `itemsIncludeLibraryIDs`.

## Checks

`scripts/check-fixtures.sh`, `scripts/test.sh`, and `scripts/privacy-guard.sh` passed. The debug APK contains the four content JSON files and does not request `INTERNET`. Lint reported no issues.

## Device

Nothing in M0 can be confirmed only on a phone. `assembleDebug` is the build check. Lock screen, notifications, biometrics, GPS, and alarms are still ahead; they are listed in `docs/device-test-checklist.md` for the milestone that builds them.
