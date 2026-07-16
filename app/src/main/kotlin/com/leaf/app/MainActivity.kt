package com.leaf.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.leaf.bookshelf.BookshelfScreen
import com.leaf.bookshelf.ShelfBook
import com.leaf.camera.ScanActivity
import com.leaf.data.files.FileStore
import com.leaf.designsystem.LeafTheme
import com.leaf.domain.model.NotebookId
import com.leaf.domain.repo.NotebookRepository
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Home = the shelf (docs/01-PRD.md §5.1). "+" runs the capture flow
 * (ScanActivity); a finished session is imported on return. Tapping a book
 * hands off into the engine scene (NotebookActivity).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: NotebookRepository

    @Inject lateinit var store: FileStore

    @Inject lateinit var importer: NotebookImporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LeafTheme {
                val notebooks by repository.shelf().collectAsState(initial = emptyList())
                BookshelfScreen(
                    books = notebooks.map {
                        ShelfBook(
                            id = it.id.value,
                            title = it.title,
                            coverThumbPath = store.thumbFile(it.cover.front.id.value)
                                .takeIf { f -> f.exists() }?.absolutePath,
                            sheetCount = it.sheets.size,
                        )
                    },
                    onOpen = { book -> NotebookActivity.open(this, book.id) },
                    onCreate = { startActivity(Intent(this, ScanActivity::class.java)) },
                    onRename = { book, title ->
                        lifecycleScope.launch { repository.rename(NotebookId(book.id), title) }
                    },
                    onDelete = { book ->
                        lifecycleScope.launch { repository.delete(NotebookId(book.id)) }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A finished scan session waits in staging; fold it into the shelf.
        lifecycleScope.launch {
            importer.importPending(File(filesDir, "capture-staging"))
        }
    }
}
