plugins {
    alias(libs.plugins.leaf.android.library)
    alias(libs.plugins.leaf.android.compose)
}

android {
    namespace = "com.leaf.designsystem"
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
}
