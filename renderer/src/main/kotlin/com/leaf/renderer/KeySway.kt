package com.leaf.renderer

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Key-light sway (M9, docs/04-GRAPHICS-PIPELINE.md §3): the key direction
 * leans up to ~1° with device tilt so the paper grain shimmers as the reader
 * moves — the light position stays fixed, only micro-response changes.
 *
 * The sway is a *high-pass* on tilt: a slow-tracking neutral pose follows the
 * device, and only the deviation from it sways the light. Holding any pose
 * eventually reads as rest (no permanently saturated offset); tilting the
 * device produces an immediate, smoothed shimmer that settles back.
 *
 * Pure Kotlin: gravity samples in, normalized light direction out.
 */
class KeySway(
    baseX: Float,
    baseY: Float,
    baseZ: Float,
    private val maxAngleRad: Float = 0.02f,
    private val gain: Float = 0.5f,
    /** Neutral-pose tracking time constant (s): how fast a held pose becomes rest. */
    private val neutralTau: Float = 2.5f,
    /** Output smoothing time constant (s). */
    private val smoothTau: Float = 0.08f,
) {
    private val base = normalized(baseX, baseY, baseZ)

    // Orthonormal basis perpendicular to the base direction.
    private val e1: FloatArray
    private val e2: FloatArray

    private var pitch = 0f
    private var roll = 0f
    private var neutralPitch = 0f
    private var neutralRoll = 0f
    private var hasSample = false

    private var offset1 = 0f
    private var offset2 = 0f

    /** Last computed direction; valid after the first [update]. */
    val direction = base.copyOf()

    init {
        // Helper axis least aligned with base avoids degenerate crosses.
        val helper = if (abs(base[1]) < 0.9f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
        e1 = normalized(
            base[1] * helper[2] - base[2] * helper[1],
            base[2] * helper[0] - base[0] * helper[2],
            base[0] * helper[1] - base[1] * helper[0],
        )
        e2 = floatArrayOf(
            base[1] * e1[2] - base[2] * e1[1],
            base[2] * e1[0] - base[0] * e1[2],
            base[0] * e1[1] - base[1] * e1[0],
        )
    }

    /** Feeds a gravity-vector sample in device coordinates (any magnitude). */
    fun setTilt(gx: Float, gy: Float, gz: Float) {
        val len = sqrt(gx * gx + gy * gy + gz * gz)
        if (len < 1e-6f) return
        pitch = atan2(gy, gz)
        roll = atan2(gx, gz)
        if (!hasSample) {
            // First sample defines rest — no startup lurch.
            neutralPitch = pitch
            neutralRoll = roll
            hasSample = true
        }
    }

    /** Advances the filters and returns the swayed, normalized direction. */
    fun update(dt: Float): FloatArray {
        val neutralAlpha = 1f - exp(-dt / neutralTau)
        neutralPitch += (pitch - neutralPitch) * neutralAlpha
        neutralRoll += (roll - neutralRoll) * neutralAlpha

        val target1 = (gain * (pitch - neutralPitch)).coerceIn(-maxAngleRad, maxAngleRad)
        val target2 = (gain * (roll - neutralRoll)).coerceIn(-maxAngleRad, maxAngleRad)

        val smoothAlpha = 1f - exp(-dt / smoothTau)
        offset1 += (target1 - offset1) * smoothAlpha
        offset2 += (target2 - offset2) * smoothAlpha

        // Small angles: offset in radians ≈ perpendicular displacement.
        val x = base[0] + offset1 * e1[0] + offset2 * e2[0]
        val y = base[1] + offset1 * e1[1] + offset2 * e2[1]
        val z = base[2] + offset1 * e1[2] + offset2 * e2[2]
        val inv = 1f / sqrt(x * x + y * y + z * z)
        direction[0] = x * inv
        direction[1] = y * inv
        direction[2] = z * inv
        return direction
    }

    private fun normalized(x: Float, y: Float, z: Float): FloatArray {
        val inv = 1f / sqrt(x * x + y * y + z * z)
        return floatArrayOf(x * inv, y * inv, z * inv)
    }
}
