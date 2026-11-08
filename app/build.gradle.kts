plugins { id("com.android.application") }

// Έκδοση: appVersionBase (gradle.properties) + "." + αριθμός build του GitHub Actions.
// Π.χ. base 1.1, run 12 → versionName 1.1.12, versionCode 101012. Τοπικό build: 1.1.0 / 101000.
val base = (project.findProperty("appVersionBase") as String? ?: "1.1").trim()
val build = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 0
val (maj, min) = base.split(".").let { (it.getOrNull(0)?.toIntOrNull() ?: 1) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }

android {
    namespace = "gr.eortologio.plus"
    compileSdk = 35
    defaultConfig {
        applicationId = "gr.eortologio.plus" // ΠΟΤΕ μην αλλάξει — αλλιώς δεν γίνεται ενημέρωση
        minSdk = 26; targetSdk = 35
        versionName = "$base.$build"
        versionCode = maj * 100000 + min * 1000 + build
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    // Η υπογραφή γίνεται στο CI με apksigner (secret SIGNING_KEYSTORE_BASE64), όχι εδώ.
}
