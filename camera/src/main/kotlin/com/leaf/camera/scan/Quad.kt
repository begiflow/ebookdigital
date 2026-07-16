package com.leaf.camera.scan

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * A detected document quad in normalized frame coordinates (0..1, origin
 * top-left). Corners are always stored in TL, TR, BR, BL order — detection
 * hands in unordered points and [ordered] canonicalizes them.
 */
class Quad private constructor(private val c: FloatArray) {

    val x0 get() = c[0]; val y0 get() = c[1] // TL
    val x1 get() = c[2]; val y1 get() = c[3] // TR
    val x2 get() = c[4]; val y2 get() = c[5] // BR
    val x3 get() = c[6]; val y3 get() = c[7] // BL

    fun corner(i: Int): Pair<Float, Float> = c[2 * i] to c[2 * i + 1]

    /** Shoelace area (normalized units²). */
    fun area(): Float {
        var s = 0f
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            s += c[2 * i] * c[2 * j + 1] - c[2 * j] * c[2 * i + 1]
        }
        return abs(s) / 2f
    }

    /** True when all cross products share a sign (no bow-tie). */
    fun isConvex(): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val k = (i + 2) % 4
            val cross = (c[2 * j] - c[2 * i]) * (c[2 * k + 1] - c[2 * j + 1]) -
                (c[2 * j + 1] - c[2 * i + 1]) * (c[2 * k] - c[2 * j])
            val s = if (cross > 0f) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /** Largest corner-to-corner distance to [other] — the stability metric. */
    fun maxCornerDistance(other: Quad): Float {
        var worst = 0f
        for (i in 0 until 8 step 2) {
            val dx = c[i] - other.c[i]
            val dy = c[i + 1] - other.c[i + 1]
            val d = sqrt(dx * dx + dy * dy)
            if (d > worst) worst = d
        }
        return worst
    }

    /** Per-corner exponential blend toward [target] (overlay smoothing). */
    fun lerpToward(target: Quad, alpha: Float): Quad {
        val out = FloatArray(8)
        for (i in 0 until 8) out[i] = c[i] + (target.c[i] - c[i]) * alpha
        return Quad(out)
    }

    fun corners(): FloatArray = c.copyOf()

    companion object {
        /**
         * Canonicalizes four unordered points: sorted counter/clockwise
         * around the centroid, then rotated so the corner nearest the
         * top-left leads, yielding TL, TR, BR, BL in screen coordinates.
         */
        fun ordered(points: List<Pair<Float, Float>>): Quad {
            require(points.size == 4) { "quad needs 4 corners" }
            val cx = points.sumOf { it.first.toDouble() }.toFloat() / 4f
            val cy = points.sumOf { it.second.toDouble() }.toFloat() / 4f
            // Screen y grows downward: ascending atan2 walks TL->TR->BR->BL.
            val sorted = points.sortedBy { atan2((it.second - cy), (it.first - cx)) }
            var lead = 0
            var best = Float.MAX_VALUE
            for (i in 0 until 4) {
                val d = sorted[i].first * sorted[i].first + sorted[i].second * sorted[i].second
                if (d < best) {
                    best = d
                    lead = i
                }
            }
            val out = FloatArray(8)
            for (i in 0 until 4) {
                val p = sorted[(lead + i) % 4]
                out[2 * i] = p.first
                out[2 * i + 1] = p.second
            }
            return Quad(out)
        }

        fun of(vararg xy: Float): Quad {
            require(xy.size == 8)
            return ordered(listOf(xy[0] to xy[1], xy[2] to xy[3], xy[4] to xy[5], xy[6] to xy[7]))
        }
    }
}
