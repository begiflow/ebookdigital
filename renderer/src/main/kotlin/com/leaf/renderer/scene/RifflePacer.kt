package com.leaf.renderer.scene

/**
 * Riffle cadence (M8, docs/03-RENDERER.md §3): dragging along the fore-edge
 * releases pages at a rate driven by finger speed. Accumulator-based so the
 * cadence is smooth at any frame rate and deterministic for tests.
 */
class RifflePacer {

    private var accumulator = 0f

    /**
     * Advances by [dt] at finger [speed] (m/s, absolute) and returns how many
     * pages to release this step. Below [MIN_SPEED] the accumulator drains —
     * a resting finger stops the riffle instead of banking pages.
     */
    fun step(dt: Float, speed: Float): Int {
        if (speed < MIN_SPEED) {
            accumulator = (accumulator - DRAIN_RATE * dt).coerceAtLeast(0f)
            return 0
        }
        val rate = (speed * PAGES_PER_METER).coerceIn(MIN_RATE, MAX_RATE)
        accumulator += rate * dt
        val pages = accumulator.toInt()
        accumulator -= pages
        return pages
    }

    fun reset() {
        accumulator = 0f
    }

    private companion object {
        /** Finger speed below this stops feeding the riffle (m/s). */
        const val MIN_SPEED = 0.08f

        /** Cadence scale: how many pages one meter of finger travel is worth. */
        const val PAGES_PER_METER = 55f

        const val MIN_RATE = 4f // pages/s once riffling at all
        const val MAX_RATE = 26f // thumb-riffle ceiling; pool still caps bursts
        const val DRAIN_RATE = 8f
    }
}
