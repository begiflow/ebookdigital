plugins {
    alias(libs.plugins.leaf.android.library)
    alias(libs.plugins.leaf.android.compose)
}

android {
    namespace = "com.leaf.editor"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
