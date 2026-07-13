package com.leaf.renderer.geometry

import com.leaf.filament.MeshData
import com.leaf.filament.TangentFrames
import com.leaf.physics.math.Vec3

/**
 * Accumulates textured quads into a [MeshData]. Corner order is
 * counter-clockwise seen from the front (normal side): p00 -> p10 -> p11 -> p01.
 */
class MeshDataBuilder {

    private val positions = ArrayList<Float>(256 * 3)
    private val tangents = ArrayList<Float>(256 * 4)
    private val uvs = ArrayList<Float>(256 * 2)
    private val indices = ArrayList<Short>(256 * 3)

    /**
     * Adds a quad with an explicit orthonormal frame ([t]angent, [n]ormal;
     * bitangent derived as n x t) shared by all four corners — correct for
     * planar quads; curved surfaces add per-corner frames via [addQuadFrames].
     */
    fun addQuad(
        p00: Vec3, p10: Vec3, p11: Vec3, p01: Vec3,
        t: Vec3, n: Vec3,
        uv00: FloatArray, uv10: FloatArray, uv11: FloatArray, uv01: FloatArray,
    ) {
        val b = n cross t
        val quat = FloatArray(4)
        TangentFrames.packQuat(t.x, t.y, t.z, b.x, b.y, b.z, n.x, n.y, n.z, quat, 0)
        addQuadFrames(
            p00, p10, p11, p01,
            quat, quat, quat, quat,
            uv00, uv10, uv11, uv01,
        )
    }

    /** Adds a quad with per-corner packed tangent-frame quaternions. */
    fun addQuadFrames(
        p00: Vec3, p10: Vec3, p11: Vec3, p01: Vec3,
        q00: FloatArray, q10: FloatArray, q11: FloatArray, q01: FloatArray,
        uv00: FloatArray, uv10: FloatArray, uv11: FloatArray, uv01: FloatArray,
    ) {
        val base = (positions.size / 3).toShort()
        vertex(p00, q00, uv00)
        vertex(p10, q10, uv10)
        vertex(p11, q11, uv11)
        vertex(p01, q01, uv01)
        // Two CCW triangles: (0,1,2) (0,2,3)
        indices.add(base)
        indices.add((base + 1).toShort())
        indices.add((base + 2).toShort())
        indices.add(base)
        indices.add((base + 2).toShort())
        indices.add((base + 3).toShort())
    }

    fun build(): MeshData = MeshData(
        positions = positions.toFloatArray(),
        tangents = tangents.toFloatArray(),
        uvs = uvs.toFloatArray(),
        indices = indices.toShortArray(),
    )

    private fun vertex(p: Vec3, quat: FloatArray, uv: FloatArray) {
        positions.add(p.x); positions.add(p.y); positions.add(p.z)
        tangents.add(quat[0]); tangents.add(quat[1]); tangents.add(quat[2]); tangents.add(quat[3])
        uvs.add(uv[0]); uvs.add(uv[1])
    }
}
