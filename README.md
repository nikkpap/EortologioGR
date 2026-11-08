# Εορτολόγιο+ · Android

Native Greek Android name-day and personal dates calendar.

## Download
**[Android v1.0.0 signed APK](https://github.com/nikkpap/EortologioGR/releases/download/v1.0.0/EortologioGR-v1.0.0-release.apk)** · [Release notes](https://github.com/nikkpap/EortologioGR/releases/tag/v1.0.0)

Supports Android 8.0+ (API 26+). Installs as `gr.eortologio.plus`.

## Features
- Greek calendar and events stored locally
- Add, edit, and delete personal dates
- Daily reminder schedule without persistent background services
- Manual check for updates through the GitHub Releases page

## Build
Android Studio, JDK 17, Android Gradle Plugin 8.9.2, Gradle 8.11.1.
Run `gradle :app:assembleDebug` to generate a development APK.
Run `gradle :app:assembleRelease` for an **unsigned** release, then sign securely using the private release signing key.

## Release status
GitHub Actions successfully compiled Android debug and unsigned release APKs. The published APK is a separately signed, verified release (SHA-256: `ab94c0eddd51f1a2acdf35bc289c7b0eabe09a1ba4f8d7f22e5823b34fee4ad0`).
A complete device QA pass has **not** yet been performed.
The repository's built-in calendar asset is a smaller starter dataset; the signed v1.0.0 APK includes the imported legacy name-day data (358 parsed entries). Future sources and builds should synchronize this data.

**OTA limitations:** The in-app option currently opens the official GitHub release page; it does not perform a silent or automatic in-app APK install. Future updates require the same signing key and an increased versionCode.

No personal events or private signing keys are committed to this public repository.
