package com.leaf.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import com.leaf.domain.model.EditParams

/**
 * Non-destructive page editor (docs/01-PRD.md §5.5): live brightness /
 * contrast preview via a color matrix, draggable crop corners, quarter-turn
 * rotation. Nothing is applied to pixels here — Save hands the [EditParams]
 * back and the pipeline regenerates derivatives from the original.
 */
@Composable
fun EditorScreen(
    original: Bitmap,
    initial: EditParams,
    onSave: (EditParams) -> Unit,
    onShare: (EditParams) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = remember { EditDraft(initial) }
    // Recomposition tick — EditDraft is deliberately plain (JVM-tested).
    var revision by remember { mutableIntStateOf(0) }
    fun touch() {
        revision++
    }
    @Suppress("UNUSED_EXPRESSION")
    revision

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { canvasSize = it },
        ) {
            val params = draft.params
            Image(
                bitmap = original.asImageBitmap(),
                contentDescription = "page",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(
                    brightnessContrastMatrix(params.brightness, params.contrast),
                ),
            )
            // Crop overlay: quad outline + corner handles.
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var corner = -1
                        detectDragGestures(
                            onDragStart = { start ->
                                corner = nearestCorner(draft.params, start, canvasSize)
                            },
                            onDrag = { change, _ ->
                                if (corner >= 0 && canvasSize.width > 0) {
                                    draft.setCropCorner(
                                        corner,
                                        change.position.x / canvasSize.width,
                                        change.position.y / canvasSize.height,
                                    )
                                    touch()
                                }
                            },
                        )
                    },
            ) {
                val quad = draft.params.cropQuad ?: EditDraft.FULL_PAGE
                val pts = listOf(
                    quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft,
                ).map { Offset(it.x * size.width, it.y * size.height) }
                for (i in 0 until 4) {
                    drawLine(
                        color = Color(0xFFF2B84B),
                        start = pts[i],
                        end = pts[(i + 1) % 4],
                        strokeWidth = 4f,
                    )
                    drawCircle(color = Color(0xFFF2B84B), radius = 18f, center = pts[i])
                }
            }
        }

        Text("Brightness")
        Slider(
            value = draft.params.brightness,
            onValueChange = {
                draft.setBrightness(it)
                touch()
            },
            valueRange = -1f..1f,
        )
        Text("Contrast")
        Slider(
            value = draft.params.contrast,
            onValueChange = {
                draft.setContrast(it)
                touch()
            },
            valueRange = -1f..1f,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = {
                draft.rotateCw()
                touch()
            }) { Text("Rotate") }
            TextButton(onClick = {
                draft.clearCrop()
                touch()
            }) { Text("Uncrop") }
            TextButton(onClick = {
                draft.reset()
                touch()
            }) { Text("Reset") }
            Button(onClick = { onShare(draft.params) }) { Text("Share") }
            Button(onClick = { onSave(draft.params) }) { Text("Save") }
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}

private fun brightnessContrastMatrix(brightness: Float, contrast: Float): ColorMatrix {
    val scale = 1f + contrast
    val translate = brightness * 128f
    return ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

private fun nearestCorner(params: EditParams, at: Offset, size: IntSize): Int {
    if (size.width == 0 || size.height == 0) return -1
    val quad = params.cropQuad ?: EditDraft.FULL_PAGE
    val corners = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
    var best = -1
    var bestDist = Float.MAX_VALUE
    for ((i, c) in corners.withIndex()) {
        val dx = c.x * size.width - at.x
        val dy = c.y * size.height - at.y
        val d = dx * dx + dy * dy
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    return best
}
