package com.leaf.filament

import android.graphics.Bitmap
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.android.TextureHelper
import kotlin.math.log2
import kotlin.math.max

/**
 * Bitmap -> GPU texture path. M3 uploads full bitmaps; the KTX2/ASTC streaming
 * pipeline (docs/04-GRAPHICS-PIPELINE.md §4) replaces this for page textures
 * in M13 — covers and generated art keep using it.
 */
object Textures {

    /**
     * Creates a mipmapped texture from [bitmap] (ARGB_8888). Color content is
     * sRGB; pass [srgb] = false for data textures (normal maps).
     */
    fun fromBitmap(engine: Engine, bitmap: Bitmap, srgb: Boolean = true): Texture {
        val levels = log2(max(bitmap.width, bitmap.height).toFloat()).toInt() + 1
        val texture = Texture.Builder()
            .width(bitmap.width)
            .height(bitmap.height)
            .levels(levels)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(if (srgb) Texture.InternalFormat.SRGB8_A8 else Texture.InternalFormat.RGBA8)
            .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
            .build(engine)
        TextureHelper.setBitmap(engine, texture, 0, bitmap)
        texture.generateMipmaps(engine)
        return texture
    }

    /** Binds [texture] to a sampler parameter with trilinear filtering. */
    fun bind(instance: MaterialInstance, parameter: String, texture: Texture) {
        val sampler = TextureSampler(
            TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.CLAMP_TO_EDGE,
        )
        instance.setParameter(parameter, texture, sampler)
    }
}
