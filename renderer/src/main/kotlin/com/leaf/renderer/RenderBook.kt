package com.leaf.renderer

import android.graphics.Bitmap

/**
 * Engine-side book description, mapped from the domain by presentation
 * (docs/02-ARCHITECTURE.md §4). Pure data — physical dimensions in meters.
 * Page textures/streaming join in M5; physics params in M6.
 */
data class RenderBook(
    val widthMeters: Float,
    val heightMeters: Float,
    val sheetCount: Int,
    val sheetThicknessMeters: Float,
    val coverThicknessMeters: Float,
    val binding: RenderBinding,
    /** Front cover art; null falls back to a flat board color. */
    val frontCover: Bitmap?,
    /** Back cover art; null reuses [frontCover] treatment. */
    val backCover: Bitmap? = null,
    /**
     * Page art by page index (0-based; sheet i = pages 2i front / 2i+1 back).
     * M5 pull path for demo/generated content; replaced by the streaming
     * TextureProvider in M13 (docs/04-GRAPHICS-PIPELINE.md §4).
     */
    val pageBitmapProvider: ((pageIndex: Int) -> Bitmap)? = null,
    /** 0 = floppy diary paper, 1 = passport card stock (PageParams.stiffness). */
    val paperStiffness: Float = 0.5f,
    /** 0..1 show-through weight (PaperSpec.translucency): thin diary paper ~0.4, card stock ~0. */
    val paperTranslucency: Float = 0.25f,
    /** Paper grain family; selects the procedural grain normal map + roughness. */
    val grain: RenderGrain = RenderGrain.LAID,
) {
    val blockThicknessMeters: Float = sheetCount * sheetThicknessMeters
    val totalThicknessMeters: Float = blockThicknessMeters + 2f * coverThicknessMeters

    companion object {
        /** A5 vaccination-booklet-ish defaults for demos and tests. */
        fun a5(sheetCount: Int = 40, frontCover: Bitmap? = null) = RenderBook(
            widthMeters = 0.148f,
            heightMeters = 0.210f,
            sheetCount = sheetCount,
            sheetThicknessMeters = 0.00012f,
            coverThicknessMeters = 0.002f,
            binding = RenderBinding.SEWN,
            frontCover = frontCover,
        )
    }
}

/**
 * Renderer-side binding selector; maps 1:1 from domain Binding. Each value
 * gets its own BindingStrategy (spine mesh + pivot model) — SEWN is the M3
 * reference, the rest complete in M11 (docs/03-RENDERER.md §4).
 */
enum class RenderBinding {
    SEWN,
    STAPLED,
    SPIRAL,
    GLUED,
}

/** Renderer-side grain selector; maps 1:1 from domain GrainKind (docs/04 §2.1). */
enum class RenderGrain {
    LAID,
    WOVEN,
    GLOSS,
}

/**
 * Live paper-feel parameters (M7 tuning harness, docs/05-PHYSICS.md §6).
 * Applied to the next page grab — physics params are per-strip — except
 * [translucency], which updates the paper material immediately.
 */
data class PaperTuning(
    val stiffness: Float,
    val damping: Float,
    val airDrag: Float,
    val translucency: Float,
) {
    companion object {
        fun fromBook(book: RenderBook) = PaperTuning(
            stiffness = book.paperStiffness,
            damping = 2.8f,
            airDrag = 12f,
            translucency = book.paperTranslucency,
        )
    }
}
