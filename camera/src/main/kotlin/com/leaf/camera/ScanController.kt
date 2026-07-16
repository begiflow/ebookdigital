package com.leaf.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.leaf.camera.detect.Dewarper
import com.leaf.camera.detect.OpenCvQuadDetector
import com.leaf.camera.scan.Quad
import com.leaf.camera.scan.QuadStabilityTracker
import com.leaf.camera.scan.ScanSession
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Binds the capture pipeline (docs/01-PRD.md §5.4): CameraX preview +
 * analysis stream through [OpenCvQuadDetector] and [QuadStabilityTracker]
 * (auto-capture), ImageCapture into the staging dir, dewarp on save
 * (original preserved as `-orig`), all narrated to Compose via [state].
 */
class ScanController(
    private val context: Context,
    private val stagingDir: File,
) {
    data class UiState(
        val stage: ScanSession.Stage = ScanSession.Stage.Cover(ScanSession.CoverSlot.FRONT),
        val overlay: Quad? = null,
        val stable: Boolean = false,
        val pageCount: Int = 0,
        val captures: List<ScanSession.Capture> = emptyList(),
        /** Non-null while re-shooting a page from review. */
        val retakingIndex: Int? = null,
        val busy: Boolean = false,
    )

    val session = ScanSession()

    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState

    private val detector = OpenCvQuadDetector()
    private val tracker = QuadStabilityTracker()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private var imageCapture: ImageCapture? = null
    private var nextId = 1L
    private var retakingIndex: Int? = null

    /** Quad frozen at shutter time, keyed by capture id (for dewarp). */
    private val quadAtCapture = HashMap<Long, Quad>()

    fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        stagingDir.mkdirs()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            imageCapture = capture

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
                capture,
            )
        }, context.mainExecutor)
    }

    fun release() {
        analysisExecutor.shutdown()
        ioExecutor.shutdown()
    }

    // ------------------------------ scanning -------------------------------

    private fun analyze(proxy: ImageProxy) {
        try {
            if (mutableState.value.busy || session.stage == ScanSession.Stage.Review) return
            val rotation = proxy.imageInfo.rotationDegrees
            var bitmap = proxy.toBitmap()
            if (rotation != 0) bitmap = rotate(bitmap, rotation.toFloat())
            val fire = tracker.feed(detector.detect(bitmap))
            publish()
            if (fire) shutter()
        } finally {
            proxy.close()
        }
    }

    /** Manual shutter; also the auto-capture entry. */
    fun shutter() {
        val capture = imageCapture ?: return
        if (mutableState.value.busy) return
        val id = nextId++
        tracker.overlay?.let { quadAtCapture[id] = it }
        tracker.onCaptured()
        mutableState.value = mutableState.value.copy(busy = true)

        val original = File(stagingDir, "cap-$id-orig.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(original).build(),
            ioExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    dewarpAndRecord(id, original)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "capture failed", exception)
                    mutableState.value = mutableState.value.copy(busy = false)
                }
            },
        )
    }

    /**
     * Dewarp on the IO executor (docs/01 §5.4: perspective correction on
     * every capture; the original frame is preserved untouched).
     */
    private fun dewarpAndRecord(id: Long, original: File) {
        try {
            val quad = quadAtCapture[id]
            if (quad != null) {
                val source = BitmapFactory.decodeFile(original.absolutePath)
                if (source != null) {
                    val flat = Dewarper.dewarp(source, quad)
                    FileOutputStream(File(stagingDir, "cap-$id.jpg")).use {
                        flat.compress(Bitmap.CompressFormat.JPEG, 92, it)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "dewarp failed; original kept", e)
        }
        val retake = retakingIndex
        if (retake != null) {
            session.retakePage(retake, id)
            retakingIndex = null
            session.finishScanning() // straight back to review
        } else {
            session.capture(id)
        }
        mutableState.value = mutableState.value.copy(busy = false)
        publish()
    }

    fun markBlank() {
        session.markBlank()
        publish()
    }

    fun skipCover() {
        session.skip()
        publish()
    }

    fun finishScanning() {
        session.finishScanning()
        publish()
    }

    fun resumeScanning() {
        session.resumeScanning()
        tracker.reset()
        publish()
    }

    // ------------------------------- review --------------------------------

    fun retakePage(index: Int) {
        retakingIndex = index
        session.resumeScanning()
        tracker.reset()
        publish()
    }

    fun rotatePage(index: Int) {
        session.rotatePage(index)
        publish()
    }

    fun deletePage(index: Int) {
        session.deletePage(index)
        publish()
    }

    fun movePage(from: Int, to: Int) {
        session.movePage(from, to)
        publish()
    }

    fun insertBlank(at: Int) {
        session.insertBlank(at)
        publish()
    }

    /** Dewarped image for a capture (falls back to the original). */
    fun imageFile(capture: ScanSession.Capture): File? {
        if (capture.blank) return null
        val flat = File(stagingDir, "cap-${capture.id}.jpg")
        if (flat.exists()) return flat
        val orig = File(stagingDir, "cap-${capture.id}-orig.jpg")
        return if (orig.exists()) orig else null
    }

    private fun publish() {
        val retake = retakingIndex
        mutableState.value = mutableState.value.copy(
            stage = session.stage,
            overlay = tracker.overlay,
            stable = tracker.isStable,
            pageCount = session.pageCount,
            captures = session.pages,
            retakingIndex = retake,
        )
    }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private companion object {
        const val TAG = "LeafScan"
    }
}
