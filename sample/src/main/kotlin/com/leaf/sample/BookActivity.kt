package com.leaf.sample

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import com.leaf.renderer.FilamentNotebookRenderer
import com.leaf.renderer.GestureEvent
import com.leaf.renderer.RenderBinding
import com.leaf.renderer.RenderBook

/**
 * M3 demo: closed vaccination booklet. One finger orbits to inspect covers
 * and spine; pinch dollies in and out.
 */
class BookActivity : Activity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var renderer: FilamentNotebookRenderer
    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surfaceView = SurfaceView(this)
        setContentView(surfaceView)

        renderer = FilamentNotebookRenderer()
        renderer.attach(surfaceView)
        renderer.load(
            RenderBook(
                widthMeters = 0.148f,
                heightMeters = 0.210f,
                sheetCount = 30,
                // Chunky booklet paper so wedge thickness changes read clearly.
                sheetThicknessMeters = 0.00045f,
                coverThicknessMeters = 0.002f,
                binding = RenderBinding.SEWN,
                frontCover = CoverArt.vaccinationBooklet(),
                pageBitmapProvider = PageArt::page,
            ),
        )

        scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    renderer.onGesture(
                        GestureEvent.Pinch(
                            scaleFactor = detector.scaleFactor,
                            x = detector.focusX,
                            y = detector.focusY,
                            timeMillis = detector.eventTime,
                        ),
                    )
                    return true
                }
            },
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true

        val gesture = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> GestureEvent.Down(event.x, event.y, event.eventTime)
            MotionEvent.ACTION_MOVE -> GestureEvent.Move(event.x, event.y, event.eventTime)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                GestureEvent.Up(event.x, event.y, event.eventTime)
            else -> null
        }
        gesture?.let(renderer::onGesture)
        return true
    }

    override fun onDestroy() {
        renderer.detach()
        super.onDestroy()
    }
}
