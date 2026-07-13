plugins {
    alias(libs.plugins.leaf.android.library)
    alias(libs.plugins.leaf.android.compose)
}

android {
    namespace = "com.leaf.search"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // Gemma Nano + OCR indexing is future scope (docs/01-PRD.md §5.7)
}
