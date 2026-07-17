plugins {
    alias(libs.plugins.leaf.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.leaf.data"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)

    // M13 storage (docs/02 §3): Room + file store + texture pipeline.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.opencv)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
