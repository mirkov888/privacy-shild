plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mora.shield.locationhook"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mora.shield.locationhook"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // This companion APK is sideloaded for LSPosed, not distributed through Google Play.
    // Keep the legacy target SDK for Xposed compatibility while suppressing only this Play policy lint.
    lint {
        disable += "ExpiredTargetSdkVersion"
    }
    signingConfigs {
        create("moraRelease") {
            val storePath = System.getenv("MORA_KEYSTORE_PATH")
            val storePass = System.getenv("MORA_KEYSTORE_PASSWORD")
            val alias = System.getenv("MORA_KEY_ALIAS")
            val keyPass = System.getenv("MORA_KEY_PASSWORD")
            if (!storePath.isNullOrBlank() && !storePass.isNullOrBlank() &&
                !alias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("MORA_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else signingConfigs.getByName("moraRelease")
        }
    }
}

dependencies {
    implementation(project(":location-engine"))
    compileOnly("de.robv.android.xposed:api:82")
}
