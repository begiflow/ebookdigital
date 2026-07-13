// Convention for Android library modules. AGP 9 built-in Kotlin: no kotlin-android
// plugin; jvmTarget defaults to compileOptions.targetCompatibility.
plugins {
    id("com.android.library")
}

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
