package com.leaf.app

import android.graphics.Bitmap
import android.graphics.Canvas
import com.leaf.data.files.FileStore
import com.leaf.data.texture.TexturePipeline
import com.leaf.domain.model.Binding
import com.leaf.domain.model.CoverSet
import com.leaf.domain.model.CoverType
import com.leaf.domain.model.EditParams
import com.leaf.domain.model.GrainKind
import com.leaf.domain.model.ImageRef
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a finished capture-staging session (camera's line manifest + JPEGs)
 * into a stored notebook: originals write-once into the file store,
 * derivatives generated, rows created, staging cleared. Sheets pair pages
 * 2k / 2k+1; an odd tail and blank marks get generated blank backs
 * (docs/01-PRD.md locked decisions).
 */
@Singleton
class NotebookImporter @Inject constructor(
    private val repository: NotebookRepository,
    private val store: FileStore,
    private val pipeline: TexturePipeline,
) {
    /** Imports a pending session if one exists. Returns the new id or null. */
    suspend fun importPending(stagingDir: File): NotebookId? = withContext(Dispatchers.IO) {
        val manifest = File(stagingDir, "session.manifest")
        if (!manifest.exists()) return@withContext null

        val covers = HashMap<String, String>() // slot -> capture id
        val pages = ArrayList<Pair<String, Int>>() // capture id | "blank" -> rotation
        for (line in manifest.readLines()) {
            val parts = line.trim().split(" ")
            when {
                parts.size >= 3 && parts[0] == "cover" -> covers[parts[1]] = parts[2]
                parts.size >= 3 && parts[0] == "page" -> pages.add(parts[1] to (parts[2].toIntOrNull() ?: 0))
            }
        }
        if (covers["FRONT"] == null) return@withContext null

        val now = System.currentTimeMillis()

        fun importCapture(captureId: String, rotation: Int): Page {
            val pageId = UUID.randomUUID().toString()
            val bytes = stagedBytes(stagingDir, captureId) ?: blankJpeg()
            val hash = store.writeOriginal(pageId, bytes)
            val edits = EditParams(rotationDeg = rotation)
            pipeline.regenerate(pageId, edits)
            return Page(
                id = PageId(pageId),
                original = ImageRef("originals/$pageId.jpg", hash),
                edits = edits,
                capturedAtEpochMs = now,
            )
        }

        fun blankPage(): Page {
            val pageId = UUID.randomUUID().toString()
            val hash = store.writeOriginal(pageId, blankJpeg())
            pipeline.regenerate(pageId, EditParams.NONE)
            return Page(
                id = PageId(pageId),
                original = ImageRef("originals/$pageId.jpg", hash),
                edits = EditParams.NONE,
                capturedAtEpochMs = now,
                isGeneratedBlank = true,
            )
        }

        val importedPages = pages.map { (ref, rotation) ->
            if (ref == "blank") blankPage() else importCapture(ref, rotation)
        }
        val padded = if (importedPages.size % 2 == 1) importedPages + blankPage() else importedPages
        val sheets = padded.chunked(2).mapIndexed { index, (front, back) ->
            Sheet(SheetId(UUID.randomUUID().toString()), index, front, back)
        }

        val notebook = Notebook(
            id = NotebookId(UUID.randomUUID().toString()),
            title = "Notebook ${now % 10_000}",
            profile = DEFAULT_PROFILE,
            cover = CoverSet(
                front = importCapture(covers.getValue("FRONT"), 0),
                back = covers["BACK"]?.let { importCapture(it, 0) },
                insideFront = covers["INSIDE_FRONT"]?.let { importCapture(it, 0) },
                insideBack = covers["INSIDE_BACK"]?.let { importCapture(it, 0) },
            ),
            sheets = sheets,
            createdAtEpochMs = now,
            shelfPosition = Int.MAX_VALUE,
        )
        repository.create(notebook)
        stagingDir.deleteRecursively()
        notebook.id
    }

    /** Prefers the dewarped capture, falls back to the original frame. */
    private fun stagedBytes(stagingDir: File, captureId: String): ByteArray? {
        val flat = File(stagingDir, "cap-$captureId.jpg")
        if (flat.exists()) return flat.readBytes()
        val orig = File(stagingDir, "cap-$captureId-orig.jpg")
        return if (orig.exists()) orig.readBytes() else null
    }

    private fun blankJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(512, 726, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(0xFFF2ECDC.toInt())
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    private companion object {
        val DEFAULT_PROFILE = NotebookProfile(
            kind = ProfileKind.CUSTOM,
            binding = Binding.SEWN,
            paper = PaperSpec(
                weightGsm = 90,
                stiffness = 0.5f,
                translucency = 0.25f,
                grain = GrainKind.LAID,
                tintArgb = 0xFFF2ECDC.toInt(),
            ),
            coverType = CoverType.CARDBOARD,
            pageAspectRatio = 0.705f,
        )
    }
}
