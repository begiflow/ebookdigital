package com.leaf.physics

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The paper simulation (docs/05-PHYSICS.md): a 1D strip of inextensible
 * segments across the page width, solved with XPBD. Particle 0 is the spine
 * pivot; the free edge is particle n-1. The strip lives in 2D "spread space":
 * x along the open book (negative = left/turned side, 0 = spine), z up off
 * the paper stacks. Extrusion to a 3D mesh happens in the renderer.
 *
 * Deterministic: fixed-dt stepping, no randomness, pure float math.
 */
class PageStrip(val params: PageParams) {

    enum class Settle { IN_FLIGHT, SETTLED_LEFT, SETTLED_RIGHT }

    val n = params.particleCount
    private val seg = params.widthMeters / (n - 1)

    /** Particle positions, exposed read-only for extrusion. */
    val px = FloatArray(n)
    val pz = FloatArray(n)

    private val prevX = FloatArray(n)
    private val prevZ = FloatArray(n)
    private val vx = FloatArray(n)
    private val vz = FloatArray(n)

    // Collision surfaces: stacks on both sides of the spine. The left side
    // (opened cover) is tilted by [leftSlope] (docs: cover rest != flat).
    private var zLeft = 0f
    private var zRight = 0f
    private var leftSlope = 0f

    private var grabbedIndex = -1
    private var targetX = 0f
    private var targetZ = 0f

    /** -1 = bias toward left, +1 = toward right, decided at release. */
    private var bias = 0f

    var settle: Settle = Settle.IN_FLIGHT
        private set

    val isGrabbed: Boolean get() = grabbedIndex >= 0

    fun setSurfaces(left: Float, right: Float, slopeLeft: Float) {
        zLeft = left
        zRight = right
        leftSlope = slopeLeft
    }

    /** Height of the stack surface under sim-space x. */
    fun surface(x: Float): Float =
        if (x < 0f) zLeft + leftSlope * -x else zRight

    /** Lays the strip flat on one side with the resting bow near the spine. */
    fun resetFlat(onRight: Boolean, bowHeight: Float, bowExtent: Float = 0.45f) {
        for (i in 0 until n) {
            val s = i / (n - 1f)
            val x = if (onRight) s * params.widthMeters else -s * params.widthMeters
            val bow = if (s < bowExtent) {
                bowHeight * kotlin.math.sin((s / bowExtent) * Math.PI).toFloat()
            } else {
                0f
            }
            px[i] = x
            pz[i] = surface(x) + bow + REST_EPS
            vx[i] = 0f
            vz[i] = 0f
        }
        pz[0] = PIVOT_Z
        px[0] = 0f
        grabbedIndex = -1
        bias = 0f
        settle = Settle.IN_FLIGHT
    }

    /** Grabs the material point at fraction [u] of the width from the spine. */
    fun grab(u: Float) {
        grabbedIndex = (u * (n - 1)).toInt().coerceIn(MIN_GRAB_INDEX, n - 1)
        settle = Settle.IN_FLIGHT
    }

    /**
     * Attachment target in spread space — the finger-ray pierce point. The
     * solver keeps the grabbed material point on it when reachable; when the
     * finger is closer to the spine than the paper allows, inextensibility
     * makes the page buckle upward. That IS the grab-under-finger invariant.
     */
    fun drag(x: Float, z: Float) {
        if (grabbedIndex < 0) return
        targetX = x
        targetZ = max(z, surface(x) + MIN_DRAG_LIFT)
    }

    /**
     * [directionHint] from flick velocity: -1 finish left, +1 fall back
     * right, 0 = decide from momentum + lean (docs/05 §3): if the outer
     * third of the page carries clear sideways speed, it wins; otherwise
     * the free edge's lean does.
     */
    fun release(directionHint: Int) {
        if (grabbedIndex < 0) return
        grabbedIndex = -1
        bias = when {
            directionHint != 0 -> directionHint.toFloat()
            else -> {
                var meanVx = 0f
                val outer = n - n / 3
                for (i in outer until n) meanVx += vx[i]
                meanVx /= (n - outer)
                when {
                    meanVx < -RELEASE_MOMENTUM -> -1f
                    meanVx > RELEASE_MOMENTUM -> 1f
                    else -> if (px[n - 1] < 0f) -1f else 1f
                }
            }
        }
    }

    /**
     * Kicks the free edge (riffle: the thumb flicks the page without holding
     * it). Velocity ramps from the pivot to the tip like a real edge strike.
     */
    fun fling(speedX: Float, lift: Float) {
        for (i in 1 until n) {
            val s = i / (n - 1f)
            vx[i] += speedX * s
            vz[i] += lift * s
        }
    }

    fun step(dt: Float) {
        val h = dt / SUBSTEPS
        repeat(SUBSTEPS) { substep(h) }
        updateSettle()
    }

