package com.leaf.camera.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScanSessionTest {

    @Test
    fun `flow runs front cover through prompts into pages`() {
        val s = ScanSession()
        assertEquals(ScanSession.Stage.Cover(ScanSession.CoverSlot.FRONT), s.stage)
        s.capture(id = 1)
        assertEquals(ScanSession.Stage.Cover(ScanSession.CoverSlot.BACK), s.stage)
        s.skip()
        s.skip()
        s.skip()
        assertEquals(ScanSession.Stage.Pages(1), s.stage)
    }

    @Test
    fun `front cover cannot be skipped`() {
        val s = ScanSession()
        assertFailsWith<IllegalStateException> { s.skip() }
    }

    @Test
    fun `continuous scanning counts pages and pairs sheets`() {
        val s = ScanSession().apply {
            capture(1) // front cover
            skip(); skip(); skip()
        }
        s.capture(10)
        s.capture(11)
        s.markBlank()
        s.capture(12)
        s.capture(13)
        assertEquals(ScanSession.Stage.Pages(6), s.stage)
        assertEquals(5, s.pageCount)
        // 5 pages -> 3 sheets (last back pads blank at storage time).
        assertEquals(3, s.sheetCount)
        assertTrue(s.pages[2].blank)
    }

    @Test
    fun `review operations preserve reading order`() {
        val s = ScanSession().apply {
            capture(1)
            skip(); skip(); skip()
            capture(10); capture(11); capture(12)
            finishScanning()
        }
        assertEquals(ScanSession.Stage.Review, s.stage)

        s.movePage(from = 2, to = 0)
        assertEquals(listOf(12L, 10L, 11L), s.pages.map { it.id })

        s.retakePage(1, newId = 99)
        assertEquals(listOf(12L, 99L, 11L), s.pages.map { it.id })

        s.insertBlank(at = 1)
        assertTrue(s.pages[1].blank)
        assertEquals(4, s.pageCount)

        s.deletePage(1)
        assertEquals(listOf(12L, 99L, 11L), s.pages.map { it.id })

        s.rotatePage(0)
        s.rotatePage(0)
        assertEquals(180, s.pages[0].rotationDeg)

        val crop = Quad.of(0.1f, 0.1f, 0.9f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f)
        s.setCrop(2, crop)
        assertEquals(crop, s.pages[2].cropQuad)
    }

    @Test
    fun `resume scanning appends after review`() {
        val s = ScanSession().apply {
            capture(1)
            skip(); skip(); skip()
            capture(10)
            finishScanning()
        }
        s.resumeScanning()
        assertEquals(ScanSession.Stage.Pages(2), s.stage)
        s.capture(11)
        assertEquals(2, s.pageCount)
    }

    @Test
    fun `capturing during review is rejected`() {
        val s = ScanSession().apply {
            capture(1)
            skip(); skip(); skip()
            capture(10)
            finishScanning()
        }
        assertFailsWith<IllegalStateException> { s.capture(11) }
    }
}
