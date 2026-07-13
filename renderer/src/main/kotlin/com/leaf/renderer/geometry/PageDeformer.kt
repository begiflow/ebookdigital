package com.leaf.renderer.geometry

import com.leaf.physics.PageStrip
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * M7 extrusion (docs/05-PHYSICS.md §4): expands the 1D strip into per-row
 * column curves with the corner-grab skew, and produces the analytic surface
 * derivatives the renderer turns into normals.
 *
 * Skew model: render row r gets a weight w(r) — 1 at the grabbed row falling
 * linearly to λ(stiffness) at the row farthest from it. A row's curve is
 * rebuilt by re-integrating the base curve's chords with their angles blended
 * toward the rest direction by w: unit chord lengths are untouched, so
 * inextensibility survives the skew exactly. A stiff page (λ→1) lifts as a
 * plate; floppy paper lets the grabbed corner lead.
 *
 * Pure Kotlin — no Filament types — so the whole deformation is JVM-testable.
 */
class PageDeformer(val cols: Int, val rows: Int) {

    // Base curve (spline through the strip, sampled per column).
    private val baseX = FloatArray(cols)
    private val baseZ = FloatArray(cols)
    private val baseTangentAngle = FloatArray(cols)

    // Chord decomposition of the base curve.
    private val chordLen = FloatArray(cols - 1)
    private val chordAngle = FloatArray(cols - 1)

    /** Outputs, row-major (row * cols + col), in 2D spread space. */
    val posX = FloatArray(cols * rows)
    val posZ = FloatArray(cols * rows)

    /** Unit column tangent per vertex (the curl direction). */
    val tanX = FloatArray(cols * rows)
    val tanZ = FloatArray(cols * rows)

    /** Surface derivative w.r.t. the row fraction v in 0..1 (for normals). */
    val dPosXdv = FloatArray(cols * rows)
    val dPosZdv = FloatArray(cols * rows)

    /**
     * [grabV] row fraction (0..1) of the grab point; [skew] the effective skew
     * amount in 0..1 — BookScene ramps it while grabbed and fades it after
     * release ((1-λ) is already folded in by the caller); [restAngle] the
     * direction (radians) of the page's undeformed rest pose, 0 for a page
     * lying to the right, ~±π for one lying on the opened cover.
     */
    fun deform(strip: PageStrip, grabV: Float, skew: Float, restAngle: Float) {
        sampleSpline(strip)

        for (c in 0 until cols - 1) {
            val dx = baseX[c + 1] - baseX[c]
            val dz = baseZ[c + 1] - baseZ[c]
            chordLen[c] = sqrt(dx * dx + dz * dz)
            chordAngle[c] = if (chordLen[c] > 1e-12f) atan2(dz, dx) else restAngle
        }

        val maxDist = maxOf(grabV, 1f - grabV, 1e-4f)
        for (r in 0 until rows) {
            val v = r / (rows - 1f)
            val dist = kotlin.math.abs(v - grabV) / maxDist
            val w = 1f - skew * dist
            // d(w)/d(v), constant per row; zero exactly at the grab row.
            val dwdv = when {
                skew == 0f || v == grabV -> 0f
                v > grabV -> -skew / maxDist
                else -> skew / maxDist
            }
            integrateRow(r, w, dwdv, restAngle)
        }
    }

    private fun integrateRow(r: Int, w: Float, dwdv: Float, restAngle: Float) {
        val row = r * cols
        var x = baseX[0]
        var z = baseZ[0]
        // Accumulated dP/dw along the row (the skew shear), scaled by dw/dv
        // at each vertex to yield the analytic row derivative.
        var accDx = 0f
        var accDz = 0f

        posX[row] = x
        posZ[row] = z
        dPosXdv[row] = 0f
        dPosZdv[row] = 0f
        writeTangent(row, restAngle + w * wrap(baseTangentAngle[0] - restAngle))

        for (c in 0 until cols - 1) {
            val delta = wrap(chordAngle[c] - restAngle)
            val theta = restAngle + w * delta
            val ct = cos(theta)
            val st = sin(theta)
            x += chordLen[c] * ct
            z += chordLen[c] * st
            // d/dw of this chord's contribution: L * delta * (-sin, cos).
            accDx += chordLen[c] * delta * -st
            accDz += chordLen[c] * delta * ct

            val i = row + c + 1
            posX[i] = x
            posZ[i] = z
            dPosXdv[i] = accDx * dwdv
            dPosZdv[i] = accDz * dwdv
            writeTangent(i, restAngle + w * wrap(baseTangentAngle[c + 1] - restAngle))
        }
    }

    private fun writeTangent(i: Int, angle: Float) {
        tanX[i] = cos(angle)
        tanZ[i] = sin(angle)
    }

    /** Wraps an angle difference into (-π, π]. */
    private fun wrap(a: Float): Float {
        var d = a
        while (d > PI.toFloat()) d -= TWO_PI
        while (d <= -PI.toFloat()) d += TWO_PI
        return d
    }

    /**
     * Catmull-Rom through the strip particles, sampled uniformly in particle
     * index — segments are constraint-equal, so index space IS arc length.
     */
    private fun sampleSpline(strip: PageStrip) {
        val n = strip.n
        for (c in 0 until cols) {
            val s = c.toFloat() / (cols - 1) * (n - 1)
            val j = s.toInt().coerceAtMost(n - 2)
            val t = s - j

            val x0 = strip.px[maxOf(j - 1, 0)]; val z0 = strip.pz[maxOf(j - 1, 0)]
            val x1 = strip.px[j]; val z1 = strip.pz[j]
            val x2 = strip.px[j + 1]; val z2 = strip.pz[j + 1]
            val x3 = strip.px[minOf(j + 2, n - 1)]; val z3 = strip.pz[minOf(j + 2, n - 1)]

            val t2 = t * t
            val t3 = t2 * t
            baseX[c] = 0.5f * (
                2f * x1 + (x2 - x0) * t +
                    (2f * x0 - 5f * x1 + 4f * x2 - x3) * t2 +
                    (3f * x1 - x0 - 3f * x2 + x3) * t3
                )
            baseZ[c] = 0.5f * (
                2f * z1 + (z2 - z0) * t +
                    (2f * z0 - 5f * z1 + 4f * z2 - z3) * t2 +
                    (3f * z1 - z0 - 3f * z2 + z3) * t3
                )
            val dx = 0.5f * (
                (x2 - x0) + 2f * (2f * x0 - 5f * x1 + 4f * x2 - x3) * t +
                    3f * (3f * x1 - x0 - 3f * x2 + x3) * t2
                )
            val dz = 0.5f * (
                (z2 - z0) + 2f * (2f * z0 - 5f * z1 + 4f * z2 - z3) * t +
                    3f * (3f * z1 - z0 - 3f * z2 + z3) * t2
                )
            baseTangentAngle[c] = if (dx * dx + dz * dz > 1e-18f) atan2(dz, dx) else 0f
        }
    }

    private companion object {
        const val TWO_PI = (2.0 * PI).toFloat()
    }
}
