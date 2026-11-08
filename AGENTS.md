# AGENTS.md — Εορτολόγιο+ (EortologioGR)

Οδηγίες για κάθε AI agent (Claude, Codex κ.λπ.) ή προγραμματιστή.
**Στόχος:** κάθε αλλαγή φτάνει στο `main` → το GitHub Actions χτίζει **υπογεγραμμένο** APK → βγάζει Release →
οι εγκατεστημένες εφαρμογές ενημερώνονται (OTA). **Μην σπάσεις αυτή την αλυσίδα.**

## 1. Με μια ματιά

| | |
|---|---|
| App | Εορτολόγιο+ — ονομαστικές γιορτές, επαφές & ευχές, ειδοποιήσεις |
| applicationId | `gr.eortologio.plus` (**ποτέ μην το αλλάξεις**) |
| Γλώσσα / UI | Java, Android views χωρίς εξαρτήσεις (κανένα AndroidX) |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 · JDK 17 · AGP 8.9.2 · Gradle 8.11.1 |
| Repo | `github.com/nikkpap/EortologioGR` (**public**) · branch `main` |
| Workflow | `.github/workflows/android.yml` («Build & Release») |
| OTA | `OtaUpdater.java` (έλεγχος/λήψη) + `UpdateProvider.java` (δίνει το APK στον installer) |
| Δεδομένα | `assets/TODAY.DAT` (γιορτές), `assets/NAMES.DAT` (παραλλαγές/greeklish) |

## 2. Η ροή

```
push στο main
  └─► «Build & Release»
        1. Έκδοση = appVersionBase (gradle.properties) + "." + run_number   π.χ. 1.1.12
        2. gradle :app:assembleRelease (unsigned)
        3. zipalign + apksigner με το secret SIGNING_KEYSTORE_BASE64 (+ SIGNING_STORE_PASSWORD)
        4. Έλεγχος: SHA-256 πιστοποιητικού == 76b4e155…120112c (αυτό της v1.0.0)
        5. Release v<έκδοση>, asset EortologioGR-v<έκδοση>.apk + SHA256SUMS.txt, latest
  └─► Εφαρμογή (OtaUpdater)
        • api.github.com/repos/nikkpap/EortologioGR/releases/latest — στο άνοιγμα, το πολύ ανά 6 ώρες
        • αν tag > versionName → «Νέα έκδοση» → λήψη πρώτου *.apk → system installer
```

| Περίπτωση | Release; |
|---|---|
| push στο `main` με αλλαγή κώδικα/πόρων | ✅ |
| μήνυμα commit με `[no release]` | ❌ μόνο build |
| αλλάζουν μόνο `*.md` / `docs/**` | ❌ δεν τρέχει |
| pull request | ❌ μόνο build |
| λείπει το secret | ❌ build + unsigned artifact, warning |
| αποτυχία build ή λάθος κλειδί | ❌ κόκκινο ✗ — οι συσκευές δεν επηρεάζονται |
| Actions → Build & Release → **Run workflow** (στο main) | ✅ νέα έκδοση |

## 3. Υπογραφή (το πιο κρίσιμο)

Το Android δέχεται ενημέρωση **μόνο** με ίδια υπογραφή. Αλλαγή κλειδιού = απεγκατάσταση σε όλους.

| Στοιχείο | Τιμή |
|---|---|
| Κλειδί | αρχείο PKCS#12 (`.p12`), CN «EortologioGR Android Release» — **ποτέ στο git** (`.gitignore`) |
| Secret (υποχρεωτικό) | `SIGNING_KEYSTORE_BASE64` = base64 του `.p12` |
| Secret (υποχρεωτικό) | `SIGNING_STORE_PASSWORD` = κωδικός του `.p12` |
| Secrets (προαιρετικά) | `SIGNING_KEY_ALIAS` (αν το `.p12` έχει >1 κλειδί), `SIGNING_KEY_PASSWORD` (αν διαφέρει) |
| SHA-256 πιστοποιητικού | `76:B4:E1:55:A3:2F:4A:78:63:C3:8D:EE:A1:41:CC:21:75:A5:73:DB:72:5B:00:E3:EE:AF:A9:68:C1:20:11:2C` |

