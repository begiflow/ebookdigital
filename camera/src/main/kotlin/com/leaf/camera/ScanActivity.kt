package com.leaf.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.leaf.camera.scan.ScanSession
import com.leaf.camera.ui.ReviewScreen
import com.leaf.camera.ui.ScanScreen
import java.io.File
import org.opencv.android.OpenCVLoader

/**
 * Standalone capture entry (M12): create-notebook flow's scanning stage —
 * front cover → prompts → continuous reading-order pages → review
 * (docs/01-PRD.md §5.4). The M13/M14 app flow embeds the same screens; this
 * activity exists so capture is demo-able and hallway-testable on its own.
 */
class ScanActivity : ComponentActivity() {

    private lateinit var controller: ScanController

    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) start() else finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OpenCVLoader.initLocal()
        controller = ScanController(
            context = applicationContext,
            stagingDir = File(filesDir, "capture-staging"),
        )
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            start()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun start() {
        setContent {
            val state by controller.state.collectAsState()
            if (state.stage == ScanSession.Stage.Review) {
                ReviewScreen(
                    controller = controller,
                    onDone = {
                        controller.writeManifest()
                        setResult(RESULT_OK)
                        finish()
                    },
                )
            } else {
                ScanScreen(controller = controller, lifecycleOwner = this)
            }
        }
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}
