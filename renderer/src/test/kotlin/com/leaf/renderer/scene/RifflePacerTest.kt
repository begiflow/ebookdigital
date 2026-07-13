package com.leaf.renderer.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RifflePacerTest {

    private val dt = 1f / 120f

    @Test
    fun `slow fingers release nothing`() {
        val pacer = RifflePacer()
        var pages = 0
        repeat(240) { pages += pacer.step(dt, speed = 0.05f) }
        assertEquals(0, pages)
    }

    @Test
    fun `cadence scales with speed and stays within bounds`() {
        fun pagesPerSecond(speed: Float): Float {
            val pacer = RifflePacer()
            var pages = 0
            repeat(360) { pages += pacer.step(dt, speed) }
            return pages / 3f
        }
        val slow = pagesPerSecond(0.12f)
        val fast = pagesPerSecond(0.45f)
        assertTrue(slow in 3f..10f, "slow cadence out of range: $slow")
        assertTrue(fast > slow, "cadence should grow with speed")
        assertTrue(pagesPerSecond(5f) <= 27f, "cadence must cap at the riffle ceiling")
    }

    @Test
    fun `stopping the finger drains banked pages instead of releasing them`() {
        val pacer = RifflePacer()
        // Almost bank a page, then stop.
        var pages = 0
        repeat(8) { pages += pacer.step(dt, speed = 0.3f) }
        repeat(60) { pacer.step(dt, speed = 0f) }
        // Resume from empty: the first release should take a while again.
        var releasedAt = -1
        for (i in 0 until 60) {
            if (pacer.step(dt, speed = 0.3f) > 0) {
                releasedAt = i
                break
            }
        }
        assertTrue(releasedAt > 2, "accumulator did not drain while stopped")
    }
}
