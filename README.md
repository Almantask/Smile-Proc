# SmileProc

A private mood check-in for iPhone and Android. Three faces, one tap to save, and a weekly card computed on the phone. No account, no server, no analytics.

The shared rules, copy, and test fixtures live in `spec/`. The apps are native: SwiftUI in `ios/`, Jetpack Compose in `android/`. `spec/engine-rules.md` is the behavior both engines have to match.

## Android debug build

This needs JDK 21 and Android SDK 37.1 (build-tools 37.0.0).

```bash
export ANDROID_HOME="$HOME/android-sdk"   # or your SDK path
echo "sdk.dir=$ANDROID_HOME" > android/local.properties
scripts/test-android.sh
```

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`. A copy checked in for sideloading is `dist/SmileProc-debug.apk` once a build has been produced.

Install on a device with USB debugging:

```bash
adb install -r dist/SmileProc-debug.apk
```

The app id is the placeholder `com.example.smileproc`. Change it before a Play upload.

## iOS

Open the project on a Mac with Xcode 16 or newer. The Xcode project is generated and is not checked in.

```bash
brew install xcodegen   # if you don't already have it
scripts/test-ios.sh
```

Bundle ids and the App Group in `spec/versions.json` are placeholders. Signing needs your Apple team set in `ios/project.yml`.

## Checks

```bash
scripts/check-spec.sh
scripts/privacy-guard.sh
```

`spec/content/helplines.json` is a seed. Every entry is `verified: false`. Shipping is blocked until those numbers are checked against the official sources.
