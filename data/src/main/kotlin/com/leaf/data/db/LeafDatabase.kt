package com.leaf.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Room schema (docs/02-ARCHITECTURE.md §3): notebooks, sheets, pages.
 * Images are never stored here — pages carry file-store paths + content
 * hashes only. Edit parameters live inline on the page row (small, always
 * loaded together).
 */
@Entity(tableName = "notebooks")
data class NotebookRow(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val binding: String,
    val coverType: String,
    val grain: String,
    val paperWeightGsm: Int,
    val paperStiffness: Float,
    val paperTranslucency: Float,
    val paperTintArgb: Int,
    val pageAspectRatio: Float,
    val createdAtEpochMs: Long,
    val shelfPosition: Int,
)

@Entity(tableName = "sheets")
data class SheetRow(
    @PrimaryKey val id: String,
    val notebookId: String,
    val sheetIndex: Int,
    val frontPageId: String,
    val backPageId: String,
)

/**
 * One captured face, [role] SHEET/COVER_FRONT/COVER_BACK/INSIDE_FRONT/
 * INSIDE_BACK. Crop quad serialized as 8 comma-joined floats (tiny, schema
 * stays flat).
 */
@Entity(tableName = "pages")
data class PageRow(
    @PrimaryKey val id: String,
    val notebookId: String,
    val role: String,
    val originalPath: String,
    val sha256: String,
    val capturedAtEpochMs: Long,
    val isGeneratedBlank: Boolean,
    val cropQuad: String?,
    val rotationDeg: Int,
    val brightness: Float,
    val contrast: Float,
)

@Dao
interface NotebookDao {

    @Query("SELECT * FROM notebooks ORDER BY shelfPosition")
    fun shelf(): Flow<List<NotebookRow>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun notebook(id: String): NotebookRow?

    @Query("SELECT * FROM sheets WHERE notebookId = :notebookId ORDER BY sheetIndex")
    suspend fun sheets(notebookId: String): List<SheetRow>

    @Query("SELECT * FROM pages WHERE notebookId = :notebookId")
    suspend fun pages(notebookId: String): List<PageRow>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun page(pageId: String): PageRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotebook(row: NotebookRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSheets(rows: List<SheetRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPages(rows: List<PageRow>)

    @Query("UPDATE notebooks SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE notebooks SET shelfPosition = :position WHERE id = :id")
    suspend fun moveOnShelf(id: String, position: Int)

    @Query(
        "UPDATE pages SET cropQuad = :cropQuad, rotationDeg = :rotationDeg, " +
            "brightness = :brightness, contrast = :contrast WHERE id = :pageId",
    )
    suspend fun updateEdits(
        pageId: String,
        cropQuad: String?,
        rotationDeg: Int,
        brightness: Float,
        contrast: Float,
    )

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteNotebook(id: String)

    @Query("DELETE FROM sheets WHERE notebookId = :id")
    suspend fun deleteSheets(id: String)

    @Query("DELETE FROM pages WHERE notebookId = :id")
    suspend fun deletePages(id: String)

    @Transaction
    suspend fun deleteNotebookDeep(id: String) {
        deleteSheets(id)
        deletePages(id)
        deleteNotebook(id)
    }
}

@Database(
    entities = [NotebookRow::class, SheetRow::class, PageRow::class],
    version = 1,
    exportSchema = false,
)
abstract class LeafDatabase : RoomDatabase() {
    abstract fun notebooks(): NotebookDao
}
