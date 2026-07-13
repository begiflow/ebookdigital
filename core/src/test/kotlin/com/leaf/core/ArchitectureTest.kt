package com.leaf.core

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Enforces the module dependency rules from docs/02-ARCHITECTURE.md and
 * docs/06-MODULES.md. These rules are the reason the engine cluster stays
 * reusable; failures here are architecture regressions, not style issues.
 */
class ArchitectureTest {

    private val scope: KoScope = Konsist.scopeFromProject()

    private fun filesUnder(module: String) =
        scope.files.filter { it.path.contains("/$module/src/main/") }

    private fun assertNoImports(module: String, forbiddenPrefixes: List<String>, why: String) {
        val violations = filesUnder(module).flatMap { file ->
            file.imports
                .filter { imp -> forbiddenPrefixes.any { imp.name.startsWith(it) } }
                .map { "${file.path}: import ${it.name}" }
        }
        assertTrue(violations.isEmpty(), "$why\n${violations.joinToString("\n")}")
    }

    @Test
    fun `physics is pure Kotlin`() = assertNoImports(
        module = "physics",
        forbiddenPrefixes = listOf("android.", "androidx.", "com.android."),
        why = ":physics must run on the JVM (unit-testable paper simulation)",
    )

    @Test
    fun `domain is pure Kotlin`() = assertNoImports(
        module = "domain",
        forbiddenPrefixes = listOf("android.", "androidx.", "com.android."),
        why = ":domain must not depend on the Android framework",
    )

    @Test
    fun `core is pure Kotlin`() = assertNoImports(
        module = "core",
        forbiddenPrefixes = listOf("android.", "androidx.", "com.android."),
        why = ":core must not depend on the Android framework",
    )

    @Test
    fun `renderer stays free of UI, DI and persistence frameworks`() = assertNoImports(
        module = "renderer",
        forbiddenPrefixes = listOf("androidx.compose.", "dagger.", "javax.inject.", "androidx.room.", "androidx.hilt."),
        why = ":renderer must be reusable outside the app (driven via NotebookRenderer API)",
    )

    @Test
    fun `filament wrapper stays free of UI, DI and persistence frameworks`() = assertNoImports(
        module = "filament",
        forbiddenPrefixes = listOf("androidx.compose.", "dagger.", "javax.inject.", "androidx.room.", "androidx.hilt."),
        why = ":filament must be reusable outside the app",
    )

    @Test
    fun `features never import the data layer`() {
        for (feature in listOf("bookshelf", "editor", "camera", "search", "app")) {
            assertNoImports(
                module = feature,
                forbiddenPrefixes = listOf("com.leaf.data"),
                why = ":$feature must reach data only through domain interfaces",
            )
        }
    }

    @Test
    fun `only the engine cluster touches Filament directly`() {
        // :renderer builds scenes with Filament types via :filament's handles;
        // everyone else must stay behind the NotebookRenderer / FilamentHost APIs.
        for (module in listOf("app", "sample", "bookshelf", "editor", "camera", "search")) {
            assertNoImports(
                module = module,
                forbiddenPrefixes = listOf("com.google.android.filament"),
                why = ":$module must use the :filament wrapper, not raw Filament APIs",
            )
        }
    }
}
