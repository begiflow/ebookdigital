package com.leaf.renderer.scene

import com.google.android.filament.Box
import com.google.android.filament.MaterialInstance
import com.leaf.filament.DynamicMesh
import com.leaf.filament.FilamentHost
import com.leaf.filament.TangentFrames
import com.leaf.physics.PageStrip
import com.leaf.renderer.geometry.PageDeformer
import kotlin.math.sqrt

/**
 * The in-flight sheet: the PBD strip extruded into two per-frame-streamed
 * meshes — front face (sheet front texture) and back face (sheet back
 * texture), coincident geometry with opposite winding so backface culling
 * shows exactly one per view side (docs/03-RENDERER.md §2, 05 §4).
 *
 * M7: extrusion goes through [PageDeformer] — corner grabs skew the rows
 * (grabbed row leads, far edge lags by λ(stiffness)) and normals are analytic
 * from the blended tangent + the skew shear, never averaged from triangles.
 */
class FlightPage(
    host: FilamentHost,
    frontInstance: MaterialInstance,
    backInstance: MaterialInstance,
    /** Tight local bounds around anywhere the page can deform (M10 shadows). */
    bounds: Box,
    /** Grid density; the degradation ladder may lower it (docs/02 §7). */
    private val cols: Int = DEFAULT_COLS,
    private val rows: Int = DEFAULT_ROWS,
) {
    private val front = DynamicMesh(
        host.engine, cols, rows, frontInstance,
        castShadows = true, receiveShadows = true, boundingBox = bounds,
    )
    private val back = DynamicMesh(
        host.engine, cols, rows, backInstance, flipWinding = true,
        castShadows = true, receiveShadows = true, boundingBox = bounds,
    )
    private val deformer = PageDeformer(cols, rows)

    val frontEntity: Int get() = front.entity
    val backEntity: Int get() = back.entity

    init {
        // Back face: mirrored u (its texture reads as a left page).
        val uvs = FloatArray(cols * rows * 2)
        var i = 0
        for (r in 0 until rows) {
            val v = r / (rows - 1f)
            for (c in 0 until cols) {
                uvs[i++] = 1f - c / (cols - 1f)
                uvs[i++] = v
            }
        }
        back.setUvs(uvs)
    }

    /**
     * Re-extrudes both meshes from the strip's current state. [grabV] is the
     * grab row fraction, [skew] the effective skew in 0..1 (strength × (1-λ)),
     * [restAngle] the undeformed rest direction of this turn's source side.
     */
    fun update(
        strip: PageStrip,
        spineX: Float,
        baseZ: Float,
        pageHeight: Float,
        grabV: Float = 0.5f,
        skew: Float = 0f,
        restAngle: Float = 0f,
    ) {
        deformer.deform(strip, grabV, skew, restAngle)
        front.update { pos, tan -> fill(pos, tan, spineX, baseZ, pageHeight, frontFace = true) }
        back.update { pos, tan -> fill(pos, tan, spineX, baseZ, pageHeight, frontFace = false) }
    }

    fun destroy() {
        front.destroy()
        back.destroy()
    }

    private fun fill(
        pos: FloatArray,
        tan: FloatArray,
        spineX: Float,
        baseZ: Float,
        pageHeight: Float,
        frontFace: Boolean,
    ) {
        val d = deformer
        var pi = 0
        var ti = 0
        for (r in 0 until rows) {
            val y = (r / (rows - 1f) - 0.5f) * pageHeight
            val row = r * cols
            for (c in 0 until cols) {
                val i = row + c
                pos[pi++] = spineX + d.posX[i]
                pos[pi++] = y
                pos[pi++] = baseZ + d.posZ[i]

                // Column tangent (unit, in the spread plane).
                val tcx = d.tanX[i]
                val tcz = d.tanZ[i]
                // Row tangent: y advances by pageHeight per unit v, x/z by the
                // skew shear. Normalize.
                var rx = d.dPosXdv[i]
                var ry = pageHeight
                var rz = d.dPosZdv[i]
                val rInv = 1f / sqrt(rx * rx + ry * ry + rz * rz)
                rx *= rInv; ry *= rInv; rz *= rInv

                // Front normal n = t_c × t_r; orthogonal to t_c by construction.
                var nx = -tcz * ry
                var ny = tcz * rx - tcx * rz
                var nz = tcx * ry
                val nInv = 1f / sqrt(nx * nx + ny * ny + nz * nz)
                nx *= nInv; ny *= nInv; nz *= nInv

                // Back face mirrors tangent and normal.
                val sign = if (frontFace) 1f else -1f
                val tx = tcx * sign
                val tz = tcz * sign
                nx *= sign; ny *= sign; nz *= sign

                // b = n × t (t has no y component: t = (tx, 0, tz)).
                val bx = ny * tz
                val by = nz * tx - nx * tz
                val bz = -ny * tx
                TangentFrames.packQuat(tx, 0f, tz, bx, by, bz, nx, ny, nz, tan, ti)
                ti += 4
            }
        }
    }

    companion object {
        const val DEFAULT_COLS = 32
        const val DEFAULT_ROWS = 8
        const val REDUCED_COLS = 24
        const val REDUCED_ROWS = 6
    }
}
