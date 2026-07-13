package com.leaf.physics

/**
 * Single-DOF hinge for rigid covers (docs/05-PHYSICS.md §5): an angular
 * spring-damper with a bistable free potential (closed at 0, open at
 * [openRestAngle]) and a detent near closed — resistance rises in the last
 * few degrees, then the cover snaps shut. That detent IS the "cover feel"
 * (docs/03-RENDERER.md §5).
 *
 * Angle in radians: 0 = closed, positive = opening. Step with a fixed dt
 * (120 Hz); dynamics are deterministic.
 */
class CoverHinge(
    val openRestAngle: Float = 2.95f,
    val maxAngle: Float = 3.10f,
) {
    var angle: Float = 0f
        private set
    var velocity: Float = 0f
        private set

    val isGrabbed: Boolean get() = grabbed

    /** True when resting at a rest pose (closed, or the open rest angle). */
    val isSettled: Boolean
        get() = !grabbed &&
            kotlin.math.abs(velocity) < SETTLE_VELOCITY &&
            (angle < SETTLE_DISTANCE || kotlin.math.abs(angle - openRestAngle) < SETTLE_DISTANCE)

    private var grabbed = false
    private var dragTarget = 0f

    fun grab() {
        grabbed = true
        dragTarget = angle
    }

    fun drag(targetAngle: Float) {
        if (!grabbed) return
        dragTarget = targetAngle.coerceIn(0f, maxAngle)
    }

    fun release() {
        grabbed = false
    }

    fun step(dt: Float) {
        val torque = if (grabbed) {
            // Stiff servo toward the finger: the cover must track the hand
            // without lag, but critically damped so it never oscillates.
            GRAB_STIFFNESS * (dragTarget - angle) - GRAB_DAMPING * velocity
        } else {
            freeTorque() - FREE_DAMPING * velocity
        }
        velocity += torque * dt
        angle += velocity * dt

        if (angle <= 0f) {
            angle = 0f
            if (velocity < 0f) velocity = 0f
        } else if (angle >= maxAngle) {
            angle = maxAngle
            if (velocity > 0f) velocity = 0f
        }
    }

    private fun freeTorque(): Float {
        return if (angle < DECISION_ANGLE) {
            // Falling closed. Inside the detent range the pull strengthens
            // sharply — the "snap" of a case-bound cover seating itself.
            var torque = -CLOSE_STIFFNESS * angle
            if (angle < DETENT_RANGE) {
                torque -= DETENT_STRENGTH * (DETENT_RANGE - angle) / DETENT_RANGE
            }
            torque
        } else {
            // Falling open toward the natural rest pose.
            OPEN_STIFFNESS * (openRestAngle - angle)
        }
    }

    private companion object {
        const val GRAB_STIFFNESS = 900f
        const val GRAB_DAMPING = 60f
        const val FREE_DAMPING = 4.5f
        const val CLOSE_STIFFNESS = 28f
        const val OPEN_STIFFNESS = 22f
        const val DECISION_ANGLE = 1.05f
        const val DETENT_RANGE = 0.16f
        const val DETENT_STRENGTH = 9f
        const val SETTLE_VELOCITY = 0.02f
        const val SETTLE_DISTANCE = 0.03f
    }
}
