package com.leaf.sample

import com.leaf.renderer.PageTexture
import com.leaf.renderer.TextureDetail
import com.leaf.renderer.TextureProvider
import java.util.concurrent.Executors

/**
 * M13 streaming demo: generates page art off-thread and hands the engine a
 * precomputed RGBA mip chain — the same shape the real KTX pipeline
 * delivers. With 50 sheets (100 pages) this exercises the residency window,
 * bounded uploads, and eviction: the zero-pop-in exit criterion is judged
 * flipping this book.
 */
class SyntheticTextureProvider : TextureProvider {

    private val executor = Executors.newFixedThreadPool(2)

    override fun request(pageIndex: Int, detail: TextureDetail, deliver: (PageTexture?) -> Unit) {
        executor.execute {
            val size = if (detail == TextureDetail.FULL) 512 else 128
            val bitmap = PageArt.page(pageIndex, width = size, height = size * 726 / 512)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            // 2x2 box mips, level 0 first (matches the KTX derivative layout).
            val levels = ArrayList<ByteArray>()
            var level = Mip(bitmap.width, bitmap.height, pixels)
            levels.add(level.rgba())
            while (level.w > 1 || level.h > 1) {
                level = level.halve()
                levels.add(level.rgba())
            }
            deliver(PageTexture(bitmap.width, bitmap.height, levels))
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private class Mip(val w: Int, val h: Int, val px: IntArray) {
        fun halve(): Mip {
            val nw = maxOf(1, w / 2)
            val nh = maxOf(1, h / 2)
            val out = IntArray(nw * nh)
            for (y in 0 until nh) {
                for (x in 0 until nw) {
                    val sx = minOf(2 * x, w - 1)
                    val sy = minOf(2 * y, h - 1)
                    val sx1 = minOf(sx + 1, w - 1)
                    val sy1 = minOf(sy + 1, h - 1)
                    out[y * nw + x] = avg(px[sy * w + sx], px[sy * w + sx1], px[sy1 * w + sx], px[sy1 * w + sx1])
                }
            }
            return Mip(nw, nh, out)
        }

        fun rgba(): ByteArray {
            val out = ByteArray(w * h * 4)
            var i = 0
            for (p in px) {
                out[i++] = (p ushr 16 and 0xFF).toByte()
                out[i++] = (p ushr 8 and 0xFF).toByte()
                out[i++] = (p and 0xFF).toByte()
                out[i++] = (p ushr 24 and 0xFF).toByte()
            }
            return out
        }

        private fun avg(a: Int, b: Int, c: Int, d: Int): Int {
            fun ch(shift: Int) =
                ((a ushr shift and 0xFF) + (b ushr shift and 0xFF) + (c ushr shift and 0xFF) + (d ushr shift and 0xFF) + 2) / 4
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
