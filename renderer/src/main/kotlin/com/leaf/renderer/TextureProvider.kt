package com.leaf.renderer

/**
 * Decoded GPU-ready page derivative: RGBA8 mip levels, level 0 first
 * (docs/02 §3 — the pipeline precomputes the chain; the render thread never
 * generates mips).
 */
class PageTexture(
    val width: Int,
    val height: Int,
    val levels: List<ByteArray>,
) {
    val byteSize: Long = levels.sumOf { it.size.toLong() }
}

enum class TextureDetail { FULL, LOW }

/**
 * The engine's pull path for page textures (docs/02 §4, docs/04 §4):
 * streaming policy lives in the engine (it knows spread + turn direction),
 * I/O stays behind this interface in the data layer or the host app.
 */
interface TextureProvider {

    /**
     * Loads page [pageIndex]'s derivative off-thread; [deliver] may be
     * called from any thread (the engine re-syncs internally), with null
     * when the page has no stored image. [detail] LOW may deliver a
     * truncated chain (small levels only).
     */
    fun request(pageIndex: Int, detail: TextureDetail, deliver: (PageTexture?) -> Unit)

    /** Best-effort: the engine no longer wants this page. */
    fun cancel(pageIndex: Int) {}
}
