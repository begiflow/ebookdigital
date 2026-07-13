package com.leaf.filament

import android.os.Trace
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Owns the Filament engine, swap chain, and the Choreographer-driven frame
 * loop for one SurfaceView. This is the only place in the project that talks
 * to Filament's engine lifecycle; :renderer builds scenes through the handles
 * exposed here.
 *
 * Frame flow (docs/04-GRAPHICS-PIPELINE.md §1):
 *   Choreographer -> onFrame(frameTimeNanos) -> [frameListener] -> render
 * where [frameListener] is the hook :renderer uses for physics + mesh upload.
 */
class FilamentHost(private val surfaceView: SurfaceView) {

    /** Called every frame before rendering; receives the frame time in nanos. */
    var frameListener: ((frameTimeNanos: Long) -> Unit)? = null

    val engine: Engine = Engine.create()
    val scene: Scene = engine.createScene()
    val view: View = engine.createView()
    val camera: Camera = engine.createCamera(EntityManager.get().create())

    private val renderer: Renderer = engine.createRenderer()
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper = DisplayHelper(surfaceView.context)
    private val choreographer = Choreographer.getInstance()

    private var swapChain: SwapChain? = null
    private var running = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            choreographer.postFrameCallback(this)
            // "LeafFrame" is measured by :benchmark via TraceSectionMetric —
            // it covers sim + mesh upload (frameListener) and render submission.
            Trace.beginSection("LeafFrame")
            try {
                frameListener?.invoke(frameTimeNanos)
                val sc = swapChain ?: return
                if (uiHelper.isReadyToRender && renderer.beginFrame(sc, frameTimeNanos)) {
                    renderer.render(view)
                    renderer.endFrame()
                }
            } finally {
                Trace.endSection()
            }
        }
    }

    init {
        view.scene = scene
        view.camera = camera

        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                displayHelper.attach(renderer, surfaceView.display)
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                val aspect = width.toDouble() / height.toDouble()
                camera.setProjection(VERTICAL_FOV_DEGREES, aspect, 0.05, 100.0, Camera.Fov.VERTICAL)
            }
        }
        uiHelper.attachTo(surfaceView)
    }

    /** Loads a compiled .filamat payload (e.g. read from assets). */
    fun loadMaterial(payload: ByteArray): Material {
        val buffer = ByteBuffer.allocateDirect(payload.size)
            .order(ByteOrder.nativeOrder())
            .put(payload)
        buffer.flip()
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    /** Adds a directional light to the scene; returns its entity. */
    fun addDirectionalLight(
        r: Float, g: Float, b: Float,
        intensityLux: Float,
        dirX: Float, dirY: Float, dirZ: Float,
        castShadows: Boolean = false,
    ): Int {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(r, g, b)
            .intensity(intensityLux)
            .direction(dirX, dirY, dirZ)
            .castShadows(castShadows)
            .build(engine, entity)
        scene.addEntity(entity)
        return entity
    }

    /**
     * Sets ambient irradiance from spherical harmonics (3 bands, 27 floats,
     * RGB-interleaved). Hand-tuned SH stands in until the real IBL asset
     * lands in M9 (docs/04-GRAPHICS-PIPELINE.md §3).
     */
    fun setAmbientLight(intensity: Float, sh: FloatArray) {
        require(sh.size == 27) { "expected 3 SH bands = 27 floats" }
        scene.indirectLight = IndirectLight.Builder()
            .irradiance(3, sh)
            .intensity(intensity)
            .build(engine)
    }

    /** Sets the background clear color (linear RGBA). */
    fun setClearColor(r: Float, g: Float, b: Float, a: Float = 1f) {
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(r.toDouble(), g.toDouble(), b.toDouble(), a.toDouble())
        }
    }

    fun resume() {
        if (running) return
        running = true
        choreographer.postFrameCallback(frameCallback)
    }

    fun pause() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    fun destroy() {
        pause()
        uiHelper.detach()
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        EntityManager.get().destroy(camera.entity)
        engine.destroy()
    }

    companion object {
        /** Shared with the renderer's ray caster — keep in sync with projection. */
        const val VERTICAL_FOV_DEGREES = 40.0

        init {
            Filament.init()
        }
    }
}
