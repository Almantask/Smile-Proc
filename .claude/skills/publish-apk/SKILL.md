---
name: publish-apk
description: Build the latest SmileProc Android APKs (dogfood and debug) and copy them to E:\GDrive\APK. Use at the end of every task, milestone, bug fix, or feature, or whenever the user asks to build, publish, copy, export, or deliver APKs to E:\GDrive\APK.
---

# Publish SmileProc APKs to Google Drive

Build the latest `dogfood` and `debug` Android APKs and copy them directly to `E:\GDrive\APK`.

## Invariant

> [!IMPORTANT]
> **Always Run at the End of a Task:**
> At the end of every feature addition, milestone implementation, bug fix, or coding session in this repo, run this skill to assemble fresh APKs and place them in `E:\GDrive\APK`. Conclude by providing clickable file links to the updated APKs in `E:\GDrive\APK`.

## Quick Start (Git Bash on Windows)

Always execute the publish helper script via **Git Bash** using single-quoted strings:

```powershell
& "$env:ProgramFiles\Git\bin\bash.exe" -c 'scripts/publish-apk.sh'
```

Alternatively, invoke Gradle and copy via PowerShell:

```powershell
& "$env:ProgramFiles\Git\bin\bash.exe" -c '. scripts/lib/env.sh && ./gradlew :app:assembleDogfood :app:assembleDebug'
Copy-Item -Path "app\build\outputs\apk\dogfood\app-dogfood.apk" -Destination "E:\GDrive\APK\SmileProc-dogfood.apk"
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "E:\GDrive\APK\SmileProc-debug.apk"
```

## Published Artifacts

The script copies to `E:\GDrive\APK`:
1. `SmileProc-dogfood.apk`: The human's daily app (release code, debug signature, target `com.example.smileproc`).
2. `SmileProc-M<n>-dogfood-<version>.apk`: Versioned and milestone-tagged dogfood archive.
3. `SmileProc-debug.apk`: The debug app with probe and debug event receivers (target `com.example.smileproc.debug`).

## Reporting to User

Always conclude with clickable links in markdown:
- [SmileProc-dogfood.apk](file:///E:/GDrive/APK/SmileProc-dogfood.apk)
- [SmileProc-debug.apk](file:///E:/GDrive/APK/SmileProc-debug.apk)
