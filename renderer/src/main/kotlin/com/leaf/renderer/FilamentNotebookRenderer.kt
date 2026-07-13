package com.leaf.renderer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.SurfaceView
import com.leaf.filament.FilamentHost
import com.leaf.physics.CoverHinge
import com.leaf.renderer.scene.BookScene
import com.leaf.renderer.scene.RifflePacer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The engine behind the NotebookRenderer API. M8 state: closed book,
 * interactive cover, multi-page turning (pool of 3), fore-edge riffle, and
 * sound + haptic feedback — ray-picked grabs driving PBD strips at a fixed
 * 120Hz timestep (docs/03-RENDERER.md §3, §6).
 */
class FilamentNotebookRenderer : NotebookRenderer {

    private val mutableState = MutableStateFlow<BookState>(BookState.Unloaded)
    override val state: StateFlow<BookState> = mutableState

    private var host: FilamentHost? = null
    private var surfaceView: SurfaceView? = null
    private var scene: BookScene? = null
    private var rig: OrbitCameraRig? = null
    private var hinge: CoverHinge? = null
    private var loadedBook: RenderBook? = null
    private var feedback: FeelFeedback? = null

    private val raycaster = Raycaster(FilamentHost.VERTICAL_FOV_DEGREES.toFloat())
    private val rifflePacer = RifflePacer()

    // M10: frame-time driven quality ladder (docs/02 §7).
    private val ladder = DegradationLadder()
    private var appliedRung = DegradationRung.FULL

    // M9 key sway: gravity sensor tilt leans the key light ~1° so paper
    // grain shimmers as the reader moves (docs/04-GRAPHICS-PIPELINE.md §3).
    private var keySway: KeySway? = null
    private var keySwayEnabled = true
    private var sensorManager: SensorManager? = null
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            keySway?.setTilt(event.values[0], event.values[1], event.values[2])
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private var lastFrameNanos = 0L
    private var simAccumulator = 0f

    private var mode = TouchMode.NONE
    private var downX = 0f
    private var downY = 0f
    private var downTimeMillis = 0L
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var grabAngle = 0f

    // Page-drag tracking: the world-y plane the finger ray is resolved
    // against, and finger velocity in sim space for flick detection.
    private var grabPlaneY = 0f
    private var lastSimX = 0f
    private var lastSimTimeMillis = 0L
    private var simVelocityX = 0f

    private enum class TouchMode { NONE, ORBIT, COVER, PAGE, RIFFLE }

