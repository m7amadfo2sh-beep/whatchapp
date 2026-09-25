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

dependencies {
    testImplementation("junit:junit:4.13.2")
}
