package com.leaf.filament

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * A grid mesh whose vertices are re-uploaded every frame — the streaming
 * pattern every in-flight page will use (docs/03-RENDERER.md §2). Attributes:
 * POSITION (float3) and TANGENTS (float4 quaternion, see [TangentFrames]).
 *
 * CPU-side arrays are double-buffered: Filament consumes upload buffers
 * asynchronously, so we alternate generations to never write an array the
 * backend may still be copying from.
 */
class DynamicMesh(
    private val engine: Engine,
    val cols: Int,
    val rows: Int,
    materialInstance: MaterialInstance,
    /** Flip triangle winding for meshes whose visible face is -Z (left pages). */
    flipWinding: Boolean = false,
) {
    init {
        require(cols >= 2 && rows >= 2) { "grid needs at least 2x2 vertices" }
        require(cols * rows <= MAX_VERTICES) { "USHORT index space exceeded" }
    }

    val vertexCount: Int = cols * rows
    val entity: Int = EntityManager.get().create()

    private val vertexBuffer: VertexBuffer = VertexBuffer.Builder()
        .bufferCount(3)
        .vertexCount(vertexCount)
        .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 0)
        .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 0)
        .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 0)
        .build(engine)

    private val indexBuffer: IndexBuffer

    private val positions = Array(GENERATIONS) { FloatArray(vertexCount * 3) }
    private val tangents = Array(GENERATIONS) { FloatArray(vertexCount * 4) }
    private var generation = 0

    init {
        val indices = ShortArray((cols - 1) * (rows - 1) * 6)
        var i = 0
        for (r in 0 until rows - 1) {
            for (c in 0 until cols - 1) {
                val v00 = (r * cols + c).toShort()
                val v01 = (r * cols + c + 1).toShort()
                val v10 = ((r + 1) * cols + c).toShort()
                val v11 = ((r + 1) * cols + c + 1).toShort()
                if (flipWinding) {
                    indices[i++] = v00; indices[i++] = v10; indices[i++] = v01
                    indices[i++] = v01; indices[i++] = v10; indices[i++] = v11
                } else {
                    indices[i++] = v00; indices[i++] = v01; indices[i++] = v10
                    indices[i++] = v01; indices[i++] = v11; indices[i++] = v10
                }
            }
        }

        // Default grid UVs: u along columns, v along rows (row 0 = v0).
        // Override with setUvs for atlas/mirrored mappings.
        val uvs = FloatArray(vertexCount * 2)
        var u = 0
        for (r in 0 until rows) {
            val v = r / (rows - 1f)
            for (c in 0 until cols) {
                uvs[u++] = c / (cols - 1f)
                uvs[u++] = v
            }
        }
        vertexBuffer.setBufferAt(engine, 2, FloatBuffer.wrap(uvs))
        indexBuffer = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        indexBuffer.setBuffer(engine, ShortBuffer.wrap(indices))

        RenderableManager.Builder(1)
            // Generous static bounds: deformation stays well inside; per-frame
            // bound recomputation is not worth the cost (culling disabled anyway
            // — a page is essentially always on screen).
            .boundingBox(Box(0f, 0f, 0f, 4f, 4f, 4f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, materialInstance)
            .castShadows(false)
            .receiveShadows(false)
            .culling(false)
            .build(engine, entity)
    }

    /**
     * Fills the next generation's arrays via [fill] and schedules the upload.
     * Layout: positions xyz-interleaved, tangents xyzw-interleaved quaternions,
     * vertex order row-major (row * cols + col).
     */
    fun update(fill: (positions: FloatArray, tangents: FloatArray) -> Unit) {
        val pos = positions[generation]
        val tan = tangents[generation]
        fill(pos, tan)
        vertexBuffer.setBufferAt(engine, 0, FloatBuffer.wrap(pos))
        vertexBuffer.setBufferAt(engine, 1, FloatBuffer.wrap(tan))
        generation = (generation + 1) % GENERATIONS
    }

    /** Replaces the static UV mapping (size must be vertexCount * 2). */
    fun setUvs(uvs: FloatArray) {
        require(uvs.size == vertexCount * 2) { "uv size mismatch" }
        vertexBuffer.setBufferAt(engine, 2, FloatBuffer.wrap(uvs.copyOf()))
    }

    fun destroy() {
        engine.destroyEntity(entity)
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        EntityManager.get().destroy(entity)
    }

    private companion object {
        const val GENERATIONS = 2
        const val MAX_VERTICES = 65536
    }
}
