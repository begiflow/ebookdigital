plugins {
    alias(libs.plugins.leaf.android.library)
}

android {
    namespace = "com.leaf.filament"
}

dependencies {
    implementation(project(":core"))
    api(libs.filament.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
