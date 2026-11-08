# Εορτολόγιο+ · Android

Native Greek Android name-day and personal dates calendar.

## Download
**[Android v1.0.0 signed APK](https://github.com/nikkpap/EortologioGR/releases/download/v1.0.0/EortologioGR-v1.0.0-release.apk)** · [Release notes](https://github.com/nikkpap/EortologioGR/releases/tag/v1.0.0)

Supports Android 8.0+ (API 26+). Installs as `gr.eortologio.plus`.

## Features (v1.1.0)
- **Modern UI** with light/dark theme, gradient header, month grid calendar, edge-to-edge (Android 15 ready)
- **Search** by any spelling: Γιώργος / Γεώργιος / Giorgos / George — accent-insensitive, greeklish aware
- **Contacts sync**: shows which contacts celebrate a name day or birthday; one-tap "Χρόνια πολλά" via SMS, WhatsApp, Viber or any app, with the name in the vocative (Νίκο, Γιώργο, Αλέξανδρε)
- **Daily notification** "Σήμερα γιορτάζουν" with **snooze** buttons (configurable) and a "Ευχές" shortcut
- **Battery friendly**: no background service, one wake-up per day (`setAndAllowWhileIdle`, works in Doze); in-app shortcut for battery-optimisation exemption
- 378 name days incl. movable feasts (Λάζαρος, Λάμπρος, Θωμάς, Ζωοδόχος Πηγή…) and the St. George / St. Mark rule after Easter
- Personal dates with type (birthday/name day/anniversary) and optional year (age)
- Step-by-step setup guide on first run
- OTA: checks GitHub Releases, downloads and installs the signed APK (same signing key required)

## Build
Android Studio, JDK 17, Android Gradle Plugin 8.9.2, Gradle 8.11.1.
Run `gradle :app:assembleDebug` to generate a development APK.
Run `gradle :app:assembleRelease` for an **unsigned** release, then sign securely using the private release signing key.

## Release status
GitHub Actions successfully compiled Android debug and unsigned release APKs. The published APK is a separately signed, verified release (SHA-256: `ab94c0eddd51f1a2acdf35bc289c7b0eabe09a1ba4f8d7f22e5823b34fee4ad0`).
A complete device QA pass has **not** yet been performed.
The repository now contains the full cleaned name-day dataset (`assets/TODAY.DAT`) and the name-variant table (`assets/NAMES.DAT`).

**OTA:** The app downloads the `.apk` asset of the latest GitHub release and opens the system installer (user confirms). Updates require the same signing key and an increased versionCode.

No personal events or private signing keys are committed to this public repository.
