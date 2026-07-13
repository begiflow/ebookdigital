plugins {
    alias(libs.plugins.leaf.android.application)
}

android {
    namespace = "com.leaf.sample"

    defaultConfig {
        applicationId = "com.leaf.sample"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    // Engine cluster only — no Hilt, no Room, no Compose. The sample proves
    // the engine is drivable standalone (docs/06-MODULES.md).
    implementation(project(":filament"))
    implementation(project(":renderer"))
    implementation(project(":physics"))
}
