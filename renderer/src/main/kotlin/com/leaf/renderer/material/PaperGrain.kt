package com.leaf.renderer.material

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Procedural paper-grain normal map: anisotropically blurred noise (laid
 * paper's horizontal fiber bias) converted to a tangent-space normal map.
 * Generated once at startup; per-GrainKind variants (woven, gloss) join in M7
 * (docs/04-GRAPHICS-PIPELINE.md §2.1).
 */
object PaperGrain {

    fun laidNormalMap(size: Int = 256, seed: Long = 7L, strength: Float = 1.6f): Bitmap {
        val random = Random(seed)
        var height = FloatArray(size * size) { random.nextFloat() }

        // Anisotropic smoothing: heavy along x (fiber direction), light along y.
        repeat(3) { height = blurX(height, size) }
        height = blurY(height, size)

        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                // Central differences with wrap (map must tile seamlessly).
                val hx = height[y * size + (x + 1) % size] - height[y * size + (x - 1 + size) % size]
                val hy = height[((y + 1) % size) * size + x] - height[((y - 1 + size) % size) * size + x]
                var nx = -hx * strength
                var ny = -hy * strength
                var nz = 1f
                val inv = 1f / sqrt(nx * nx + ny * ny + nz * nz)
                nx *= inv; ny *= inv; nz *= inv
                pixels[y * size + x] = Color.rgb(
                    ((nx * 0.5f + 0.5f) * 255f).toInt(),
                    ((ny * 0.5f + 0.5f) * 255f).toInt(),
                    ((nz * 0.5f + 0.5f) * 255f).toInt(),
                )
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun blurX(src: FloatArray, size: Int): FloatArray {
        val out = FloatArray(src.size)
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                var sum = 0f
                for (k in -2..2) sum += src[row + (x + k + size) % size]
                out[row + x] = sum / 5f
            }
        }
        return out
    }

    private fun blurY(src: FloatArray, size: Int): FloatArray {
        val out = FloatArray(src.size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                var sum = 0f
                for (k in -1..1) sum += src[((y + k + size) % size) * size + x]
                out[y * size + x] = sum / 3f
            }
        }
        return out
    }
}
