import com.android.build.api.dsl.CommonExtension

// Adds Compose on top of leaf.android.library / leaf.android.application.
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

(extensions.getByName("android") as CommonExtension).apply {
    buildFeatures.compose = true
}
