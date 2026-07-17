package com.leaf.renderer.scene

import com.leaf.renderer.TextureDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResidencyPlannerTest {

    private val planner = ResidencyPlanner(sheetCount = 50, fullWindow = 4, lowWindow = 16)

    @Test
    fun `visible spread pages come first at full detail`() {
        val plan = planner.plan(spread = 20, directionBias = 0)
        val first = plan.take(4).map { it.pageIndex }.sorted()
        // Sheets 19 (left page = its back, 39) and 20 (right page = 40).
        assertEquals(listOf(38, 39, 40, 41), first)
        assertTrue(plan.take(4).all { it.detail == TextureDetail.FULL })
    }

    @Test
    fun `both faces of every planned sheet are requested`() {
        val plan = planner.plan(spread = 10, directionBias = 0)
        val pages = plan.map { it.pageIndex }.toSet()
        for (page in pages) {
            val mate = if (page % 2 == 0) page + 1 else page - 1
            assertTrue(mate in pages, "page $page planned without its sheet mate $mate")
        }
    }

    @Test
    fun `detail falls from full to low to absent with distance`() {
        val plan = planner.plan(spread = 25, directionBias = 0)
        val byPage = plan.associateBy { it.pageIndex }
        assertEquals(TextureDetail.FULL, byPage.getValue(2 * 27).detail) // dist 2
        assertEquals(TextureDetail.LOW, byPage.getValue(2 * 35).detail) // dist 10
        assertTrue(byPage[2 * 45] == null, "beyond the low window nothing is resident")
    }

    @Test
    fun `forward bias prefetches ahead of the reader`() {
        val forward = planner.plan(spread = 25, directionBias = 1)
        val byPage = forward.associateBy { it.pageIndex }
        // Ahead sheet at distance 4 stays FULL; behind sheet at 4 drops out
        // of the full window (doubled distance).
        assertEquals(TextureDetail.FULL, byPage.getValue(2 * 29).detail)
        assertEquals(TextureDetail.LOW, byPage.getValue(2 * 21).detail)
    }

    @Test
    fun `plan clamps to the book`() {
        val plan = planner.plan(spread = 0, directionBias = 0)
        assertTrue(plan.all { it.pageIndex in 0 until 100 })
        val end = planner.plan(spread = 50, directionBias = 0)
        assertTrue(end.all { it.pageIndex in 0 until 100 })
    }
}
