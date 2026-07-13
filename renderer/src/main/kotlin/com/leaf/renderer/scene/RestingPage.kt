package com.leaf.renderer.scene

import com.google.android.filament.MaterialInstance
import com.leaf.filament.DynamicMesh
import com.leaf.filament.FilamentHost
import com.leaf.filament.TangentFrames
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A resting page of the open spread: a grid mesh with the profile's rest-pose
 * bow near the spine (docs/03-RENDERER.md §2). Static between spread changes —
 * the mesh is re-filled only on setShape, never per frame. In M6 in-flight
 * pages reuse the same mesh class driven by the PBD strip instead.
 *
 * Local frame: x = 0 at the spine edge increasing toward the fore-edge,
 * y vertical, +z out of the page front. [facingPositiveZ] false = left pages
 * (visible face -z; winding flipped accordingly).
 */
class RestingPage(
    host: FilamentHost,
    materialInstance: MaterialInstance,
    private val facingPositiveZ: Boolean,
) {
    private val mesh = DynamicMesh(
        host.engine,
        COLS,
        ROWS,
        materialInstance,
        flipWinding = !facingPositiveZ,
    )

    val entity: Int get() = mesh.entity

    /**
     * [spineU] texture u at the spine edge (1 for left pages, 0 for right).
     * The bow lifts the sheet near the spine and settles flat by
     * [bowExtent] of the width.
     */
    fun setShape(
        originX: Float,
        width: Float,
        height: Float,
        baseZ: Float,
        bowHeight: Float,
        bowExtent: Float = 0.45f,
        spineU: Float,
    ) {
        val uvs = FloatArray(COLS * ROWS * 2)
        var uv = 0
        mesh.update { pos, tan ->
            var pi = 0
            var ti = 0
            for (r in 0 until ROWS) {
                val vFrac = r / (ROWS - 1f)
                val y = (vFrac - 0.5f) * height
                for (c in 0 until COLS) {
                    val s = c / (COLS - 1f)
                    val x = originX + s * width

                    val (z, dzds) = bow(s, bowHeight, bowExtent)
                    pos[pi++] = x
                    pos[pi++] = y
                    pos[pi++] = baseZ + if (facingPositiveZ) z else -z

                    writeFrame(dzds / width, tan, ti)
                    ti += 4

                    uvs[uv++] = spineU + (if (spineU == 0f) s else -s)
                    uvs[uv++] = vFrac
                }
            }
        }
        mesh.setUvs(uvs)
    }

    fun destroy() = mesh.destroy()

    private fun bow(s: Float, amplitude: Float, extent: Float): Pair<Float, Float> {
        if (s >= extent) return 0f to 0f
        val phase = (s / extent) * PI.toFloat()
        val z = amplitude * sin(phase)
        val dzds = amplitude * (PI.toFloat() / extent) * cos(phase)
        return z to dzds
    }

    private fun writeFrame(slope: Float, tan: FloatArray, offset: Int) {
        // Tangent along +x with the bow slope; sign of z-extent follows facing.
        val dz = if (facingPositiveZ) slope else -slope
        val inv = 1f / sqrt(1f + dz * dz)
        val tx = inv; val tz = dz * inv
        // Normal perpendicular to tangent, pointing toward the visible side.
        val sign = if (facingPositiveZ) 1f else -1f
        val nx = -tz * sign; val nz = tx * sign
        // b = n x t
        val by = nz * tx - nx * tz
        TangentFrames.packQuat(tx, 0f, tz, 0f, by, 0f, nx, 0f, nz, tan, offset)
    }

    private companion object {
        const val COLS = 32
        const val ROWS = 4
    }
}
