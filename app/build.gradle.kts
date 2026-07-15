plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "se.nymberg.matverktyg"
    compileSdk = 35

    defaultConfig {
        applicationId = "se.nymberg.matverktyg"
        // minSdk 29: MediaStore-export utan behörighet (IS_PENDING-flödet).
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "2.1.0"

        ndk {
            // Endast arm64: håller APK:n ~30 MB mindre än alla fyra ABI:er.
            // Alla relevanta enheter (inkl. Nothing Phone 3) är arm64.
            abiFilters += "arm64-v8a"
        }
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
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Kortdetektering (körs helt lokalt; init via OpenCVLoader.initLocal()).
    implementation("org.opencv:opencv:4.12.0")
}
