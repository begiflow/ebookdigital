package com.leaf.sample

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.leaf.renderer.FilamentNotebookRenderer
import com.leaf.renderer.GestureEvent
import com.leaf.renderer.PaperTuning
import com.leaf.renderer.RenderBinding
import com.leaf.renderer.RenderBook
import com.leaf.renderer.RenderGrain
import java.io.File

/**
 * Engine demo: vaccination booklet with interactive cover and page turning.
 * One finger orbits/drags; pinch dollies.
 *
 * M7 adds the tuning harness (docs/05-PHYSICS.md §6): the TUNE overlay exposes
 * stiffness / damping / air drag / show-through sliders plus profile presets,
 * and exports the current values as profile JSON — paper feel is tuned by
 * hand on device and committed as data.
 */
class BookActivity : Activity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var renderer: FilamentNotebookRenderer
    private lateinit var scaleDetector: ScaleGestureDetector

    private var tuning = BOOKLET

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surfaceView = SurfaceView(this)

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
                paperStiffness = tuning.stiffness,
                paperTranslucency = tuning.translucency,
                grain = RenderGrain.LAID,
            ),
        )

        val root = FrameLayout(this)
        root.addView(surfaceView)
        root.addView(buildTuningOverlay())
        setContentView(root)

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

    // ------------------------- Tuning harness UI ---------------------------

    private fun buildTuningOverlay(): View {
        val container = FrameLayout(this)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xC0202020.toInt())
            setPadding(PAD, PAD, PAD, PAD)
            isClickable = true // keep panel touches away from the book
            visibility = View.GONE
        }

        val sliders = ArrayList<() -> Unit>() // slider -> UI refresh hooks

        fun addSlider(
            label: String,
            get: () -> Float,
            set: (Float) -> Unit,
            scale: Float,
        ) {
            val title = TextView(this).apply { setTextColor(Color.WHITE) }
            fun refresh() {
                title.text = "%s  %.2f".format(label, get())
            }
            val bar = SeekBar(this).apply {
                max = 100
                progress = (get() / scale * 100f).toInt().coerceIn(0, 100)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        set(value / 100f * scale)
                        refresh()
                        renderer.setPaperTuning(tuning)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) = Unit
                    override fun onStopTrackingTouch(sb: SeekBar) = Unit
                })
            }
            sliders += {
                refresh()
                bar.progress = (get() / scale * 100f).toInt().coerceIn(0, 100)
            }
            refresh()
            panel.addView(title)
            panel.addView(bar)
        }

        addSlider("stiffness", { tuning.stiffness }, { tuning = tuning.copy(stiffness = it) }, scale = 1f)
        addSlider("damping", { tuning.damping }, { tuning = tuning.copy(damping = it) }, scale = 8f)
        addSlider("air drag", { tuning.airDrag }, { tuning = tuning.copy(airDrag = it) }, scale = 30f)
        addSlider("show-through", { tuning.translucency }, { tuning = tuning.copy(translucency = it) }, scale = 1f)

        fun applyPreset(preset: PaperTuning) {
            tuning = preset
            renderer.setPaperTuning(tuning)
            sliders.forEach { it() }
        }

        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("diary" to DIARY, "booklet" to BOOKLET, "passport" to PASSPORT).forEach { (name, preset) ->
            presets.addView(
                Button(this).apply {
                    text = name
                    setOnClickListener { applyPreset(preset) }
                },
            )
        }
        panel.addView(presets)

        panel.addView(
            Button(this).apply {
                text = "export json"
                setOnClickListener { exportProfile() }
            },
        )

        val toggle = Button(this).apply {
            text = "TUNE"
            setOnClickListener {
                panel.visibility = if (panel.visibility == View.GONE) View.VISIBLE else View.GONE
            }
        }

        container.addView(
            panel,
            FrameLayout.LayoutParams(PANEL_WIDTH, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = TOGGLE_CLEARANCE
            },
        )
        container.addView(
            toggle,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.TOP or Gravity.END },
        )
        return container
    }

    /**
     * Writes the current tuning as profile JSON (docs/05 §6: tuned by hand,
     * committed as data) to app files and logcat for adb pull / copy-paste.
     */
    private fun exportProfile() {
        val json = """
            {
              "stiffness": ${tuning.stiffness},
              "damping": ${tuning.damping},
              "airDrag": ${tuning.airDrag},
              "translucency": ${tuning.translucency}
            }
        """.trimIndent()
        val file = File(filesDir, "paper-profile.json")
        file.writeText(json)
        Log.i("LeafTuning", "exported to ${file.absolutePath}\n$json")
    }

    private companion object {
        val DIARY = PaperTuning(stiffness = 0.12f, damping = 2.2f, airDrag = 18f, translucency = 0.42f)
        val BOOKLET = PaperTuning(stiffness = 0.5f, damping = 2.8f, airDrag = 12f, translucency = 0.3f)
        val PASSPORT = PaperTuning(stiffness = 0.92f, damping = 3.6f, airDrag = 6f, translucency = 0.04f)
        const val PAD = 24
        const val PANEL_WIDTH = 640
        const val TOGGLE_CLEARANCE = 140
    }
}
