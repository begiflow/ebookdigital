package com.leaf.camera.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.leaf.camera.ScanController
import com.leaf.camera.scan.ScanSession

/**
 * The viewfinder (docs/01-PRD.md §5.4): live preview, detected-quad overlay
 * (amber while settling, green when stable), stage prompt, page counter, and
 * the always-available manual shutter + mark-blank + skip controls.
 */
@Composable
fun ScanScreen(
    controller: ScanController,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).also { controller.bind(lifecycleOwner, it) }
            },
        )

        // Quad overlay in normalized coords scaled to the canvas.
        Canvas(Modifier.fillMaxSize()) {
            val quad = state.overlay ?: return@Canvas
            val color = if (state.stable) Color(0xFF57D06B) else Color(0xFFF2B84B)
            val w = size.width
            val h = size.height
            for (i in 0 until 4) {
                val (ax, ay) = quad.corner(i)
                val (bx, by) = quad.corner((i + 1) % 4)
                drawLine(
                    color = color,
                    start = Offset(ax * w, ay * h),
                    end = Offset(bx * w, by * h),
                    strokeWidth = 6f,
                )
            }
        }

        Text(
            text = stagePrompt(state),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(24.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val stage = state.stage
            if (stage is ScanSession.Stage.Cover && stage.slot != ScanSession.CoverSlot.FRONT) {
                Button(onClick = controller::skipCover) { Text("Skip") }
            }
            Button(onClick = controller::shutter, enabled = !state.busy) { Text("Capture") }
            if (stage is ScanSession.Stage.Pages) {
                Button(onClick = controller::markBlank) { Text("Mark blank") }
                if (state.pageCount > 0 && state.retakingIndex == null) {
                    Button(onClick = controller::finishScanning) { Text("Review") }
                }
            }
        }
    }
}

private fun stagePrompt(state: ScanController.UiState): String {
    val retaking = state.retakingIndex
    if (retaking != null) return "Retake page ${retaking + 1}"
    return when (val stage = state.stage) {
        is ScanSession.Stage.Cover -> when (stage.slot) {
            ScanSession.CoverSlot.FRONT -> "Front cover"
            ScanSession.CoverSlot.BACK -> "Back cover (skippable)"
            ScanSession.CoverSlot.INSIDE_FRONT -> "Inside front cover (skippable)"
            ScanSession.CoverSlot.INSIDE_BACK -> "Inside back cover (skippable)"
        }
        is ScanSession.Stage.Pages -> "Page ${stage.nextPageNumber} — ${state.pageCount} captured"
        ScanSession.Stage.Review -> "Review"
    }
}
