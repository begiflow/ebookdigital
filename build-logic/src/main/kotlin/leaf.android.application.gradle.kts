// Convention for Android application modules (:app, :sample).
plugins {
    id("com.android.application")
}

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Debug signing so release variants are installable for local perf testing;
            // real signing config arrives with the release process (M16).
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
