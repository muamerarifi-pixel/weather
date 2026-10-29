import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Every GitHub Actions build gets a higher version, so a new APK installs over the old one.
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "app.moti.kosova"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.moti.kosova"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    // The signing key is never stored in this public repo: CI writes it to keystore/ at build
    // time (from the MOTI_KEYSTORE_B64 secret if set, otherwise a one-time key).
    val keystore = rootProject.file("keystore/moti.jks")
    val keyPass = System.getenv("MOTI_KEYSTORE_PASSWORD") ?: ""

    signingConfigs {
        if (keystore.exists()) {
            create("release") {
                storeFile = keystore
                storeType = "PKCS12"
                storePassword = keyPass
                keyAlias = "moti"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystore.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
