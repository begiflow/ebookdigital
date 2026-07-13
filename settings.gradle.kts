pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "leaf"

// App + features
include(":app")
include(":bookshelf")
include(":editor")
include(":camera")
include(":search")

// Clean architecture layers
include(":domain")
include(":data")

// Engine cluster — reusable, no Hilt/Room/Compose (enforced by Konsist tests in :core)
include(":renderer")
include(":physics")
include(":filament")

// Shared
include(":core")
include(":designsystem")

// Validation
include(":sample")
include(":benchmark")
