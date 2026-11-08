# Εορτολόγιο+ · Android

Native Greek Android name-day, birthdays and personal events app.

## Project
Android 8+ (API 26), Java 17, Android Gradle Plugin 8.9.2. Offline name-day source based on legacy TODAY.DAT (Windows-1253); Orthodox Easter movable feasts; local personal dates; battery-conscious daily reminders; opt-in OTA updates through GitHub Releases.

## OTA
Updates are checked against https://api.github.com/repos/nikkpap/EortologioGR/releases/latest. Each published release should contain a release-signed APK named `EortologioGR-vX.Y.Z.apk`. The app checks a SHA-256 asset digest before opening Android's normal package installer. Use the **same release signing key** and an incremented `versionCode` for every update.

Personal records are stored only on the device. Never commit `CUSTOM.INI` with real personal information, private events, or signing files.

## Android Studio
Import the full Android source archive supplied in the project conversation and build with Android Studio / JDK 17. This repository is being bootstrapped; the full Android source tree must be committed before CI can produce an APK.

## Status
Source starter available as a ZIP; no compiled APK, CI build, or on-device OTA validation is claimed yet.
