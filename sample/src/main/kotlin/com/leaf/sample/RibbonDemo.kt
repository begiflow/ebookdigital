package com.leaf.sample

import android.content.res.AssetManager
import android.util.Log
import com.leaf.filament.DynamicMesh
import com.leaf.filament.FilamentHost
import com.leaf.filament.TangentFrames
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * M2 exit-criteria demo: a paper-toned ribbon whose 64x8 grid is deformed on
 * the CPU and re-uploaded to the GPU every frame — positions and analytic
 * tangent frames, exactly the workload of a turning page (docs/07-ROADMAP.md M2).
 * Logs achieved fps once per second under the "LeafSample" tag.
 */
class RibbonDemo(private val host: FilamentHost, assets: AssetManager) {

    private val mesh: DynamicMesh

    private var fpsWindowStart = 0L
    private var fpsFrames = 0

    init {
        val payload = assets.open("materials/ribbon.filamat").use { it.readBytes() }
        val material = host.loadMaterial(payload)
        val instance = material.createInstance().apply {
            setParameter("baseColor", 0.96f, 0.93f, 0.85f)
            setParameter("roughness", 0.55f)
        }

        mesh = DynamicMesh(host.engine, COLS, ROWS, instance)
        host.scene.addEntity(mesh.entity)

        // Reading-lamp key light, top-left (docs/04-GRAPHICS-PIPELINE.md §3).
        host.addDirectionalLight(1f, 0.98f, 0.94f, 100_000f, 0.4f, -0.7f, -0.6f)

        host.camera.setExposure(16f, 1f / 125f, 100f)
        host.camera.lookAt(0.0, 0.0, 2.6, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        host.setClearColor(0.90f, 0.87f, 0.80f)
    }

    fun frame(frameTimeNanos: Long) {
        mesh.update { pos, tan -> fillWave(pos, tan, frameTimeNanos) }
        logFps(frameTimeNanos)
    }

    private fun fillWave(pos: FloatArray, tan: FloatArray, timeNanos: Long) {
        // Wrap to keep double precision healthy over long sessions.
        val t = (timeNanos % 1_000_000_000_000L) / 1e9

        var pi = 0
        var ti = 0
        for (r in 0 until ROWS) {
            val v = r / (ROWS - 1f)
            val y = (v - 0.5f) * HEIGHT
            // Amplitude grows toward the top edge: a hint of the corner-lead
            // behavior real pages will have.
            val amp = AMPLITUDE * (1.0 + 0.35 * y)
            for (c in 0 until COLS) {
                val u = c / (COLS - 1f)
                val x = (u - 0.5f) * WIDTH
                val phase = WAVE_K * x - OMEGA * t

                val z = amp * sin(phase)
                val dzdx = (amp * WAVE_K * cos(phase)).toFloat()
                val dzdy = (0.35 * AMPLITUDE * sin(phase)).toFloat()

                pos[pi++] = x
                pos[pi++] = y
                pos[pi++] = z.toFloat()

                // Analytic frame: t along +x, raw bitangent along +y, n = t x b.
                val tInv = 1f / sqrt(1f + dzdx * dzdx)
                val tx = tInv; val tz = dzdx * tInv
                val bInv = 1f / sqrt(1f + dzdy * dzdy)
                val by = bInv; val bz = dzdy * bInv
                // n = t x b for t=(tx,0,tz), b=(0,by,bz)
                var nx = -tz * by
                var ny = -tx * bz
                var nz = tx * by
                val nInv = 1f / sqrt(nx * nx + ny * ny + nz * nz)
                nx *= nInv; ny *= nInv; nz *= nInv
                // Re-orthogonalize b = n x t so the basis is exactly orthonormal.
                val obx = ny * tz
                val oby = nz * tx - nx * tz
                val obz = -ny * tx

                TangentFrames.packQuat(tx, 0f, tz, obx, oby, obz, nx, ny, nz, tan, ti)
                ti += 4
            }
        }
    }

    private fun logFps(frameTimeNanos: Long) {
        if (fpsWindowStart == 0L) fpsWindowStart = frameTimeNanos
        fpsFrames++
        val elapsed = frameTimeNanos - fpsWindowStart
        if (elapsed >= 1_000_000_000L) {
            val fps = fpsFrames * 1e9 / elapsed
            Log.i(TAG, "fps=%.1f (%d verts re-uploaded/frame)".format(fps, mesh.vertexCount))
            fpsWindowStart = frameTimeNanos
            fpsFrames = 0
        }
    }

    fun destroy() {
        mesh.destroy()
    }

    private companion object {
        const val TAG = "LeafSample"
        const val COLS = 64
        const val ROWS = 8
        const val WIDTH = 2.4f
        const val HEIGHT = 0.8f
        const val AMPLITUDE = 0.16
        const val WAVE_K = 4.0
        const val OMEGA = 3.0
    }
}
