package com.leaf.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DegradationLadderTest {

    private val period = 1f / 120f
    private val miss = period * 2f

    /** Feeds [n] frames with [missEvery]-th one a vsync miss (0 = none). */
    private fun DegradationLadder.run(n: Int, missEvery: Int = 0): DegradationRung {
        var r = rung
        for (i in 1..n) {
            val dt = if (missEvery > 0 && i % missEvery == 0) miss else period
            r = feed(dt)
        }
        return r
    }

    @Test
    fun `starts at full quality and stays there on a healthy device`() {
        val ladder = DegradationLadder()
        assertEquals(DegradationRung.FULL, ladder.run(5_000))
    }

    @Test
    fun `sustained misses walk down one rung at a time to the floor`() {
        val ladder = DegradationLadder()
        ladder.run(200) // learn the period
        // 1 in 4 frames misses: well above the 15% threshold.
        val seen = ArrayList<DegradationRung>()
        var prev = ladder.rung
        repeat(10_000) {
            val r = ladder.feed(if (it % 4 == 0) miss else period)
            if (r != prev) {
                seen.add(r)
                prev = r
            }
        }
        assertEquals(
            listOf(
                DegradationRung.REDUCED_SHADOWS,
                DegradationRung.REDUCED_MESH,
                DegradationRung.CAPPED_60,
            ),
            seen,
            "must degrade stepwise, never skip rungs",
        )
        assertEquals(DegradationRung.CAPPED_60, ladder.rung) // floor holds
    }

    @Test
    fun `recovery steps back up after sustained clean windows`() {
        val ladder = DegradationLadder()
        ladder.run(200)
        ladder.run(3_000, missEvery = 4) // degrade at least one rung
        assertTrue(ladder.rung != DegradationRung.FULL)
        ladder.run(20_000) // long healthy stretch
        assertEquals(DegradationRung.FULL, ladder.rung)
    }

    @Test
    fun `cooldown prevents rung flapping`() {
        val ladder = DegradationLadder(cooldownFrames = 360)
        ladder.run(200)
        var changes = 0
        var prev = ladder.rung
        // Pathological workload: alternating bad and good windows.
        repeat(40) { window ->
            val bad = window % 2 == 0
            repeat(120) { i ->
                val r = ladder.feed(if (bad && i % 3 == 0) miss else period)
                if (r != prev) {
                    changes++
                    prev = r
                }
            }
        }
        // 4 800 frames / 360-frame cooldown bounds changes to ~13; flapping
        // every window would be 40.
        assertTrue(changes <= 14, "ladder flapped $changes times")
    }

    @Test
    fun `occasional single miss never degrades`() {
        val ladder = DegradationLadder()
        ladder.run(200)
        // One miss every ~2 seconds: ~0.4% ratio.
        assertEquals(DegradationRung.FULL, ladder.run(20_000, missEvery = 240))
    }
}
