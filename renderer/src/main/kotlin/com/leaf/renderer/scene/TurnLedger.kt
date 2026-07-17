package com.leaf.renderer.scene

/**
 * Bookkeeping for multi-page flight (M8, docs/03-RENDERER.md §3): which
 * sheets rest left, which rest right, and which are airborne. Only stack
 * tops can be grabbed, so the airborne set is always the contiguous index
 * range between the stacks: [leftCount, sheetCount - rightCount - 1] minus
 * none — grabs peel from either end of the resting ranges.
 *
 * Landing can finish out of grab order (a later page may fall back before
 * an earlier one lands). A sheet can only join a stack it is adjacent to,
 * so landing sheet s on the left forces every airborne sheet below it
 * (index < s) left first; landing right forces every airborne sheet above
 * it (index > s) right first. [land] returns that forced sequence so the
 * scene can retire those flights in order.
 *
 * Pure Kotlin — JVM-tested; the scene owns meshes, this owns the ledger.
 */
class TurnLedger(val sheetCount: Int, initialSpread: Int) {

    var leftCount: Int = initialSpread.coerceIn(0, sheetCount)
        private set
    var rightCount: Int = sheetCount - leftCount
        private set

    private val airborneSheets = sortedSetOf<Int>()

    val airborne: List<Int> get() = airborneSheets.toList()
    val inFlight: Int get() = airborneSheets.size

    /** Spread once everything airborne has landed left of it / right of it. */
    val restingSpread: Int get() = leftCount

    init {
        require(sheetCount >= 0)
    }

    /** Takes the top right sheet into flight; null when the right side is spent. */
    fun grabForward(): Int? {
        if (rightCount == 0) return null
        val sheet = sheetCount - rightCount
        rightCount--
        airborneSheets.add(sheet)
        return sheet
    }

    /** Takes the top left sheet into flight; null when the left side is spent. */
    fun grabBackward(): Int? {
        if (leftCount == 0) return null
        val sheet = leftCount - 1
        leftCount--
        airborneSheets.add(sheet)
        return sheet
    }

    /**
     * Lands [sheet] on a side. Returns every landing this implies, forced
     * blockers first, ending with [sheet] itself. Each entry is
     * sheet -> landedLeft.
     */
    fun land(sheet: Int, left: Boolean): List<Pair<Int, Boolean>> {
        require(airborneSheets.contains(sheet)) { "sheet $sheet is not airborne" }
        val landings = ArrayList<Pair<Int, Boolean>>(airborneSheets.size)
        if (left) {
            for (s in airborneSheets.headSet(sheet).toList()) landings.add(s to true)
            landings.add(sheet to true)
        } else {
            for (s in airborneSheets.tailSet(sheet + 1).toList().asReversed()) landings.add(s to false)
            landings.add(sheet to false)
        }
        for ((s, l) in landings) {
            airborneSheets.remove(s)
            if (l) {
                check(s == leftCount) { "left landing out of order: sheet $s onto $leftCount" }
                leftCount++
            } else {
                check(s == sheetCount - rightCount - 1) { "right landing out of order: sheet $s" }
                rightCount++
            }
        }
        return landings
    }

    /**
     * Number of airborne sheets that would pile UNDER [sheet] on each side —
     * used to keep in-flight floors stacked correctly before they land.
     */
    fun airborneBelowOnLeft(sheet: Int): Int = airborneSheets.headSet(sheet).size

    fun airborneAboveOnRight(sheet: Int): Int = airborneSheets.tailSet(sheet + 1).size
}