Τα secrets τα βάζει **μόνο ο ιδιοκτήτης** (τα agents δεν έχουν πρόσβαση):
GitHub → repo → **Settings → Secrets and variables → Actions → New repository secret**.

### ⛔ Ποτέ
- Commit του `.p12`, του base64 του ή του κωδικού (το repo είναι public).
- Αλλαγή `applicationId`, κλειδιού ή του `EXPECTED_CERT_SHA256` χωρίς τον ιδιοκτήτη.
- Αφαίρεση `REQUEST_INSTALL_PACKAGES`, `UpdateProvider` ή του `<provider>` από το manifest.
- Αλλαγή του repo στο `OtaUpdater.API` ή μετονομασία asset (η εφαρμογή παίρνει το **πρώτο** `*.apk`).
- Force-push στο `main`, διαγραφή tags/Releases.

## 4. Εκδόσεις
- `gradle.properties` → `appVersionBase=1.1` (μόνο αυτό αλλάζει ο άνθρωπος, π.χ. `1.2`).
- CI: `versionName = <base>.<run>`, `versionCode = major*100000 + minor*1000 + run`.
- Τοπικό build: `<base>.0` — πάντα μικρότερο από τα CI builds.
- Μην κατεβάσεις ποτέ το `appVersionBase`· το `versionCode` πρέπει πάντα να ανεβαίνει.

## 5. Πώς δουλεύει ένα agent
1. `git fetch origin main && git rebase origin/main`
2. Αλλαγές. Κείμενα UI στα ελληνικά, απλά («για όλους»). Χρώματα από την παλέτα στο `MainActivity.setupWindow()`.
3. Χωρίς Android SDK: compile-έλεγχος με `javac --release 17 -cp android.jar` (+ stub `R`), αλλιώς το **CI είναι ο compiler**.
4. **Commit στα ελληνικά** — το μήνυμα γίνεται οι σημειώσεις του Release:
   ```
   Σύντομος τίτλος

   - τι άλλαξε 1
   - τι άλλαξε 2
   ```
   Μισοτελειωμένη δουλειά → `[no release]`.
5. `git push origin main` → παρακολούθηση:
   ```bash
   gh run list -R nikkpap/EortologioGR --limit 1
   gh api repos/nikkpap/EortologioGR/releases/latest --jq .tag_name
   ```
6. Αναφορά στον χρήστη: ποια έκδοση βγήκε και τι αλλάζει.

## 6. Συμβάσεις κώδικα
- Χωρίς εξωτερικές βιβλιοθήκες (μικρό APK, γρήγορο build).
- Δεδομένα: `TODAY.DAT` → `Η/Μ Όνομα` ή `+Ν/-Ν Όνομα` (από το Πάσχα), `"…"` = αργία. Επίσημο όνομα· τα υποκοριστικά στο `NAMES.DAT`.
- Κανόνας Αγ. Γεωργίου/Μάρκου στο `EventStore.Event.onYear()` — μην τον αφαιρέσεις.
- Μπαταρία: **κανένα** background service. Ένα alarm/ημέρα (`setAndAllowWhileIdle`) + snooze.
- Επαφές: μόνο ανάγνωση, τίποτα δεν αποθηκεύεται/στέλνεται εκτός συσκευής.

## 7. Γρήγορη αντιμετώπιση
| Σύμπτωμα | Λύση |
|---|---|
| Πράσινο αλλά χωρίς Release | λείπει secret, `[no release]`, ή PR |
| «Άλλο κλειδί» στο log | λάθος `.p12` στο secret |
| «Η εφαρμογή δεν εγκαταστάθηκε» | διαφορετική υπογραφή (τοπικό/unsigned build) |
| Η συσκευή δεν βλέπει την ενημέρωση | Release `latest`; υπάρχει `.apk`; Ρυθμίσεις → Έλεγχος τώρα |
