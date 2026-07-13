plugins {
    alias(libs.plugins.leaf.android.application)
    alias(libs.plugins.leaf.android.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leaf.app"

    defaultConfig {
        applicationId = "com.leaf.notebook"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":designsystem"))
    implementation(project(":domain"))
    implementation(project(":renderer"))
    implementation(project(":bookshelf"))
    implementation(project(":editor"))
    implementation(project(":camera"))
    implementation(project(":search"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
