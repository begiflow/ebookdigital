plugins {
    alias(libs.plugins.leaf.kotlin.library)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
