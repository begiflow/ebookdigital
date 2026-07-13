package com.leaf.renderer.scene

import com.google.android.filament.MaterialInstance
import com.leaf.filament.DynamicMesh
import com.leaf.filament.FilamentHost
import com.leaf.filament.TangentFrames
import com.leaf.physics.PageStrip
import kotlin.math.sqrt

/**
 * The in-flight sheet: the PBD strip extruded into two per-frame-streamed
 * meshes — front face (sheet front texture) and back face (sheet back
 * texture), coincident geometry with opposite winding so backface culling
 * shows exactly one per view side (docs/03-RENDERER.md §2, 05 §4).
 *
 * M6 extrusion is a pure ruled surface (uniform across the height); the
 * corner-grab skew term arrives in M7.
 */
class FlightPage(
    host: FilamentHost,
    frontInstance: MaterialInstance,
    backInstance: MaterialInstance,
) {
    private val front = DynamicMesh(host.engine, COLS, ROWS, frontInstance)
    private val back = DynamicMesh(host.engine, COLS, ROWS, backInstance, flipWinding = true)

    val frontEntity: Int get() = front.entity
    val backEntity: Int get() = back.entity

    // Spline sample cache (positions + tangents per column).
    private val colX = FloatArray(COLS)
    private val colZ = FloatArray(COLS)
    private val colTx = FloatArray(COLS)
    private val colTz = FloatArray(COLS)

    init {
        // Back face: mirrored u (its texture reads as a left page).
        val uvs = FloatArray(COLS * ROWS * 2)
        var i = 0
        for (r in 0 until ROWS) {
            val v = r / (ROWS - 1f)
            for (c in 0 until COLS) {
                uvs[i++] = 1f - c / (COLS - 1f)
                uvs[i++] = v
            }
        }
        back.setUvs(uvs)
    }

    /** Re-extrudes both meshes from the strip's current state. */
    fun update(strip: PageStrip, spineX: Float, baseZ: Float, pageHeight: Float) {
        sampleSpline(strip)

        front.update { pos, tan -> fill(pos, tan, spineX, baseZ, pageHeight, frontFace = true) }
        back.update { pos, tan -> fill(pos, tan, spineX, baseZ, pageHeight, frontFace = false) }
    }

    fun destroy() {
        front.destroy()
        back.destroy()
    }

    /**
     * Catmull-Rom through the strip particles, sampled uniformly in particle
     * index — segments are constraint-equal, so index space IS arc length.
     */
    private fun sampleSpline(strip: PageStrip) {
        val n = strip.n
        for (c in 0 until COLS) {
            val s = c.toFloat() / (COLS - 1) * (n - 1)
            val j = s.toInt().coerceAtMost(n - 2)
            val t = s - j

            val x0 = strip.px[maxOf(j - 1, 0)]; val z0 = strip.pz[maxOf(j - 1, 0)]
            val x1 = strip.px[j]; val z1 = strip.pz[j]
            val x2 = strip.px[j + 1]; val z2 = strip.pz[j + 1]
            val x3 = strip.px[minOf(j + 2, n - 1)]; val z3 = strip.pz[minOf(j + 2, n - 1)]

            val t2 = t * t
            val t3 = t2 * t
            colX[c] = 0.5f * (
                2f * x1 + (x2 - x0) * t +
                    (2f * x0 - 5f * x1 + 4f * x2 - x3) * t2 +
                    (3f * x1 - x0 - 3f * x2 + x3) * t3
                )
            colZ[c] = 0.5f * (
                2f * z1 + (z2 - z0) * t +
                    (2f * z0 - 5f * z1 + 4f * z2 - z3) * t2 +
                    (3f * z1 - z0 - 3f * z2 + z3) * t3
                )
            // Spline derivative for the tangent.
            var dx = 0.5f * (
                (x2 - x0) + 2f * (2f * x0 - 5f * x1 + 4f * x2 - x3) * t +
                    3f * (3f * x1 - x0 - 3f * x2 + x3) * t2
                )
            var dz = 0.5f * (
                (z2 - z0) + 2f * (2f * z0 - 5f * z1 + 4f * z2 - z3) * t +
                    3f * (3f * z1 - z0 - 3f * z2 + z3) * t2
                )
            val len = sqrt(dx * dx + dz * dz)
            if (len > 1e-9f) {
                dx /= len; dz /= len
            } else {
                dx = 1f; dz = 0f
            }
            colTx[c] = dx
            colTz[c] = dz
        }
    }

    private fun fill(
        pos: FloatArray,
        tan: FloatArray,
        spineX: Float,
        baseZ: Float,
        pageHeight: Float,
        frontFace: Boolean,
    ) {
        var pi = 0
        var ti = 0
        for (r in 0 until ROWS) {
            val y = (r / (ROWS - 1f) - 0.5f) * pageHeight
            for (c in 0 until COLS) {
                pos[pi++] = spineX + colX[c]
                pos[pi++] = y
                pos[pi++] = baseZ + colZ[c]

                // 2D curve frame lifted to 3D; back face mirrors the frame.
                val sign = if (frontFace) 1f else -1f
                val tx = colTx[c] * sign
                val tz = colTz[c] * sign
                val nx = -colTz[c] * sign
                val nz = colTx[c] * sign
                // b = n x t (= +/-y; y-component: nz*tx - nx*tz)
                val by = nz * tx - nx * tz
                TangentFrames.packQuat(tx, 0f, tz, 0f, by, 0f, nx, 0f, nz, tan, ti)
                ti += 4
            }
        }
    }

    private companion object {
        const val COLS = 32
        const val ROWS = 8
    }
}