    private fun substep(h: Float) {
        val dampingFactor = exp(-params.damping * h)
        for (i in 1 until n) {
            vz[i] += params.gravity * h
            // Turn-completion torque acts on airborne paper only: once a
            // particle rests on the stack the bias lets go, so a landed page
            // stops being pushed sideways and can actually settle (M8).
            if (grabbedIndex < 0 && settle == Settle.IN_FLIGHT &&
                pz[i] - surface(px[i]) > BIAS_LIFT_EPS
            ) {
                vx[i] += bias * BIAS_ACCEL * h
            }
            // Air drag ∝ -v·|v| (docs/05 §2), integrated implicitly so it can
            // never reverse a velocity: v/(1 + k|v|h). This is the flutter
            // brake — a released page slows into its fall instead of whipping.
            val speed = sqrt(vx[i] * vx[i] + vz[i] * vz[i])
            val drag = 1f / (1f + params.airDrag * speed * h)
            vx[i] *= dampingFactor * drag
            vz[i] *= dampingFactor * drag
            prevX[i] = px[i]
            prevZ[i] = pz[i]
            px[i] += vx[i] * h
            pz[i] += vz[i] * h
        }

        val bendStiffness = BEND_MIN + (BEND_MAX - BEND_MIN) * params.stiffness
        repeat(ITERATIONS) {
            // Attachment first; the distance chain then arbitrates.
            if (grabbedIndex > 0) {
                val g = grabbedIndex
                px[g] += (targetX - px[g]) * ATTACH_K
                pz[g] += (targetZ - pz[g]) * ATTACH_K
            }
            // Inextensibility (hard).
            for (i in 0 until n - 1) {
                val dx = px[i + 1] - px[i]
                val dz = pz[i + 1] - pz[i]
                val len = sqrt(dx * dx + dz * dz)
                if (len < 1e-9f) continue
                val diff = (len - seg) / len
                if (i == 0) {
                    // Pivot immovable: correction lands on the outer particle.
                    px[1] -= dx * diff
                    pz[1] -= dz * diff
                } else {
                    px[i] += dx * diff * 0.5f
                    pz[i] += dz * diff * 0.5f
                    px[i + 1] -= dx * diff * 0.5f
                    pz[i + 1] -= dz * diff * 0.5f
                }
            }
            // Bending: second-neighbor distance toward straight.
            for (i in 0 until n - 2) {
                val dx = px[i + 2] - px[i]
                val dz = pz[i + 2] - pz[i]
                val len = sqrt(dx * dx + dz * dz)
                if (len < 1e-9f) continue
                val rest = 2f * seg
                val diff = (len - rest) / len * bendStiffness
                if (i == 0) {
                    px[2] -= dx * diff
                    pz[2] -= dz * diff
                } else {
                    px[i] += dx * diff * 0.5f
                    pz[i] += dz * diff * 0.5f
                    px[i + 2] -= dx * diff * 0.5f
                    pz[i + 2] -= dz * diff * 0.5f
                }
            }
            // Stack collision (projection).
            for (i in 1 until n) {
                val floor = surface(px[i])
                if (pz[i] < floor) pz[i] = floor
            }
        }

        // Final hard passes: whatever the attachment/bending tug-of-war left
        // behind, the strip exits each substep inextensible (docs/05 §6).
        repeat(FINAL_DISTANCE_PASSES) {
            for (i in 0 until n - 1) {
                val dx = px[i + 1] - px[i]
                val dz = pz[i + 1] - pz[i]
                val len = sqrt(dx * dx + dz * dz)
                if (len < 1e-9f) continue
                val diff = (len - seg) / len
                if (i == 0) {
                    px[1] -= dx * diff
                    pz[1] -= dz * diff
                } else {
                    px[i] += dx * diff * 0.5f
                    pz[i] += dz * diff * 0.5f
                    px[i + 1] -= dx * diff * 0.5f
                    pz[i + 1] -= dz * diff * 0.5f
                }
            }
        }

        // Closing collision clamp (the distance passes may have dipped a
        // particle below the stack again; the up-only nudge is sub-segment).
        for (i in 1 until n) {
            val floor = surface(px[i])
            if (pz[i] < floor) pz[i] = floor
        }

        for (i in 1 until n) {
            vx[i] = (px[i] - prevX[i]) / h
            vz[i] = (pz[i] - prevZ[i]) / h
        }
    }

    private fun updateSettle() {
        if (grabbedIndex >= 0) {
            settle = Settle.IN_FLIGHT
            return
        }
        var maxSpeedSq = 0f
        var maxLift = 0f
        for (i in 1 until n) {
            val sq = vx[i] * vx[i] + vz[i] * vz[i]
            if (sq > maxSpeedSq) maxSpeedSq = sq
            val lift = pz[i] - surface(px[i])
            if (lift > maxLift) maxLift = lift
        }
        settle = when {
            maxSpeedSq > SETTLE_SPEED * SETTLE_SPEED -> Settle.IN_FLIGHT
            maxLift > SETTLE_LIFT -> Settle.IN_FLIGHT
            px[n - 1] < 0f -> Settle.SETTLED_LEFT
            else -> Settle.SETTLED_RIGHT
        }
    }

    private companion object {
        const val SUBSTEPS = 2
        const val ITERATIONS = 8
        const val FINAL_DISTANCE_PASSES = 3
        const val ATTACH_K = 0.55f
        const val BEND_MIN = 0.04f
        const val BEND_MAX = 0.55f
        const val BIAS_ACCEL = 26f
        const val SETTLE_SPEED = 0.03f
        const val SETTLE_LIFT = 0.006f
        const val RELEASE_MOMENTUM = 0.18f
        const val BIAS_LIFT_EPS = 0.0015f
        const val MIN_DRAG_LIFT = 0.002f
        const val REST_EPS = 0.0002f
        const val PIVOT_Z = 0.0008f
        const val MIN_GRAB_INDEX = 2
    }
}

/** Physical page parameters, mapped from the notebook profile. */
class PageParams(
    val widthMeters: Float,
    val particleCount: Int = 16,
    /** 0 = floppy diary paper, 1 = passport card stock. */
    val stiffness: Float = 0.5f,
    /** Linear velocity damping (internal friction), 1/s. */
    val damping: Float = 2.8f,
    /** Quadratic air drag coefficient, 1/m — the flutter-settle knob. */
    val airDrag: Float = 12f,
    val gravity: Float = -9.81f,
)
