plugins {
    alias(libs.plugins.leaf.android.library)
}

android {
    namespace = "com.leaf.data"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
}
