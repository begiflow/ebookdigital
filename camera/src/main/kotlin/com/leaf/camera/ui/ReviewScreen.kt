package com.leaf.camera.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.leaf.camera.ScanController
import com.leaf.camera.scan.ScanSession

/**
 * Review (docs/01-PRD.md §5.4): the captured reading order as a list —
 * retake, rotate, reorder, delete, insert blank — plus "scan more" and done.
 * Non-destructive: every action edits the session's bookkeeping only.
 */
@Composable
fun ReviewScreen(
    controller: ScanController,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text("Review — ${state.pageCount} pages, ${(state.pageCount + 1) / 2} sheets")

        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(state.captures) { index, capture ->
                ReviewRow(
                    index = index,
                    capture = capture,
                    controller = controller,
                    last = index == state.captures.size - 1,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = controller::resumeScanning) { Text("Scan more") }
            Button(onClick = { controller.insertBlank(state.pageCount) }) { Text("+ blank") }
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun ReviewRow(
    index: Int,
    capture: ScanSession.Capture,
    controller: ScanController,
    last: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp)) {
            val file = controller.imageFile(capture)
            val thumb = remember(capture.id, capture.rotationDeg) {
                file?.let {
                    BitmapFactory.decodeFile(
                        it.absolutePath,
                        BitmapFactory.Options().apply { inSampleSize = 8 },
                    )
                }
            }
            if (thumb != null) {
                Image(bitmap = thumb.asImageBitmap(), contentDescription = "page ${index + 1}")
            } else {
                Text(if (capture.blank) "blank" else "…", Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text("Page ${index + 1}" + if (capture.blank) " (blank)" else "")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { controller.movePage(index, index - 1) }, enabled = index > 0) {
                    Text("↑")
                }
                Button(onClick = { controller.movePage(index, index + 1) }, enabled = !last) {
                    Text("↓")
                }
                Button(onClick = { controller.rotatePage(index) }) { Text("⟳") }
                if (!capture.blank) {
                    Button(onClick = { controller.retakePage(index) }) { Text("Retake") }
                }
                Button(onClick = { controller.deletePage(index) }) { Text("✕") }
            }
        }
    }
}
