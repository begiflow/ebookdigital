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
        versionCode = 2
        versionName = "1.0.0-rc1"
    }
}

dependencies {
    implementation(project(":designsystem"))
    implementation(project(":domain"))
    implementation(project(":data")) // composition root binds the impls (docs/02 §6)
    implementation(project(":renderer"))
    implementation(project(":bookshelf"))
    implementation(project(":editor"))
    implementation(project(":camera"))
    implementation(project(":search"))

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx) // FileProvider (page sharing)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
