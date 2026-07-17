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
    implementation(libs.androidx.activity.compose)

    // M12 capture pipeline (docs/01-PRD.md §5.4): CameraX + OpenCV.
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.opencv)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
