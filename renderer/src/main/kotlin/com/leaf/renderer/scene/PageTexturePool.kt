package com.leaf.renderer.scene

import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.leaf.filament.Textures
import com.leaf.renderer.PageTexture
import com.leaf.renderer.TextureDetail
import com.leaf.renderer.TextureProvider
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Streaming texture pool (docs/04-GRAPHICS-PIPELINE.md §4): requests what
 * the [ResidencyPlanner] wants, uploads at most [MAX_UPLOADS_PER_FRAME]
 * decoded results per frame on the render thread, evicts LRU outside the
 * plan when over budget. A page keeps its previous texture until the
 * replacement is fully uploaded — never gray, never the wrong page.
 */
class PageTexturePool(
    private val engine: Engine,
    private val provider: TextureProvider,
    private val budgetBytes: Long = DEFAULT_BUDGET_BYTES,
) {
    private class Entry(
        var texture: Texture?,
        var detail: TextureDetail?,
        var pendingDetail: TextureDetail?,
        var sizeBytes: Long,
        var lastWantedFrame: Long,
    )

    private val entries = HashMap<Int, Entry>()
    private val delivered = ConcurrentLinkedQueue<Triple<Int, TextureDetail, PageTexture?>>()
    private var frame = 0L
    private var residentBytes = 0L

    /** Pages whose texture changed in the last [update] — rebind these. */
    val updatedPages = ArrayList<Int>()

    fun texture(pageIndex: Int): Texture? = entries[pageIndex]?.texture

    /**
     * One render-thread step: issue wanted requests, upload a bounded number
     * of delivered results, evict what the plan no longer wants.
     */
    fun update(plan: List<ResidencyPlanner.Want>) {
        frame++
        updatedPages.clear()

        val wanted = HashMap<Int, TextureDetail>(plan.size)
        for (want in plan) {
            wanted[want.pageIndex] = want.detail
            val entry = entries.getOrPut(want.pageIndex) {
                Entry(null, null, null, 0L, frame)
            }
            entry.lastWantedFrame = frame
            val needs = entry.detail != want.detail && entry.pendingDetail != want.detail
            if (needs && entry.pendingDetail == null) {
                entry.pendingDetail = want.detail
                val page = want.pageIndex
                val detail = want.detail
                provider.request(page, detail) { result ->
                    delivered.add(Triple(page, detail, result))
                }
            }
        }

        // Bounded uploads (docs/04 §4: ≤2 textures/frame on the render thread).
        var uploads = 0
        while (uploads < MAX_UPLOADS_PER_FRAME) {
            val (page, detail, result) = delivered.poll() ?: break
            val entry = entries[page] ?: continue
            if (entry.pendingDetail == detail) entry.pendingDetail = null
            if (result == null) continue
            // Stale delivery (plan moved on) still replaces nothing worse —
            // but skip if we already hold something at least as good.
            if (entry.detail == TextureDetail.FULL && detail == TextureDetail.LOW) continue
            val texture = Textures.fromMipLevels(engine, result.width, result.height, result.levels)
            entry.texture?.let {
                engine.destroyTexture(it)
                residentBytes -= entry.sizeBytes
            }
            entry.texture = texture
            entry.detail = detail
            entry.sizeBytes = result.byteSize
            residentBytes += result.byteSize
            updatedPages.add(page)
            uploads++
        }

        // Evict resident textures the plan no longer mentions, oldest first,
        // until we're back under budget (and always drop never-wanted ones).
        if (residentBytes > budgetBytes) {
            val candidates = entries.entries
                .filter { it.value.texture != null && !wanted.containsKey(it.key) }
                .sortedBy { it.value.lastWantedFrame }
            for (candidate in candidates) {
                if (residentBytes <= budgetBytes) break
                evict(candidate.key)
            }
        }
    }

    val residentByteCount: Long get() = residentBytes

    private fun evict(pageIndex: Int) {
        val entry = entries.remove(pageIndex) ?: return
        entry.texture?.let {
            engine.destroyTexture(it)
            residentBytes -= entry.sizeBytes
        }
        provider.cancel(pageIndex)
    }

    fun destroy() {
        for ((_, entry) in entries) {
            entry.texture?.let { engine.destroyTexture(it) }
        }
        entries.clear()
        residentBytes = 0
    }

    companion object {
        const val MAX_UPLOADS_PER_FRAME = 2

        /** ≈24 resident 2K RGBA chains (docs/04 §4 pool sizing). */
        const val DEFAULT_BUDGET_BYTES = 24L * 2048 * 2048 * 4 * 4 / 3
    }
}
