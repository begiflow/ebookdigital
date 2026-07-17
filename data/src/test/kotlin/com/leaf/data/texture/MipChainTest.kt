package com.leaf.data.texture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MipChainTest {

    @Test
    fun `chain runs down to 1x1 with halving dimensions`() {
        val levels = MipChain.build(IntArray(64 * 32), 64, 32)
        assertEquals(7, levels.size) // 64x32 .. 1x1
        assertEquals(64 to 32, levels[0].width to levels[0].height)
        assertEquals(32 to 16, levels[1].width to levels[1].height)
        assertEquals(1 to 1, levels.last().width to levels.last().height)
    }

    @Test
    fun `box filter averages the four source pixels`() {
        val src = intArrayOf(
            argb(255, 100, 0, 0), argb(255, 200, 0, 0),
            argb(255, 0, 50, 0), argb(255, 0, 150, 0),
        )
        val half = MipChain.halve(MipChain.Level(2, 2, src))
        assertEquals(1, half.width)
        val p = half.pixels[0]
        assertEquals(255, p ushr 24 and 0xFF)
        assertEquals(75, p ushr 16 and 0xFF) // (100+200+0+0+2)/4
        assertEquals(50, p ushr 8 and 0xFF) // (0+0+50+150+2)/4
    }

    @Test
    fun `non power of two sizes clamp instead of crashing`() {
        val levels = MipChain.build(IntArray(5 * 3), 5, 3)
        assertEquals(5 to 3, levels[0].width to levels[0].height)
        assertEquals(2 to 1, levels[1].width to levels[1].height)
        assertEquals(1 to 1, levels[2].width to levels[2].height)
    }

    @Test
    fun `flat color survives the whole chain exactly`() {
        val color = argb(255, 123, 45, 67)
        val levels = MipChain.build(IntArray(16 * 16) { color }, 16, 16)
        assertTrue(levels.all { level -> level.pixels.all { it == color } })
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int) =
        (a shl 24) or (r shl 16) or (g shl 8) or b
}
