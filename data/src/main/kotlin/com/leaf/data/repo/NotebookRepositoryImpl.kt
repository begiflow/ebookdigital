package com.leaf.data.repo

import com.leaf.data.db.NotebookDao
import com.leaf.data.db.NotebookRow
import com.leaf.data.db.PageRow
import com.leaf.data.db.SheetRow
import com.leaf.data.files.FileStore
import com.leaf.data.texture.TexturePipeline
import com.leaf.domain.model.Binding
import com.leaf.domain.model.CoverSet
import com.leaf.domain.model.CoverType
import com.leaf.domain.model.CropQuad
import com.leaf.domain.model.EditParams
import com.leaf.domain.model.GrainKind
import com.leaf.domain.model.ImageRef
import com.leaf.domain.model.NormPoint
import com.leaf.domain.model.Notebook
import com.leaf.domain.model.NotebookId
import com.leaf.domain.model.NotebookProfile
import com.leaf.domain.model.Page
import com.leaf.domain.model.PageId
import com.leaf.domain.model.PaperSpec
import com.leaf.domain.model.ProfileKind
import com.leaf.domain.model.Sheet
import com.leaf.domain.model.SheetId
import com.leaf.domain.repo.NotebookRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room + file store behind the domain interface (docs/02 §3). Edits update
 * the page row then regenerate derivatives from the untouched original.
 */
