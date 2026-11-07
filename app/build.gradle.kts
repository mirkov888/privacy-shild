plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.mora.shield"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mora.shield"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "3.7.0"
    }

    buildFeatures {
        compose = true
    }

    // Compile the shared Connector API v1 source in the host app.
    sourceSets["main"].java.srcDir("../sdk/src/main/java")

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
            // GitHub Actions wajib menyediakan keystore tetap. Debug fallback hanya untuk build lokal.
            signingConfig = if (System.getenv("MORA_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("moraRelease")
            }
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")

    // Mora Core persistent storage.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
}
