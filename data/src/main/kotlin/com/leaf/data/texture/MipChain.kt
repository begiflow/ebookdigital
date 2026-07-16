package com.leaf.data.texture

/**
 * CPU mip chain (docs/02 §3 TexturePipeline): RGBA8888 pixels as IntArray
 * (ARGB int per pixel, Android Bitmap layout), each level a 2x2 box filter
 * of the previous. Pure Kotlin so the pipeline's resampling is JVM-tested;
 * the GPU only ever sees precomputed levels — no runtime mip generation on
 * the render thread.
 */
object MipChain {

    class Level(val width: Int, val height: Int, val pixels: IntArray)

    /** Full chain from [width]x[height] down to 1x1. */
    fun build(pixels: IntArray, width: Int, height: Int): List<Level> {
        require(pixels.size == width * height)
        val levels = ArrayList<Level>()
        var level = Level(width, height, pixels)
        levels.add(level)
        while (level.width > 1 || level.height > 1) {
            level = halve(level)
            levels.add(level)
        }
        return levels
    }

    /** One 2x2 box-filter step (odd dimensions clamp the last row/column). */
    fun halve(src: Level): Level {
        val w = maxOf(1, src.width / 2)
        val h = maxOf(1, src.height / 2)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val sy0 = minOf(2 * y, src.height - 1)
            val sy1 = minOf(2 * y + 1, src.height - 1)
            for (x in 0 until w) {
                val sx0 = minOf(2 * x, src.width - 1)
                val sx1 = minOf(2 * x + 1, src.width - 1)
                out[y * w + x] = average(
                    src.pixels[sy0 * src.width + sx0],
                    src.pixels[sy0 * src.width + sx1],
                    src.pixels[sy1 * src.width + sx0],
                    src.pixels[sy1 * src.width + sx1],
                )
            }
        }
        return Level(w, h, out)
    }

    private fun average(a: Int, b: Int, c: Int, d: Int): Int {
        val alpha = ((a ushr 24 and 0xFF) + (b ushr 24 and 0xFF) + (c ushr 24 and 0xFF) + (d ushr 24 and 0xFF) + 2) / 4
        val red = ((a ushr 16 and 0xFF) + (b ushr 16 and 0xFF) + (c ushr 16 and 0xFF) + (d ushr 16 and 0xFF) + 2) / 4
        val green = ((a ushr 8 and 0xFF) + (b ushr 8 and 0xFF) + (c ushr 8 and 0xFF) + (d ushr 8 and 0xFF) + 2) / 4
        val blue = ((a and 0xFF) + (b and 0xFF) + (c and 0xFF) + (d and 0xFF) + 2) / 4
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    /** RGBA byte layout (GL order) for one level — what the container stores. */
    fun toRgbaBytes(level: Level): ByteArray {
        val out = ByteArray(level.width * level.height * 4)
        var i = 0
        for (p in level.pixels) {
            out[i++] = (p ushr 16 and 0xFF).toByte() // R
            out[i++] = (p ushr 8 and 0xFF).toByte() // G
            out[i++] = (p and 0xFF).toByte() // B
            out[i++] = (p ushr 24 and 0xFF).toByte() // A
        }
        return out
    }
}
