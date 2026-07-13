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
 * Immutable mesh payload: positions (xyz), tangent-frame quaternions (xyzw,
 * see [TangentFrames]), UVs (uv), and triangle indices.
 */
class MeshData(
    val positions: FloatArray,
    val tangents: FloatArray,
    val uvs: FloatArray,
    val indices: ShortArray,
) {
    val vertexCount: Int = positions.size / 3

    init {
        require(tangents.size == vertexCount * 4) { "tangents size mismatch" }
        require(uvs.size == vertexCount * 2) { "uvs size mismatch" }
    }
}

/**
 * Uploaded-once geometry for the static parts of a notebook: covers, spine,
 * page blocks (docs/03-RENDERER.md §1). Counterpart of [DynamicMesh].
 */
class StaticMesh(
    private val engine: Engine,
    data: MeshData,
    materialInstance: MaterialInstance,
    castShadows: Boolean = true,
    receiveShadows: Boolean = true,
) {
    val entity: Int = EntityManager.get().create()

    private val vertexBuffer: VertexBuffer = VertexBuffer.Builder()
        .bufferCount(3)
        .vertexCount(data.vertexCount)
        .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 0)
        .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 0)
        .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 0)
        .build(engine)

    private val indexBuffer: IndexBuffer = IndexBuffer.Builder()
        .indexCount(data.indices.size)
        .bufferType(IndexBuffer.Builder.IndexType.USHORT)
        .build(engine)

    init {
        vertexBuffer.setBufferAt(engine, 0, FloatBuffer.wrap(data.positions))
        vertexBuffer.setBufferAt(engine, 1, FloatBuffer.wrap(data.tangents))
        vertexBuffer.setBufferAt(engine, 2, FloatBuffer.wrap(data.uvs))
        indexBuffer.setBuffer(engine, ShortBuffer.wrap(data.indices))

        RenderableManager.Builder(1)
            .boundingBox(computeBounds(data.positions))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, materialInstance)
            .castShadows(castShadows)
            .receiveShadows(receiveShadows)
            .build(engine, entity)
    }

    fun destroy() {
        engine.destroyEntity(entity)
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        EntityManager.get().destroy(entity)
    }

    private fun computeBounds(positions: FloatArray): Box {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < positions.size) {
            val x = positions[i]; val y = positions[i + 1]; val z = positions[i + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            i += 3
        }
        return Box(
            (minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f,
            (maxX - minX) / 2f + PADDING, (maxY - minY) / 2f + PADDING, (maxZ - minZ) / 2f + PADDING,
        )
    }

    private companion object {
        const val PADDING = 1e-4f
    }
}
