package com.leaf.renderer

/**
 * The degradation ladder (docs/02-ARCHITECTURE.md §7): when a device can't
 * hold its refresh rate, presentation quality steps down one rung at a time —
 * shadow resolution first, then flight-mesh density, then a 60 Hz render cap.
 * Physics feel never degrades (the sim keeps its fixed 120 Hz step).
 */
enum class DegradationRung {
    FULL,
    REDUCED_SHADOWS,
    REDUCED_MESH,
    CAPPED_60,
}

/**
 * Frame-time monitor driving [DegradationRung] decisions. Feed it the
 * Choreographer callback interval every frame:
 *
 * - The display period is learned as the smallest interval seen (floored):
 *   an interval well above it is a missed vsync.
 * - A window with too many misses steps DOWN one rung.
 * - Several consecutive clean windows step back UP.
 * - A cooldown after every change prevents flapping.
 *
 * Pure Kotlin and deterministic — the ladder's hysteresis is JVM-tested,
 * the rungs' visual meaning lives in the scene.
 */
class DegradationLadder(
    private val windowFrames: Int = 120,
    private val missRatioDown: Float = 0.15f,
    private val missRatioUp: Float = 0.02f,
    private val cooldownFrames: Int = 360,
    private val cleanWindowsToStepUp: Int = 4,
) {
    var rung: DegradationRung = DegradationRung.FULL
        private set

    private var periodEstimate = Float.MAX_VALUE
    private var frames = 0
    private var misses = 0
    private var cleanWindows = 0
    private var framesSinceChange = cooldownFrames

    /** Feeds one frame interval; returns the (possibly new) rung. */
    fun feed(dtSeconds: Float): DegradationRung {
        if (dtSeconds <= 0f) return rung
        if (dtSeconds < periodEstimate && dtSeconds > MIN_PERIOD) periodEstimate = dtSeconds
        if (periodEstimate == Float.MAX_VALUE) return rung

        framesSinceChange++
        frames++
        if (dtSeconds > periodEstimate * MISS_FACTOR) misses++
        if (frames < windowFrames) return rung

        val ratio = misses.toFloat() / frames
        frames = 0
        misses = 0

        if (ratio > missRatioDown) {
            cleanWindows = 0
            if (framesSinceChange >= cooldownFrames && rung.ordinal < DegradationRung.CAPPED_60.ordinal) {
                rung = DegradationRung.entries[rung.ordinal + 1]
                framesSinceChange = 0
            }
        } else if (ratio <= missRatioUp) {
            cleanWindows++
            if (cleanWindows >= cleanWindowsToStepUp &&
                framesSinceChange >= cooldownFrames &&
                rung.ordinal > 0
            ) {
                rung = DegradationRung.entries[rung.ordinal - 1]
                framesSinceChange = 0
                cleanWindows = 0
            }
        } else {
            cleanWindows = 0
        }
        return rung
    }

    private companion object {
        /** Ignore intervals faster than 150 Hz when learning the period. */
        const val MIN_PERIOD = 1f / 150f

        /** An interval beyond 1.6× the display period is a missed vsync. */
        const val MISS_FACTOR = 1.6f
    }
}
