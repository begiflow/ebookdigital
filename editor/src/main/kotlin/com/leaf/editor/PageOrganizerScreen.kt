package com.leaf.editor

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

/** One page row in the organizer — presentation supplies thumbs + ids. */
data class OrganizerPage(
    val pageId: String,
    val thumbPath: String?,
    val isBlank: Boolean,
)

/**
 * Notebook page organizer (docs/01-PRD.md §5.5): reading-order list with
 * insert / delete / reorder and per-page edit entry. Structural changes
 * are handed back as the full new order; the host re-pairs sheets
 * (2k / 2k+1) and persists.
 */
@Composable
fun PageOrganizerScreen(
    pages: List<OrganizerPage>,
    onEdit: (OrganizerPage) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (Int) -> Unit,
    onInsertBlank: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text("Pages — ${pages.size} (${(pages.size + 1) / 2} sheets)")
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(pages) { index, page ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(64.dp)) {
                        val thumb = remember(page.pageId) {
                            page.thumbPath?.let { BitmapFactory.decodeFile(it) }
                        }
                        if (thumb != null) {
                            Image(bitmap = thumb.asImageBitmap(), contentDescription = null)
                        } else {
                            Text(
                                if (page.isBlank) "blank" else "…",
                                Modifier.align(Alignment.Center),
                            )
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Page ${index + 1}")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { onMove(index, index - 1) },
                                enabled = index > 0,
                            ) { Text("↑") }
                            TextButton(
                                onClick = { onMove(index, index + 1) },
                                enabled = index < pages.size - 1,
                            ) { Text("↓") }
                            TextButton(onClick = { onEdit(page) }) { Text("Edit") }
                            TextButton(onClick = { onInsertBlank(index + 1) }) { Text("+blank") }
                            TextButton(onClick = { onDelete(index) }) { Text("✕") }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onClose) { Text("Done") }
        }
    }
}
