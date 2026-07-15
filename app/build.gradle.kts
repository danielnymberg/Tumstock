plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "se.nymberg.tumstock"
    compileSdk = 35

    defaultConfig {
        applicationId = "se.nymberg.tumstock"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        // Fast nyckel så att varje bygge signeras lika → uppgraderingar går att
        // installera över varandra. (CI genererar annars en ny debug-nyckel per
        // körning, vilket ger signaturmismatch vid installation.)
        create("stable") {
            storeFile = rootProject.file("danyapps-release.keystore")
            storePassword = "danyapps"
            keyAlias = "danyapps"
            keyPassword = "danyapps"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
}
