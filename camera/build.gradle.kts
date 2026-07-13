plugins {
    alias(libs.plugins.leaf.android.library)
    alias(libs.plugins.leaf.android.compose)
}

android {
    namespace = "com.leaf.camera"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // CameraX + OpenCV arrive with M12 (docs/07-ROADMAP.md)
}
