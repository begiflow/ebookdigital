package com.leaf.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.leaf.data.files.FileStore
import com.leaf.data.texture.TexturePipeline
import com.leaf.designsystem.LeafTheme
import com.leaf.domain.model.EditParams
import com.leaf.domain.model.ImageRef
import com.leaf.domain.model.Notebook
import com.leaf.domain.model.NotebookId
import com.leaf.domain.model.Page
import com.leaf.domain.model.PageId
import com.leaf.domain.model.Sheet
import com.leaf.domain.model.SheetId
import com.leaf.domain.repo.NotebookRepository
import com.leaf.editor.EditorScreen
import com.leaf.editor.OrganizerPage
import com.leaf.editor.PageOrganizerScreen
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editor host (M15, docs/01-PRD.md §5.5): organizer (insert / delete /
 * reorder) over the notebook's reading order, per-page non-destructive
 * editor, and page sharing (the edited derivative, never the original).
 * Structural changes re-pair sheets 2k/2k+1 and persist via the repository.
 */
@AndroidEntryPoint
class EditPagesActivity : ComponentActivity() {

    @Inject lateinit var repository: NotebookRepository

    @Inject lateinit var store: FileStore

    @Inject lateinit var pipeline: TexturePipeline

    private var notebook: Notebook? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID) ?: run {
            finish()
            return
        }

        setContent {
            LeafTheme {
                var pages by androidx.compose.runtime.remember { mutableStateOf(listOf<Page>()) }
                var editing by androidx.compose.runtime.remember { mutableStateOf<Page?>(null) }

                fun persist(newPages: List<Page>) {
                    pages = newPages
                    val book = notebook ?: return
                    lifecycleScope.launch {
                        repository.updateSheets(book.id, pairSheets(newPages))
                    }
                }

                androidx.compose.runtime.LaunchedEffect(id) {
                    notebook = repository.notebook(NotebookId(id))
                    pages = notebook?.sheets
                        ?.sortedBy { it.index }
                        ?.flatMap { listOf(it.front, it.back) }
                        ?: emptyList()
                }

                val current = editing
                if (current == null) {
                    PageOrganizerScreen(
                        pages = pages.map {
                            OrganizerPage(
                                pageId = it.id.value,
                                thumbPath = store.thumbFile(it.id.value)
                                    .takeIf { f -> f.exists() }?.absolutePath,
                                isBlank = it.isGeneratedBlank,
                            )
                        },
                        onEdit = { row -> editing = pages.firstOrNull { it.id.value == row.pageId } },
                        onMove = { from, to ->
                            val list = pages.toMutableList()
                            val p = list.removeAt(from)
                            list.add(to.coerceIn(0, list.size), p)
                            persist(list)
                        },
                        onDelete = { index -> persist(pages.toMutableList().also { it.removeAt(index) }) },
                        onInsertBlank = { at ->
                            lifecycleScope.launch {
                                val blank = withContext(Dispatchers.IO) { newBlankPage() }
                                persist(
                                    pages.toMutableList().also {
                                        it.add(at.coerceIn(0, it.size), blank)
                                    },
                                )
                            }
                        },
                        onClose = { finish() },
                    )
                } else {
                    val original = BitmapFactory.decodeFile(
                        store.originalFile(current.id.value).absolutePath,
                    )
                    if (original == null) {
                        editing = null
                    } else {
                        EditorScreen(
                            original = original,
                            initial = current.edits,
                            onSave = { params ->
                                lifecycleScope.launch {
                                    repository.updateEdits(current.id, params)
                                    pages = pages.map {
                                        if (it.id == current.id) it.copy(edits = params) else it
                                    }
                                    editing = null
                                }
                            },
                            onShare = { params -> sharePage(current.id.value, params) },
                            onExportOriginal = { shareOriginal(current.id.value) },
                            onClose = { editing = null },
                        )
                    }
                }
            }
        }
    }

    /** Re-pairs the flat reading order into sheets; odd tails pad blank. */
    private suspend fun pairSheets(pages: List<Page>): List<Sheet> {
        val padded = if (pages.size % 2 == 1) {
            pages + withContext(Dispatchers.IO) { newBlankPage() }
        } else {
            pages
        }
        return padded.chunked(2).mapIndexed { index, (front, back) ->
            Sheet(SheetId(UUID.randomUUID().toString()), index, front, back)
        }
    }

    private fun newBlankPage(): Page {
        val pageId = UUID.randomUUID().toString()
        val bitmap = android.graphics.Bitmap.createBitmap(512, 726, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).drawColor(0xFFF2ECDC.toInt())
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        val hash = store.writeOriginal(pageId, out.toByteArray())
        pipeline.regenerate(pageId, EditParams.NONE)
        return Page(
            id = PageId(pageId),
            original = ImageRef("originals/$pageId.jpg", hash),
            edits = EditParams.NONE,
            capturedAtEpochMs = System.currentTimeMillis(),
            isGeneratedBlank = true,
        )
    }

    /** Exports the *edited derivative* and hands it to the share sheet. */
    private fun sharePage(pageId: String, params: EditParams) {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                pipeline.exportEditedJpeg(pageId, params)
            } ?: return@launch
            val dir = File(cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "page-$pageId.jpg")
            withContext(Dispatchers.IO) { file.writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(
                this@EditPagesActivity,
                "$packageName.fileprovider",
                file,
            )
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("image/jpeg")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "Share page",
                ),
            )
        }
    }

    /**
     * The storage-integrity escape hatch (M16, docs/01-PRD.md §6): shares
     * the untouched original bytes — the only copy path off-device.
     */
    private fun shareOriginal(pageId: String) {
        lifecycleScope.launch {
            val source = store.originalFile(pageId)
            if (!source.exists()) return@launch
            val dir = File(cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "original-$pageId.jpg")
            withContext(Dispatchers.IO) { source.copyTo(file, overwrite = true) }
            val uri = FileProvider.getUriForFile(
                this@EditPagesActivity,
                "$packageName.fileprovider",
                file,
            )
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("image/jpeg")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "Export original",
                ),
            )
        }
    }

    companion object {
        private const val EXTRA_ID = "notebook_id"

        fun open(context: Context, notebookId: String) {
            context.startActivity(
                Intent(context, EditPagesActivity::class.java).putExtra(EXTRA_ID, notebookId),
            )
        }
    }
}
