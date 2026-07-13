package com.leaf.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Paper-first palette: warm paper whites and ink tones so the 2D chrome
 * around the renderer never fights the notebook for attention.
 */
object LeafColors {
    val PaperWhite = Color(0xFFF7F2E7)
    val PaperCream = Color(0xFFEFE6D3)
    val Ink = Color(0xFF2B2B26)
    val InkFaded = Color(0xFF6B675C)
    val LeatherBrown = Color(0xFF6D4A2F)
    val ShelfWood = Color(0xFF8A6845)
}

private val LightColors = lightColorScheme(
    primary = LeafColors.LeatherBrown,
    onPrimary = LeafColors.PaperWhite,
    secondary = LeafColors.ShelfWood,
    onSecondary = LeafColors.PaperWhite,
    background = LeafColors.PaperWhite,
    onBackground = LeafColors.Ink,
    surface = LeafColors.PaperCream,
    onSurface = LeafColors.Ink,
    surfaceVariant = LeafColors.PaperCream,
    onSurfaceVariant = LeafColors.InkFaded,
)

@Composable
fun LeafTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
