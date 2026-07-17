package com.leaf.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.leaf.data.files.FileStore
import com.leaf.domain.model.NotebookId
import com.leaf.domain.repo.NotebookRepository
import com.leaf.renderer.FilamentNotebookRenderer
import com.leaf.renderer.GestureEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The engine scene hosting a stored notebook (docs/01-PRD.md §5.2): opened
 * from the shelf with a fade handoff — the closed book appears wearing the
 * same cover the shelf card showed, so the transition reads as the notebook
 * lifting off the shelf rather than a screen change.
 */
@AndroidEntryPoint
class NotebookActivity : ComponentActivity() {

    @Inject lateinit var repository: NotebookRepository

    @Inject lateinit var store: FileStore

    private lateinit var surfaceView: SurfaceView
    private lateinit var renderer: FilamentNotebookRenderer
    private lateinit var scaleDetector: ScaleGestureDetector
    private var provider: KtxTextureProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        surfaceView = SurfaceView(this)
        setContentView(surfaceView)

        renderer = FilamentNotebookRenderer()
        renderer.attach(surfaceView)

        val id = intent.getStringExtra(EXTRA_ID) ?: run {
            finish()
            return
        }
        lifecycleScope.launch {
            val notebook = repository.notebook(NotebookId(id)) ?: run {
                finish()
                return@launch
            }
            val ktxProvider = KtxTextureProvider(notebook, store)
            provider = ktxProvider
            renderer.setTextureProvider(ktxProvider)
            renderer.load(NotebookPresentation.toRenderBook(notebook, store))
        }

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
        provider?.shutdown()
        renderer.detach()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "notebook_id"

        fun open(context: Context, notebookId: String) {
            context.startActivity(
                Intent(context, NotebookActivity::class.java).putExtra(EXTRA_ID, notebookId),
            )
        }
    }
}
