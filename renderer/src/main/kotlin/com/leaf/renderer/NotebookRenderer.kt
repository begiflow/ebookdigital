package com.leaf.renderer

import android.view.SurfaceView
import kotlinx.coroutines.flow.StateFlow

/**
 * The narrow API through which the app drives the notebook engine
 * (docs/02-ARCHITECTURE.md §4). Implemented inside :renderer starting at M3;
 * book loading and texture streaming are added to this contract when
 * RenderBook exists.
 */
interface NotebookRenderer {

    /** Current engine state, observed by presentation (never polled). */
    val state: StateFlow<BookState>

    /** Attaches the engine to a SurfaceView and starts the frame loop. */
    fun attach(surfaceView: SurfaceView)

    /** Stops the frame loop and releases surface resources. */
    fun detach()

    /** Loads a book into the scene. Requires a prior [attach]. */
    fun load(book: RenderBook)

    /** Forwards a raw touch event; the engine owns gesture interpretation. */
    fun onGesture(gesture: GestureEvent)
}

/** Engine states per the state machine in docs/03-RENDERER.md §7. */
sealed interface BookState {
    data object Unloaded : BookState
    data object Closed : BookState

    /** Front cover in flight (dragged or settling); [fraction] 0=closed, 1=open. */
    data class CoverOpening(val fraction: Float) : BookState

    data class Open(val spreadIndex: Int) : BookState
    data class Turning(val spreadIndex: Int, val forward: Boolean, val pagesInFlight: Int) : BookState
}

/** Raw pointer events in view coordinates; timestamps feed velocity estimation. */
sealed interface GestureEvent {
    val x: Float
    val y: Float
    val timeMillis: Long

    data class Down(override val x: Float, override val y: Float, override val timeMillis: Long) : GestureEvent
    data class Move(override val x: Float, override val y: Float, override val timeMillis: Long) : GestureEvent
    data class Up(override val x: Float, override val y: Float, override val timeMillis: Long) : GestureEvent

    /**
     * Incremental two-finger scale at a focal point. Pointer pairing is done
     * app-side (ScaleGestureDetector); the engine decides what zoom means in
     * its current state.
     */
    data class Pinch(
        val scaleFactor: Float,
        override val x: Float,
        override val y: Float,
        override val timeMillis: Long,
    ) : GestureEvent
}
