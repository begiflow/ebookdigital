package com.leaf.bookshelf

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.leaf.designsystem.LeafColors

/** Shelf item UI model — presentation maps domain notebooks into this. */
data class ShelfBook(
    val id: String,
    val title: String,
    val coverThumbPath: String?,
    val sheetCount: Int,
)

/**
 * The shelf home (docs/01-PRD.md §5.1): notebooks stand on wood shelves as
 * objects — real cover art, spine width tracking true sheet count. Tap
 * opens (the activity handoff continues into the engine scene); the ⋯ menu
 * carries manage actions.
 */
@Composable
fun BookshelfScreen(
    books: List<ShelfBook>,
    onOpen: (ShelfBook) -> Unit,
    onCreate: () -> Unit,
    onRename: (ShelfBook, String) -> Unit,
    onDelete: (ShelfBook) -> Unit,
    onEditPages: (ShelfBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(LeafColors.PaperWhite)) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(books.chunked(3)) { row ->
                ShelfRow(row, onOpen, onRename, onDelete, onEditPages)
            }
        }
        if (books.isEmpty()) {
            Text(
                "Scan your first notebook",
                Modifier.align(Alignment.Center),
                color = LeafColors.InkFaded,
            )
        }
        FloatingActionButton(
            onClick = onCreate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) { Text("+") }
    }
}

@Composable
private fun ShelfRow(
    row: List<ShelfBook>,
    onOpen: (ShelfBook) -> Unit,
    onRename: (ShelfBook, String) -> Unit,
    onDelete: (ShelfBook) -> Unit,
    onEditPages: (ShelfBook) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(190.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (book in row) {
                BookOnShelf(book, onOpen, onRename, onDelete, onEditPages)
            }
        }
        // The shelf board the row stands on.
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(LeafColors.ShelfWood),
        )
    }
}

@Composable
private fun BookOnShelf(
    book: ShelfBook,
    onOpen: (ShelfBook) -> Unit,
    onRename: (ShelfBook, String) -> Unit,
    onDelete: (ShelfBook) -> Unit,
    onEditPages: (ShelfBook) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    // True relative thickness: spine width grows with sheet count.
    val width = (64 + book.sheetCount).coerceAtMost(120).dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(width)
                .height(160.dp)
                .background(LeafColors.LeatherBrown)
                .clickable { onOpen(book) },
        ) {
            val thumb = remember(book.coverThumbPath) {
                book.coverThumbPath?.let { BitmapFactory.decodeFile(it) }
            }
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                book.title,
                style = MaterialTheme.typography.labelMedium,
                color = LeafColors.Ink,
                maxLines = 1,
            )
            Box {
                Text(
                    "⋯",
                    Modifier.padding(horizontal = 6.dp).clickable { menuOpen = true },
                    color = LeafColors.InkFaded,
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuOpen = false
                            renaming = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit pages") },
                        onClick = {
                            menuOpen = false
                            onEditPages(book)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuOpen = false
                            onDelete(book)
                        },
                    )
                }
            }
        }
    }

    if (renaming) {
        var title by remember { mutableStateOf(book.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename notebook") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }) },
            confirmButton = {
                Button(onClick = {
                    renaming = false
                    onRename(book, title)
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}
