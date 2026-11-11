import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Έκδοση: appVersionBase (gradle.properties) + "." + αριθμός build του GitHub Actions.
// Π.χ. base 1.2, run 12 → versionName 1.2.12, versionCode 102012. Τοπικό build: 1.2.0 / 102000.
val base = (project.findProperty("appVersionBase") as String? ?: "1.1").trim()
val build = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 0
val (maj, min) = base.split(".").let { (it.getOrNull(0)?.toIntOrNull() ?: 1) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }

android {
    namespace = "gr.eortologio.plus"
    compileSdk = 35
    defaultConfig {
        applicationId = "gr.eortologio.plus" // ΠΟΤΕ μην αλλάξει — αλλιώς δεν γίνεται ενημέρωση
        minSdk = 26
        targetSdk = 35
        versionName = "$base.$build"
        versionCode = maj * 100000 + min * 1000 + build
    }
    buildTypes {
        release {
            // Μικρό και γρήγορο APK (R8). Η υπογραφή γίνεται στο CI με apksigner, όχι εδώ.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
