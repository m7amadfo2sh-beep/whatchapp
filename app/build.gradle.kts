plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.whatchapp.hourlybuzz"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.whatchapp.hourlybuzz"
        minSdk = 30 // Wear OS 3+
        targetSdk = 34 // Wear OS 5 (Galaxy Watch 7)
        versionCode = 1
        versionName = "1.0"

        // Many Wear OS watches run a 32-bit system even on 64-bit chips, so ship both.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild {
            cmake { arguments += listOf("-DANDROID_STL=c++_static") }
        }
    }

    // whisper.cpp speech recognition for the recitation checker (app/src/main/cpp).
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // The speech model is copied out of the APK on first use; keep it uncompressed.
    androidResources {
        noCompress += "bin"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the release APK installs without extra setup.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

android {
    testOptions {
        unitTests.all {
            // Real-audio recitation test (see RecitationAudioTest); skipped when unset.
            it.systemProperty("reciteE2E", System.getenv("RECITE_E2E") ?: "")
            it.maxHeapSize = "2g"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
