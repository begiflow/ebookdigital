package com.leaf.data.texture

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Ktx1Test {

    @Test
    fun `write then read roundtrips every level`() {
        val pixels = IntArray(8 * 4) { it * 0x01010101 }
        val levels = MipChain.build(pixels, 8, 4)
        val image = assertNotNull(Ktx1.read(Ktx1.write(levels)))
        assertEquals(8, image.width)
        assertEquals(4, image.height)
        assertEquals(levels.size, image.levels.size)
        for (i in levels.indices) {
            assertContentEquals(MipChain.toRgbaBytes(levels[i]), image.levels[i])
        }
    }

    @Test
    fun `rejects corrupt and truncated containers`() {
        val good = Ktx1.write(MipChain.build(IntArray(4 * 4), 4, 4))
        assertNull(Ktx1.read(good.copyOfRange(0, 40)), "truncated header")
        assertNull(Ktx1.read(good.copyOfRange(0, good.size - 8)), "truncated payload")
        val badMagic = good.copyOf()
        badMagic[1] = 'X'.code.toByte()
        assertNull(Ktx1.read(badMagic), "bad identifier")
    }
}
