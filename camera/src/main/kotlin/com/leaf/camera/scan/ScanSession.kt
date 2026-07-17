package com.leaf.camera.scan

/**
 * The reading-order capture session (docs/01-PRD.md §5.4): front cover
 * (mandatory) → back cover / inside covers (prompted, skippable) → pages in
 * continuous reading order → review. Sheets auto-pair afterwards (page
 * 2k / 2k+1 = sheet k front/back); "Mark blank" records a generated blank
 * without firing the camera.
 *
 * Pure Kotlin state machine — capture ids are opaque references to whatever
 * the caller stored (staging files here, the M13 store later). Review
 * operations (retake / rotate / reorder / delete / insert / manual crop) are
 * non-destructive: they permute or annotate the list, never touch pixels.
 */
class ScanSession {

    enum class CoverSlot { FRONT, BACK, INSIDE_FRONT, INSIDE_BACK }

    sealed interface Stage {
        /** Prompting for a cover surface; only FRONT is mandatory. */
        data class Cover(val slot: CoverSlot) : Stage

        /** Continuous page scanning; [nextPageNumber] is 1-based for UI. */
        data class Pages(val nextPageNumber: Int) : Stage

        data object Review : Stage
    }

    data class Capture(
        val id: Long,
        val blank: Boolean = false,
        val rotationDeg: Int = 0,
        /** Manual crop override from review; null = detector's quad. */
        val cropQuad: Quad? = null,
    )

    var stage: Stage = Stage.Cover(CoverSlot.FRONT)
        private set

    private val coverMap = LinkedHashMap<CoverSlot, Capture>()
    private val pageList = ArrayList<Capture>()

    val covers: Map<CoverSlot, Capture> get() = coverMap
    val pages: List<Capture> get() = pageList
    val pageCount: Int get() = pageList.size

    /** Sheets the pages will pair into (2 pages per sheet, last may pad). */
    val sheetCount: Int get() = (pageList.size + 1) / 2

    /** Records a capture for the current stage and advances it. */
    fun capture(id: Long) {
        when (val s = stage) {
            is Stage.Cover -> {
                coverMap[s.slot] = Capture(id)
                stage = nextCoverStage(s.slot)
            }
            is Stage.Pages -> {
                pageList.add(Capture(id))
                stage = Stage.Pages(pageList.size + 1)
            }
            Stage.Review -> error("session is in review; call resumeScanning() first")
        }
    }

    /** Skips the current cover prompt. The front cover cannot be skipped. */
    fun skip() {
        val s = stage
        check(s is Stage.Cover) { "only cover prompts are skippable" }
        check(s.slot != CoverSlot.FRONT) { "front cover is mandatory (PRD §5.4)" }
        stage = nextCoverStage(s.slot)
    }

    /** One-tap blank back (PRD locked decision): no camera fire, advances. */
    fun markBlank() {
        check(stage is Stage.Pages) { "mark-blank applies to page scanning" }
        pageList.add(Capture(id = BLANK_ID, blank = true))
        stage = Stage.Pages(pageList.size + 1)
    }

    fun finishScanning() {
        check(stage is Stage.Pages) { "finish from page scanning" }
        stage = Stage.Review
    }

    /** Review → continue appending pages at the end. */
    fun resumeScanning() {
        check(stage == Stage.Review)
        stage = Stage.Pages(pageList.size + 1)
    }

    // ------------------------------ review ---------------------------------

    fun retakePage(index: Int, newId: Long) {
        pageList[index] = Capture(id = newId)
    }

    fun rotatePage(index: Int) {
        val p = pageList[index]
        pageList[index] = p.copy(rotationDeg = (p.rotationDeg + 90) % 360)
    }

    fun setCrop(index: Int, quad: Quad?) {
        pageList[index] = pageList[index].copy(cropQuad = quad)
    }

    fun deletePage(index: Int) {
        pageList.removeAt(index)
    }

    fun insertBlank(at: Int) {
        pageList.add(at.coerceIn(0, pageList.size), Capture(id = BLANK_ID, blank = true))
    }

    fun movePage(from: Int, to: Int) {
        val p = pageList.removeAt(from)
        pageList.add(to.coerceIn(0, pageList.size), p)
    }

    private fun nextCoverStage(after: CoverSlot): Stage = when (after) {
        CoverSlot.FRONT -> Stage.Cover(CoverSlot.BACK)
        CoverSlot.BACK -> Stage.Cover(CoverSlot.INSIDE_FRONT)
        CoverSlot.INSIDE_FRONT -> Stage.Cover(CoverSlot.INSIDE_BACK)
        CoverSlot.INSIDE_BACK -> Stage.Pages(1)
    }

    companion object {
        /** id of generated blanks (no stored image). */
        const val BLANK_ID = -1L
    }
}
