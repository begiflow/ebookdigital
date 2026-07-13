package com.leaf.domain.model

@JvmInline
value class NotebookId(val value: String)

@JvmInline
value class SheetId(val value: String)

@JvmInline
value class PageId(val value: String)

/**
 * A preserved physical notebook. Sheet order is the single source of page
 * order; covers are not counted as pages.
 */
data class Notebook(
    val id: NotebookId,
    val title: String,
    val profile: NotebookProfile,
    val cover: CoverSet,
    val sheets: List<Sheet>,
    val createdAtEpochMs: Long,
    val shelfPosition: Int,
)

/**
 * Covers captured separately from pages. Null inside covers fall back to a
 * generated material derived from the profile.
 */
data class CoverSet(
    val front: Page,
    val back: Page?,
    val insideFront: Page?,
    val insideBack: Page?,
)

/**
 * One physical leaf of paper. Both faces always exist (locked decision
 * 2026-07-11): [back] is either a real capture or a profile-generated blank
 * ("Mark blank" during scanning). Reading-order scans pair pages (2k-1, 2k).
 */
data class Sheet(
    val id: SheetId,
    val index: Int,
    val front: Page,
    val back: Page,
)

/**
 * One captured face. [original] is write-once and never recompressed;
 * [edits] are non-destructive parameters applied when deriving the GPU
 * texture and thumbnail.
 */
data class Page(
    val id: PageId,
    val original: ImageRef,
    val edits: EditParams,
    val capturedAtEpochMs: Long,
    val isGeneratedBlank: Boolean = false,
)

/** Reference to an image artifact in the app-private file store. */
data class ImageRef(
    val relativePath: String,
    val sha256: String,
)
