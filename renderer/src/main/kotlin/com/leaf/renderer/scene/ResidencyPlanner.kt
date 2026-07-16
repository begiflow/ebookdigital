package com.leaf.renderer.scene

import com.leaf.renderer.TextureDetail

/**
 * Texture residency policy (docs/04-GRAPHICS-PIPELINE.md §4): full-res mips
 * for the current spread ±[fullWindow] sheets (both faces — show-through
 * needs the other side), low mips out to ±[lowWindow], nothing beyond.
 * Prefetch is biased by turn direction: sheets ahead of the reader count
 * closer than sheets behind. Pure Kotlin, JVM-tested.
 */
class ResidencyPlanner(
    private val sheetCount: Int,
    private val fullWindow: Int = 4,
    private val lowWindow: Int = 16,
) {
    data class Want(
        val pageIndex: Int,
        val detail: TextureDetail,
        /** Lower = more urgent; the visible spread is always 0. */
        val priority: Int,
    )

    /**
     * The desired resident set at [spread], [directionBias] -1/0/+1
     * (backward/idle/forward). Sorted most-urgent first.
     */
    fun plan(spread: Int, directionBias: Int): List<Want> {
        val wants = ArrayList<Want>()
        for (sheet in 0 until sheetCount) {
            val distance = biasedDistance(sheet, spread, directionBias)
            val detail = when {
                distance <= fullWindow -> TextureDetail.FULL
                distance <= lowWindow -> TextureDetail.LOW
                else -> continue
            }
            wants.add(Want(2 * sheet, detail, distance))
            wants.add(Want(2 * sheet + 1, detail, distance))
        }
        wants.sortBy { it.priority }
        return wants
    }

    /**
     * Sheets adjacent to the spread boundary are distance 0 (their faces are
     * the visible pages); the against-the-bias side counts double.
     */
    private fun biasedDistance(sheet: Int, spread: Int, directionBias: Int): Int {
        val raw = when {
            sheet >= spread -> sheet - spread // ahead (right side)
            else -> spread - 1 - sheet // behind (left side)
        }
        val ahead = sheet >= spread
        return when {
            directionBias > 0 && !ahead -> raw * 2 + 1
            directionBias < 0 && ahead -> raw * 2 + 1
            else -> raw
        }
    }
}
