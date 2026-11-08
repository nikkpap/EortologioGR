plugins { id("com.android.application") }
android {
    namespace = "gr.eortologio.plus"
    compileSdk = 35
    defaultConfig { applicationId = "gr.eortologio.plus"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "1.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

    // Υπογραφή μόνο όταν υπάρχουν τα secrets (CI). Τοπικά/χωρίς κλειδί βγαίνει unsigned release.
    val ksFile = System.getenv("SIGNING_KEYSTORE")
    if (!ksFile.isNullOrBlank() && file(ksFile).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(ksFile)
                storeType = "pkcs12"
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: System.getenv("SIGNING_STORE_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
        buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release") } }
    }
}
