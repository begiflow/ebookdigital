package com.leaf.domain.repo

import com.leaf.domain.model.EditParams
import com.leaf.domain.model.Notebook
import com.leaf.domain.model.NotebookId
import com.leaf.domain.model.PageId
import com.leaf.domain.model.Sheet
import kotlinx.coroutines.flow.Flow

/**
 * The notebook store (docs/02-ARCHITECTURE.md §2–3). Implemented in :data
 * (Room + file store); features and presentation reach storage only through
 * this interface. Image bytes never cross it — pages carry [ImageRef]s into
 * the app-private file store.
 */
interface NotebookRepository {

    fun shelf(): Flow<List<Notebook>>

    suspend fun notebook(id: NotebookId): Notebook?

    suspend fun create(notebook: Notebook)

    suspend fun rename(id: NotebookId, title: String)

    suspend fun delete(id: NotebookId)

    suspend fun moveOnShelf(id: NotebookId, toPosition: Int)

    /**
     * Replaces a page's non-destructive edits (docs/01 §5.5). Derivatives
     * regenerate from the untouched original; callers observe [shelf] or
     * reload to pick up new texture refs.
     */
    suspend fun updateEdits(pageId: PageId, edits: EditParams)

    /**
     * Replaces a notebook's sheet list (editor insert/delete/reorder,
     * docs/01 §5.5). Sheet order is the single source of page order.
     */
    suspend fun updateSheets(id: NotebookId, sheets: List<Sheet>)
}
