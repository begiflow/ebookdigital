plugins {
    alias(libs.plugins.leaf.android.library)
}

android {
    namespace = "com.leaf.renderer"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":physics"))
    implementation(project(":filament"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
