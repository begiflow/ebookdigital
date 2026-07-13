package com.leaf.renderer.geometry

import com.leaf.filament.MeshData
import com.leaf.filament.TangentFrames
import com.leaf.physics.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Static geometry for the closed notebook (docs/03-RENDERER.md §1).
 *
 * Book space: origin at book center, X to the right (spine at -X), Y up,
 * Z toward the reader (front cover at +Z). Units: meters.
 */
object BookGeometry {

    /**
     * An axis-aligned board (cover or page block): w x h x d centered at
     * [cx],[cy],[cz]. The +Z face maps the full texture; -Z mirrors it
     * horizontally (so a back cover reads correctly when the book is flipped);
     * edge faces sample a thin border strip of the texture.
     */
    fun board(
        w: Float, h: Float, d: Float,
        cx: Float, cy: Float, cz: Float,
        includeBackFace: Boolean = true,
    ): MeshData {
        val b = MeshDataBuilder()
        val x0 = cx - w / 2f; val x1 = cx + w / 2f
        val y0 = cy - h / 2f; val y1 = cy + h / 2f
        val z0 = cz - d / 2f; val z1 = cz + d / 2f

        // Edge faces sample near the texture border for a board-edge tone.
        val e0 = floatArrayOf(0.01f, 0.5f)
        val e1 = floatArrayOf(0.03f, 0.5f)

        // Front (+Z), full texture. Bitmap top samples at v=1 (verified on
        // device — TextureHelper upload + Filament sampling).
        b.addQuad(
            Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1),
            t = Vec3(1f, 0f, 0f), n = Vec3(0f, 0f, 1f),
            uv00 = floatArrayOf(0f, 0f), uv10 = floatArrayOf(1f, 0f),
            uv11 = floatArrayOf(1f, 1f), uv01 = floatArrayOf(0f, 1f),
        )
        // Back (-Z), mirrored horizontally. Skipped for hinged covers, whose
        // inner face is a separate mesh with its own (endpaper) material.
        if (includeBackFace) {
            b.addQuad(
                Vec3(x1, y0, z0), Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x1, y1, z0),
                t = Vec3(-1f, 0f, 0f), n = Vec3(0f, 0f, -1f),
                uv00 = floatArrayOf(0f, 0f), uv10 = floatArrayOf(1f, 0f),
                uv11 = floatArrayOf(1f, 1f), uv01 = floatArrayOf(0f, 1f),
            )
        }
        // Right (+X)
        b.addQuad(
            Vec3(x1, y0, z1), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1),
            t = Vec3(0f, 0f, -1f), n = Vec3(1f, 0f, 0f),
            uv00 = e0, uv10 = e1, uv11 = e1, uv01 = e0,
        )
        // Left (-X)
        b.addQuad(
            Vec3(x0, y0, z0), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x0, y1, z0),
            t = Vec3(0f, 0f, 1f), n = Vec3(-1f, 0f, 0f),
            uv00 = e0, uv10 = e1, uv11 = e1, uv01 = e0,
        )
        // Top (+Y)
        b.addQuad(
            Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), Vec3(x0, y1, z0),
            t = Vec3(1f, 0f, 0f), n = Vec3(0f, 1f, 0f),
            uv00 = e0, uv10 = e1, uv11 = e1, uv01 = e0,
        )
        // Bottom (-Y)
        b.addQuad(
            Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x0, y0, z1),
            t = Vec3(1f, 0f, 0f), n = Vec3(0f, -1f, 0f),
            uv00 = e0, uv10 = e1, uv11 = e1, uv01 = e0,
        )
        return b.build()
    }

    /**
     * A single textured quad at depth [z]: endpapers and inside-cover faces.
     * [facingPositiveZ] false = visible from -Z (a hinged cover's inner face).
     */
    fun panel(
        w: Float, h: Float,
        cx: Float, cy: Float, z: Float,
        facingPositiveZ: Boolean,
    ): MeshData {
        val b = MeshDataBuilder()
        val x0 = cx - w / 2f; val x1 = cx + w / 2f
        val y0 = cy - h / 2f; val y1 = cy + h / 2f
        if (facingPositiveZ) {
            b.addQuad(
                Vec3(x0, y0, z), Vec3(x1, y0, z), Vec3(x1, y1, z), Vec3(x0, y1, z),
                t = Vec3(1f, 0f, 0f), n = Vec3(0f, 0f, 1f),
                uv00 = floatArrayOf(0f, 0f), uv10 = floatArrayOf(1f, 0f),
                uv11 = floatArrayOf(1f, 1f), uv01 = floatArrayOf(0f, 1f),
            )
        } else {
            b.addQuad(
                Vec3(x1, y0, z), Vec3(x0, y0, z), Vec3(x0, y1, z), Vec3(x1, y1, z),
                t = Vec3(-1f, 0f, 0f), n = Vec3(0f, 0f, -1f),
                uv00 = floatArrayOf(0f, 0f), uv10 = floatArrayOf(1f, 0f),
                uv11 = floatArrayOf(1f, 1f), uv01 = floatArrayOf(0f, 1f),
            )
        }
        return b.build()
    }

    /**
     * SEWN spine: a half-round shell hugging the -X edge, bridging the front
     * and back covers. Profile parameter theta in [0, pi]: theta=0 meets the
     * front cover, theta=pi the back cover, bulging outward (-X).
     * UVs sample a vertical strip at the texture's left edge.
     */
    fun sewnSpine(
        height: Float,
        radius: Float,
        spineX: Float,
        segments: Int = 16,
    ): MeshData {
        val b = MeshDataBuilder()
        val y0 = -height / 2f
        val y1 = height / 2f

        var prevPos0: Vec3? = null; var prevPos1: Vec3? = null
        var prevQuat: FloatArray? = null
        var prevU = 0f

        for (i in 0..segments) {
            val theta = (PI * i / segments).toFloat()
            val x = spineX - radius * sin(theta)
            val z = radius * cos(theta)
            val p0 = Vec3(x, y0, z)
            val p1 = Vec3(x, y1, z)

            // Outward normal; tangent runs along the arc (direction of travel).
            val n = Vec3(-sin(theta), 0f, cos(theta))
            val t = Vec3(-cos(theta), 0f, -sin(theta))
            val bt = n cross t
            val quat = FloatArray(4)
            TangentFrames.packQuat(t.x, t.y, t.z, bt.x, bt.y, bt.z, n.x, n.y, n.z, quat, 0)
            val u = 0.02f + 0.06f * (i.toFloat() / segments)

            if (prevPos0 != null) {
                // Corner order chosen so triangles face outward (-X side).
                b.addQuadFrames(
                    p0, prevPos0!!, prevPos1!!, p1,
                    quat, prevQuat!!, prevQuat!!, quat,
                    uv00 = floatArrayOf(u, 0f), uv10 = floatArrayOf(prevU, 0f),
                    uv11 = floatArrayOf(prevU, 1f), uv01 = floatArrayOf(u, 1f),
                )
            }
            prevPos0 = p0; prevPos1 = p1; prevQuat = quat; prevU = u
        }
        return b.build()
    }
}