    override fun attach(surfaceView: SurfaceView) {
        check(host == null) { "already attached" }
        this.surfaceView = surfaceView
        feedback = FeelFeedback(surfaceView.context)
        sensorManager =
            (surfaceView.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.also { sm ->
                val gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
                    ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                gravity?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
            }
        host = FilamentHost(surfaceView).also {
            it.frameListener = ::frame
            it.resume()
        }
    }

    override fun detach() {
        sensorManager?.unregisterListener(sensorListener)
        sensorManager = null
        scene?.destroy()
        scene = null
        host?.destroy()
        host = null
        feedback?.release()
        feedback = null
        surfaceView = null
        mutableState.value = BookState.Unloaded
    }

    override fun load(book: RenderBook) {
        val host = checkNotNull(host) { "attach() before load()" }
        val assets = checkNotNull(surfaceView).context.assets

        scene?.destroy()
        val newScene = BookScene(host, book, assets)
        newScene.onFeel = { event -> feedback?.on(event) }
        scene = newScene
        keySway = newScene.keyLightDirection.let { KeySway(it[0], it[1], it[2]) }
        hinge = CoverHinge(
            openRestAngle = newScene.coverOpenRestAngle,
            maxAngle = newScene.coverOpenRestAngle + 0.06f,
        )
        loadedBook = book

        val diag = max(book.heightMeters, book.widthMeters)
        rig = OrbitCameraRig(
            initialDistance = diag * 2.3f,
            minDistance = diag * 1.1f,
            maxDistance = diag * 4.5f,
        )
        mutableState.value = BookState.Closed
    }

    override fun onGesture(gesture: GestureEvent) {
        val rig = rig ?: return
        val view = surfaceView ?: return
        val hinge = hinge
        val width = max(1, view.width).toFloat()
        val height = max(1, view.height).toFloat()

        when (gesture) {
            is GestureEvent.Down -> {
                downX = gesture.x
                downY = gesture.y
                downTimeMillis = gesture.timeMillis
                lastTouchX = gesture.x
                lastTouchY = gesture.y
                mode = when {
                    tryGrabPage(gesture, width, height) -> TouchMode.PAGE
                    tryStartRiffle(gesture, width, height) -> TouchMode.RIFFLE
                    hinge != null && isCoverGrab(gesture.x, width, hinge.angle) -> {
                        grabAngle = hinge.angle
                        hinge.grab()
                        TouchMode.COVER
                    }
                    else -> TouchMode.ORBIT
                }
            }
            is GestureEvent.Move -> when (mode) {
                TouchMode.PAGE -> dragPage(gesture, width, height)
                TouchMode.RIFFLE -> trackRiffle(gesture, width, height)
                TouchMode.COVER -> {
                    val target = grabAngle + (downX - gesture.x) / (width * DRAG_SPAN) * PI.toFloat()
                    hinge?.drag(target)
                }
                TouchMode.ORBIT -> {
                    val dx = (gesture.x - lastTouchX) / width
                    val dy = (gesture.y - lastTouchY) / width
                    rig.dragBy(-dx * ORBIT_SENSITIVITY, dy * ORBIT_SENSITIVITY)
                }
                TouchMode.NONE -> Unit
            }.also {
                lastTouchX = gesture.x
                lastTouchY = gesture.y
            }
            is GestureEvent.Up -> {
                when (mode) {
                    TouchMode.PAGE -> {
                        val hint = when {
                            simVelocityX < -FLICK_VELOCITY -> -1
                            simVelocityX > FLICK_VELOCITY -> 1
                            else -> 0
                        }
                        scene?.releaseTurn(hint)
                    }
                    TouchMode.RIFFLE -> rifflePacer.reset()
                    TouchMode.COVER -> {
                        hinge?.release()
                        handleTap(gesture, width)
                    }
                    else -> handleTap(gesture, width)
                }
                mode = TouchMode.NONE
            }
            is GestureEvent.Pinch -> rig.zoomBy(gesture.scaleFactor)
        }
    }

    // ------------------------- Page picking / dragging ---------------------

    /**
     * Ray-picks the pages; on a hit, starts a turn (or re-grabs an airborne
     * page whose paper passes near the pierce point) with the grabbed
     * material point under the finger (docs/03-RENDERER.md §6). With pages
     * already in flight a resting-page hit starts ANOTHER flight — real
     * fast-flipping — until the pool of 3 is full.
     */
    private fun tryGrabPage(gesture: GestureEvent.Down, width: Float, height: Float): Boolean {
        val scene = scene ?: return false
        val rig = rig ?: return false
        if (mutableState.value !is BookState.Open && !scene.isTurning) return false

        val dir = raycaster.ray(rig.lastEye, rig.lastCenter, gesture.x, gesture.y, width, height)

        // Prefer re-grabbing an airborne page near the pierce point.
        if (scene.isTurning) {
            raycaster.hitPlaneY(dir, 0f)?.let { hit ->
                val simX = scene.worldToSimX(hit.x)
                if (scene.tryRegrab(simX, scene.worldToSimZ(hit.z), maxDist = REGRAB_RADIUS)) {
                    grabPlaneY = 0f
                    resetFlickTracking(gesture.timeMillis, simX)
                    return true
                }
            }
        }

        // Right page -> forward turn.
        raycaster.hitPlaneZ(dir, scene.rightPageWorldZ())?.let { hit ->
            if (scene.pageRectContains(hit.x, hit.y, rightSide = true)) {
                val u = (scene.worldToSimX(hit.x) / loadedPageWidth()).coerceIn(0.05f, 1f)
                if (scene.beginTurn(forward = true, u = u, v = grabRowFraction(hit.y))) {
                    grabPlaneY = hit.y
                    resetFlickTracking(gesture.timeMillis, scene.worldToSimX(hit.x))
                    return true
                }
            }
        }
        // Left page -> backward turn.
        raycaster.hitPlaneZ(dir, scene.leftPageWorldZ())?.let { hit ->
            if (scene.pageRectContains(hit.x, hit.y, rightSide = false)) {
                val u = (-scene.worldToSimX(hit.x) / loadedPageWidth()).coerceIn(0.05f, 1f)
                if (scene.beginTurn(forward = false, u = u, v = grabRowFraction(hit.y))) {
                    grabPlaneY = hit.y
                    resetFlickTracking(gesture.timeMillis, scene.worldToSimX(hit.x))
                    return true
                }
            }
        }
        return false
    }

    /** World y on the page -> row fraction (0 bottom, 1 top) for corner skew. */
    private fun grabRowFraction(worldY: Float): Float {
        val h = scene?.pageHeightMeters ?: return 0.5f
        return (worldY / h + 0.5f).coerceIn(0f, 1f)
    }

    // ------------------------------- Riffle --------------------------------

    /** A touch on the stack's fore-edge strip arms riffle mode (M8). */
    private fun tryStartRiffle(gesture: GestureEvent.Down, width: Float, height: Float): Boolean {
        val scene = scene ?: return false
        val rig = rig ?: return false
        if (mutableState.value !is BookState.Open && !scene.isTurning) return false

        val dir = raycaster.ray(rig.lastEye, rig.lastCenter, gesture.x, gesture.y, width, height)
        val hit = raycaster.hitPlaneZ(dir, scene.rightPageWorldZ()) ?: return false
        if (!scene.riffleZoneContains(hit.x, hit.y)) return false

        rifflePacer.reset()
        resetFlickTracking(gesture.timeMillis, scene.worldToSimX(hit.x))
        return true
    }

    /** Tracks finger speed along the fore-edge; the pacer consumes it per frame. */
    private fun trackRiffle(gesture: GestureEvent.Move, width: Float, height: Float) {
        val scene = scene ?: return
        val rig = rig ?: return
        val dir = raycaster.ray(rig.lastEye, rig.lastCenter, gesture.x, gesture.y, width, height)
        val hit = raycaster.hitPlaneZ(dir, scene.rightPageWorldZ()) ?: return
        val simX = scene.worldToSimX(hit.x)
        val dtMs = gesture.timeMillis - lastSimTimeMillis
        if (dtMs > 0) {
            simVelocityX = (simX - lastSimX) / (dtMs / 1000f)
            lastSimX = simX
            lastSimTimeMillis = gesture.timeMillis
        }
    }

    /** M7 tuning harness hook: live paper feel (docs/05-PHYSICS.md §6). */
    fun setPaperTuning(tuning: PaperTuning) {
        scene?.setPaperTuning(tuning)
    }

    fun paperTuning(): PaperTuning? = scene?.paperTuning

    /** M9: toggle the tilt-driven key sway (docs/04 §3 — optional by design). */
    fun setKeySwayEnabled(enabled: Boolean) {
        keySwayEnabled = enabled
        if (!enabled) {
            val s = scene ?: return
            val base = s.keyLightDirection
            host?.setLightDirection(s.keyLightEntity, base[0], base[1], base[2])
        }
    }

    private fun dragPage(gesture: GestureEvent.Move, width: Float, height: Float) {
        val scene = scene ?: return
        val rig = rig ?: return
        val dir = raycaster.ray(rig.lastEye, rig.lastCenter, gesture.x, gesture.y, width, height)
        val hit = raycaster.hitPlaneY(dir, grabPlaneY) ?: return

        val simX = scene.worldToSimX(hit.x)
        val simZ = scene.worldToSimZ(hit.z)
        scene.dragTurn(simX, simZ)

        val dtMs = gesture.timeMillis - lastSimTimeMillis
        if (dtMs > 0) {
            simVelocityX = (simX - lastSimX) / (dtMs / 1000f)
            lastSimX = simX
            lastSimTimeMillis = gesture.timeMillis
        }
    }

    private fun resetFlickTracking(timeMillis: Long, simX: Float) {
        lastSimX = simX
        lastSimTimeMillis = timeMillis
        simVelocityX = 0f
    }

    private fun loadedPageWidth(): Float {
        val book = loadedBook ?: return 1f
        return book.widthMeters * 0.98f - 0.0015f
    }

    // ----------------------------- Tap / cover -----------------------------

    private fun handleTap(up: GestureEvent.Up, width: Float) {
        val scene = scene ?: return
        if (scene.isTurning) return
        if (mutableState.value !is BookState.Open) return
        val quick = up.timeMillis - downTimeMillis < TAP_TIMEOUT_MS
        val still = abs(up.x - downX) + abs(up.y - downY) < TAP_SLOP_PX
        if (!quick || !still) return
        when {
            up.x > width * 2f / 3f -> scene.setSpread(scene.spread + 1)
            up.x < width / 3f -> scene.setSpread(scene.spread - 1)
        }
    }

    private fun isCoverGrab(x: Float, width: Float, angle: Float): Boolean {
        val hinge = hinge ?: return false
        return when {
            angle < 0.35f -> x > width * (1f - EDGE_ZONE)
            angle > hinge.openRestAngle - 0.6f -> x < width * EDGE_ZONE
            else -> true // cover mid-flight: any touch re-grabs it
        }
    }

    // ------------------------------- Frame ---------------------------------

    private fun frame(frameTimeNanos: Long) {
        val dt = if (lastFrameNanos == 0L) {
            FALLBACK_DT
        } else {
            ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(1e-4f, MAX_DT)
        }
        lastFrameNanos = frameTimeNanos

        val host = host ?: return
        val scene = scene

        // Riffle pump: finger speed feeds the pacer; the pool caps bursts.
        // Speed decays between move events so a parked finger stops feeding.
        if (mode == TouchMode.RIFFLE) {
            simVelocityX *= (1f - (RIFFLE_SPEED_DECAY * dt).coerceAtMost(1f))
            val pages = rifflePacer.step(dt, abs(simVelocityX))
            repeat(pages) { scene?.riffleTurn() }
        }

        hinge?.let { h ->
            simAccumulator = (simAccumulator + dt).coerceAtMost(MAX_ACCUMULATED)
            while (simAccumulator >= SIM_DT) {
                h.step(SIM_DT)
                scene?.stepTurn(SIM_DT)
                simAccumulator -= SIM_DT
            }
            scene?.setCoverAngle(h.angle)

            if (scene != null && scene.isTurning) scene.updateFlightMesh()
            publishState(h)
        }

        // Key sway (M9): lean the key light with device tilt.
        if (keySwayEnabled) {
            val sway = keySway
            val s = scene
            if (sway != null && s != null) {
                val dir = sway.update(dt)
                host.setLightDirection(s.keyLightEntity, dir[0], dir[1], dir[2])
            }
        }

        // Degradation ladder (M10): sustained vsync misses step quality down;
        // sustained headroom steps it back up. Physics dt stays fixed.
        val rung = ladder.feed(dt)
        if (rung != appliedRung) {
            appliedRung = rung
            scene?.applyDegradationRung(rung)
            host.minFramePeriodNanos =
                if (rung == DegradationRung.CAPPED_60) CAP_60_PERIOD_NANOS else 0L
        }

        rig?.let {
            it.update(dt)
            val fraction = hinge?.let { h -> (h.angle / h.openRestAngle).coerceIn(0f, 1f) } ?: 0f
            val spineX = -(loadedBook?.widthMeters ?: 0f) / 2f
            it.applyTo(
                host.camera,
                spineX * fraction, 0f, 0f,
                distanceScale = 1f + OPEN_DISTANCE_BOOST * fraction,
            )
        }
    }

    private fun publishState(hinge: CoverHinge) {
        val scene = scene
        val next = when {
            scene != null && scene.isTurning -> BookState.Turning(
                spreadIndex = scene.spread,
                forward = scene.turnDirectionForward,
                pagesInFlight = scene.pagesInFlight,
            )
            hinge.isSettled && hinge.angle < CLOSED_EPSILON -> BookState.Closed
            hinge.isSettled -> BookState.Open(scene?.spread ?: 0)
            else -> BookState.CoverOpening(
                (hinge.angle / hinge.openRestAngle).coerceIn(0f, 1f),
            )
        }
        val prev = mutableState.value
        if (prev != next) {
            // Cover detent moments are physical events too (M8).
            if (prev is BookState.CoverOpening) {
                when (next) {
                    is BookState.Open -> feedback?.on(FeelEvent.COVER_OPEN)
                    is BookState.Closed -> feedback?.on(FeelEvent.COVER_CLOSE)
                    else -> Unit
                }
            }
            mutableState.value = next
        }
    }

    private companion object {
        const val ORBIT_SENSITIVITY = 3.2f
        const val FALLBACK_DT = 1f / 60f
        const val MAX_DT = 1f / 10f
        const val SIM_DT = 1f / 120f
        const val MAX_ACCUMULATED = 0.25f
        const val EDGE_ZONE = 0.38f
        const val DRAG_SPAN = 0.55f
        const val CLOSED_EPSILON = 0.05f
        const val OPEN_DISTANCE_BOOST = 0.95f
        const val TAP_TIMEOUT_MS = 250L
        const val TAP_SLOP_PX = 24f
        const val FLICK_VELOCITY = 0.35f
        const val REGRAB_RADIUS = 0.02f
        const val RIFFLE_SPEED_DECAY = 6f
        const val CAP_60_PERIOD_NANOS = 1_000_000_000L / 60L
    }
}
