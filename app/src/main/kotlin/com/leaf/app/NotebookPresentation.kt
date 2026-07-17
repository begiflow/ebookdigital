package com.leaf.app

import android.graphics.BitmapFactory
import com.leaf.data.files.FileStore
import com.leaf.data.texture.Ktx1
import com.leaf.domain.model.Binding
import com.leaf.domain.model.Notebook
import com.leaf.renderer.PageTexture
import com.leaf.renderer.RenderBinding
import com.leaf.renderer.RenderBook
import com.leaf.renderer.RenderGrain
import com.leaf.renderer.TextureDetail
import com.leaf.renderer.TextureProvider
import java.util.concurrent.Executors

/**
 * Presentation mapping (docs/02-ARCHITECTURE.md §4): domain Notebook →
 * engine RenderBook, and the file store's KTX derivatives → the engine's
 * streaming pull path.
 */
object NotebookPresentation {

    /** A5-referenced physical dims scaled by the profile's aspect ratio. */
    fun toRenderBook(notebook: Notebook, store: FileStore): RenderBook {
        val heightM = 0.210f
        val widthM = heightM * notebook.profile.pageAspectRatio
        val paper = notebook.profile.paper
        val coverBitmap = store.thumbFile(notebook.cover.front.id.value)
            .takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
        return RenderBook(
            widthMeters = widthM,
            heightMeters = heightM,
            sheetCount = notebook.sheets.size,
            sheetThicknessMeters = 0.00006f + paper.weightGsm * 1.2e-6f,
            coverThicknessMeters = 0.002f,
            binding = when (notebook.profile.binding) {
                Binding.SEWN -> RenderBinding.SEWN
                Binding.STAPLED -> RenderBinding.STAPLED
                Binding.SPIRAL -> RenderBinding.SPIRAL
                Binding.GLUED -> RenderBinding.GLUED
            },
            frontCover = coverBitmap,
            paperStiffness = paper.stiffness,
            paperTranslucency = paper.translucency,
            grain = when (paper.grain) {
                com.leaf.domain.model.GrainKind.LAID -> RenderGrain.LAID
                com.leaf.domain.model.GrainKind.WOVEN -> RenderGrain.WOVEN
                com.leaf.domain.model.GrainKind.GLOSS -> RenderGrain.GLOSS
            },
        )
    }
}

/**
 * Streams stored KTX derivatives into the engine (M13 pull path, docs/04
 * §4). Page index 2k / 2k+1 = sheet k front / back — the same pairing the
 * scanner recorded.
 */
class KtxTextureProvider(
    notebook: Notebook,
    private val store: FileStore,
) : TextureProvider {

    private val pageIds: List<String> = notebook.sheets
        .sortedBy { it.index }
        .flatMap { listOf(it.front.id.value, it.back.id.value) }

    private val executor = Executors.newFixedThreadPool(2)

    override fun request(pageIndex: Int, detail: TextureDetail, deliver: (PageTexture?) -> Unit) {
        val pageId = pageIds.getOrNull(pageIndex) ?: return deliver(null)
        executor.execute {
            val file = store.textureFile(pageId)
            val image = file.takeIf { it.exists() }?.let { Ktx1.read(it.readBytes()) }
            if (image == null) {
                deliver(null)
                return@execute
            }
            // LOW detail: skip the largest levels, keep the small tail.
            val drop = if (detail == TextureDetail.LOW) minOf(2, image.levels.size - 1) else 0
            deliver(
                PageTexture(
                    width = maxOf(1, image.width shr drop),
                    height = maxOf(1, image.height shr drop),
                    levels = image.levels.drop(drop),
                ),
            )
        }
    }

    fun shutdown() {
        executor.shutdown()
    }
}
