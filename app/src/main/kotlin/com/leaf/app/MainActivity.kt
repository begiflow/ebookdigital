package com.leaf.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
                UninstallWarningOnFirstRun()
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
                    onEditPages = { book -> EditPagesActivity.open(this, book.id) },
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

    /**
     * Storage-integrity notice (M16, docs/01-PRD.md §6): originals live only
     * on this device; uninstalling deletes them. Per-page "Export original"
     * in the editor is the escape hatch. Shown once.
     */
    @androidx.compose.runtime.Composable
    private fun UninstallWarningOnFirstRun() {
        val prefs = getSharedPreferences("leaf", MODE_PRIVATE)
        var show by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(!prefs.getBoolean("uninstall_warned", false))
        }
        if (show) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                title = { androidx.compose.material3.Text("Your notebooks live here") },
                text = {
                    androidx.compose.material3.Text(
                        "Scanned originals are stored only on this device — " +
                            "uninstalling LEAF deletes them. Use \"Export original\" " +
                            "on any page to keep a copy elsewhere.",
                    )
                },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = {
                        prefs.edit().putBoolean("uninstall_warned", true).apply()
                        show = false
                    }) { androidx.compose.material3.Text("Understood") }
                },
            )
        }
    }
}