class NotebookRepositoryImpl(
    private val dao: NotebookDao,
    private val store: FileStore,
    private val pipeline: TexturePipeline,
    private val ioDispatcher: CoroutineDispatcher,
) : NotebookRepository {

    override fun shelf(): Flow<List<Notebook>> =
        dao.shelf().map { rows -> rows.map { assemble(it) } }

    override suspend fun notebook(id: NotebookId): Notebook? =
        dao.notebook(id.value)?.let { assemble(it) }

    override suspend fun create(notebook: Notebook) {
        dao.upsertNotebook(notebook.toRow())
        val pages = ArrayList<PageRow>()
        pages.add(notebook.cover.front.toRow(notebook.id.value, ROLE_COVER_FRONT))
        notebook.cover.back?.let { pages.add(it.toRow(notebook.id.value, ROLE_COVER_BACK)) }
        notebook.cover.insideFront?.let { pages.add(it.toRow(notebook.id.value, ROLE_INSIDE_FRONT)) }
        notebook.cover.insideBack?.let { pages.add(it.toRow(notebook.id.value, ROLE_INSIDE_BACK)) }
        for (sheet in notebook.sheets) {
            pages.add(sheet.front.toRow(notebook.id.value, ROLE_SHEET))
            pages.add(sheet.back.toRow(notebook.id.value, ROLE_SHEET))
        }
        dao.upsertPages(pages)
        dao.upsertSheets(
            notebook.sheets.map {
                SheetRow(
                    id = it.id.value,
                    notebookId = notebook.id.value,
                    sheetIndex = it.index,
                    frontPageId = it.front.id.value,
                    backPageId = it.back.id.value,
                )
            },
        )
    }

    override suspend fun rename(id: NotebookId, title: String) = dao.rename(id.value, title)

    override suspend fun delete(id: NotebookId) {
        val pages = dao.pages(id.value)
        dao.deleteNotebookDeep(id.value)
        withContext(ioDispatcher) {
            for (page in pages) store.deletePageArtifacts(page.id, includeOriginal = true)
        }
    }

    override suspend fun moveOnShelf(id: NotebookId, toPosition: Int) =
        dao.moveOnShelf(id.value, toPosition)

    override suspend fun updateEdits(pageId: PageId, edits: EditParams) {
        dao.updateEdits(
            pageId = pageId.value,
            cropQuad = edits.cropQuad?.serialize(),
            rotationDeg = edits.rotationDeg,
            brightness = edits.brightness,
            contrast = edits.contrast,
        )
        // Derivatives only — the original is read, never written (PRD §6).
        withContext(ioDispatcher) {
            pipeline.regenerate(pageId.value, edits)
        }
    }

    // ------------------------------ mapping --------------------------------

    private suspend fun assemble(row: NotebookRow): Notebook {
        val pagesById = dao.pages(row.id).associateBy { it.id }
        val sheets = dao.sheets(row.id).map { sheet ->
            Sheet(
                id = SheetId(sheet.id),
                index = sheet.sheetIndex,
                front = pagesById.getValue(sheet.frontPageId).toDomain(),
                back = pagesById.getValue(sheet.backPageId).toDomain(),
            )
        }
        fun cover(role: String): Page? =
            pagesById.values.firstOrNull { it.role == role }?.toDomain()
        return Notebook(
            id = NotebookId(row.id),
            title = row.title,
            profile = NotebookProfile(
                kind = ProfileKind.valueOf(row.kind),
                binding = Binding.valueOf(row.binding),
                paper = PaperSpec(
                    weightGsm = row.paperWeightGsm,
                    stiffness = row.paperStiffness,
                    translucency = row.paperTranslucency,
                    grain = GrainKind.valueOf(row.grain),
                    tintArgb = row.paperTintArgb,
                ),
                coverType = CoverType.valueOf(row.coverType),
                pageAspectRatio = row.pageAspectRatio,
            ),
            cover = CoverSet(
                front = requireNotNull(cover(ROLE_COVER_FRONT)) { "notebook ${row.id} lost its front cover" },
                back = cover(ROLE_COVER_BACK),
                insideFront = cover(ROLE_INSIDE_FRONT),
                insideBack = cover(ROLE_INSIDE_BACK),
            ),
            sheets = sheets,
            createdAtEpochMs = row.createdAtEpochMs,
            shelfPosition = row.shelfPosition,
        )
    }

    private fun Notebook.toRow() = NotebookRow(
        id = id.value,
        title = title,
        kind = profile.kind.name,
        binding = profile.binding.name,
        coverType = profile.coverType.name,
        grain = profile.paper.grain.name,
        paperWeightGsm = profile.paper.weightGsm,
        paperStiffness = profile.paper.stiffness,
        paperTranslucency = profile.paper.translucency,
        paperTintArgb = profile.paper.tintArgb,
        pageAspectRatio = profile.pageAspectRatio,
        createdAtEpochMs = createdAtEpochMs,
        shelfPosition = shelfPosition,
    )

    private fun Page.toRow(notebookId: String, role: String) = PageRow(
        id = id.value,
        notebookId = notebookId,
        role = role,
        originalPath = original.relativePath,
        sha256 = original.sha256,
        capturedAtEpochMs = capturedAtEpochMs,
        isGeneratedBlank = isGeneratedBlank,
        cropQuad = edits.cropQuad?.serialize(),
        rotationDeg = edits.rotationDeg,
        brightness = edits.brightness,
        contrast = edits.contrast,
    )

    private fun PageRow.toDomain() = Page(
        id = PageId(id),
        original = ImageRef(relativePath = originalPath, sha256 = sha256),
        edits = EditParams(
            cropQuad = cropQuad?.deserializeQuad(),
            rotationDeg = rotationDeg,
            brightness = brightness,
            contrast = contrast,
        ),
        capturedAtEpochMs = capturedAtEpochMs,
        isGeneratedBlank = isGeneratedBlank,
    )

    private companion object {
        const val ROLE_SHEET = "SHEET"
        const val ROLE_COVER_FRONT = "COVER_FRONT"
        const val ROLE_COVER_BACK = "COVER_BACK"
        const val ROLE_INSIDE_FRONT = "INSIDE_FRONT"
        const val ROLE_INSIDE_BACK = "INSIDE_BACK"

        fun CropQuad.serialize(): String = listOf(
            topLeft.x, topLeft.y, topRight.x, topRight.y,
            bottomRight.x, bottomRight.y, bottomLeft.x, bottomLeft.y,
        ).joinToString(",")

        fun String.deserializeQuad(): CropQuad? {
            val v = split(",").mapNotNull { it.toFloatOrNull() }
            if (v.size != 8) return null
            return CropQuad(
                topLeft = NormPoint(v[0], v[1]),
                topRight = NormPoint(v[2], v[3]),
                bottomRight = NormPoint(v[4], v[5]),
                bottomLeft = NormPoint(v[6], v[7]),
            )
        }
    }
}
