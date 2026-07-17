package com.leaf.camera.scan

/**
 * Auto-capture decision (docs/01-PRD.md §5.4): the shutter fires itself when
 * the detected quad holds still. Feed one detection per analysis frame —
 * [feed] returns true exactly once per hold, then a cooldown keeps the
 * camera from re-firing until the page actually changes (or the user moves).
 *
 * Also owns the smoothed overlay quad: raw detections jitter, the overlay
 * follows an exponential blend so the edge highlight feels glued to paper.
 */
class QuadStabilityTracker(
    private val requiredStableFrames: Int = 12,
    private val cornerTolerance: Float = 0.012f,
    private val cooldownFrames: Int = 45,
    private val smoothing: Float = 0.45f,
) {
    /** Smoothed quad for the viewfinder overlay; null = nothing detected. */
    var overlay: Quad? = null
        private set

    /** True while the current hold is long enough to fire. */
    var isStable: Boolean = false
        private set

    private var last: Quad? = null
    private var stableFrames = 0
    private var cooldown = 0

    /** Returns true when this frame should auto-capture. */
    fun feed(quad: Quad?): Boolean {
        if (cooldown > 0) cooldown--

        if (quad == null) {
            overlay = null
            last = null
            stableFrames = 0
            isStable = false
            return false
        }

        overlay = overlay?.lerpToward(quad, smoothing) ?: quad

        val previous = last
        last = quad
        stableFrames = if (previous != null && quad.maxCornerDistance(previous) < cornerTolerance) {
            stableFrames + 1
        } else {
            0
        }
        isStable = stableFrames >= requiredStableFrames

        if (isStable && cooldown == 0) {
            onCaptured()
            return true
        }
        return false
    }

    /** Arms the cooldown — call for manual shutters too. */
    fun onCaptured() {
        cooldown = cooldownFrames
        stableFrames = 0
        isStable = false
    }

    fun reset() {
        overlay = null
        last = null
        stableFrames = 0
        cooldown = 0
        isStable = false
    }
}
