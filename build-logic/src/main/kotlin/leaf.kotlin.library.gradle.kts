// Convention for pure-Kotlin JVM modules (:core, :domain, :physics).
// These must stay Android-free — enforced by ArchitectureTest (Konsist).
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}
