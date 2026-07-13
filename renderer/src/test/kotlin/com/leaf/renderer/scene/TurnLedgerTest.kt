package com.leaf.renderer.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnLedgerTest {

    @Test
    fun `forward grabs peel the right stack top in order`() {
        val ledger = TurnLedger(sheetCount = 10, initialSpread = 4)
        assertEquals(4, ledger.grabForward())
        assertEquals(5, ledger.grabForward())
        assertEquals(6, ledger.grabForward())
        assertEquals(4, ledger.leftCount)
        assertEquals(3, ledger.rightCount)
        assertEquals(listOf(4, 5, 6), ledger.airborne)
    }

    @Test
    fun `backward grabs peel the left stack top`() {
        val ledger = TurnLedger(sheetCount = 10, initialSpread = 4)
        assertEquals(3, ledger.grabBackward())
        assertEquals(2, ledger.grabBackward())
        assertEquals(2, ledger.leftCount)
        assertEquals(6, ledger.rightCount)
    }

    @Test
    fun `grabs return null at the ends of the book`() {
        val ledger = TurnLedger(sheetCount = 2, initialSpread = 0)
        assertNull(ledger.grabBackward())
        assertEquals(0, ledger.grabForward())
        assertEquals(1, ledger.grabForward())
        assertNull(ledger.grabForward())
    }

    @Test
    fun `in-order landings advance the spread`() {
        val ledger = TurnLedger(sheetCount = 10, initialSpread = 4)
        val sheet = ledger.grabForward()!!
        val landings = ledger.land(sheet, left = true)
        assertEquals(listOf(4 to true), landings)
        assertEquals(5, ledger.restingSpread)
        assertEquals(0, ledger.inFlight)
    }

    @Test
    fun `landing out of order forces the blockers down first`() {
        val ledger = TurnLedger(sheetCount = 10, initialSpread = 4)
        ledger.grabForward() // 4
        ledger.grabForward() // 5
        ledger.grabForward() // 6
        // The last-grabbed page lands left while 4 and 5 still fly:
        // they are beneath it, so they are forced left first.
        val landings = ledger.land(6, left = true)
        assertEquals(listOf(4 to true, 5 to true, 6 to true), landings)
        assertEquals(7, ledger.restingSpread)
        assertEquals(0, ledger.inFlight)
    }

    @Test
    fun `falling back right forces the sheets above it`() {
        val ledger = TurnLedger(sheetCount = 10, initialSpread = 4)
        ledger.grabForward() // 4
        ledger.grabForward() // 5
        ledger.grabForward() // 6
        // The first-grabbed page falls back right: 6 and 5 must land first.
        val landings = ledger.land(4, left = false)
        assertEquals(listOf(6 to false, 5 to false, 4 to false), landings)
        assertEquals(4, ledger.restingSpread)
        assertEquals(6, ledger.rightCount)
    }

    @Test
    fun `mixed direction flight keeps counts consistent`() {
        val ledger = TurnLedger(sheetCount = 10, initialSpread = 4)
        val fwd = ledger.grabForward()!! // 4
        val bwd = ledger.grabBackward()!! // 3
        assertEquals(listOf(3, 4), ledger.airborne)

        // The backward page (3) completes right; the forward page (4) sits
        // deeper in the right stack, so it is forced down first.
        val landings = ledger.land(bwd, left = false)
        assertEquals(listOf(fwd to false, bwd to false), landings)
        assertEquals(3, ledger.restingSpread)
        assertEquals(0, ledger.inFlight)
        assertEquals(10, ledger.leftCount + ledger.rightCount)
    }

    @Test
    fun `airborne stacking helpers count correctly`() {
        val ledger = TurnLedger(sheetCount = 10, initialSpread = 4)
        ledger.grabForward() // 4
        ledger.grabForward() // 5
        ledger.grabForward() // 6
        assertEquals(0, ledger.airborneBelowOnLeft(4))
        assertEquals(2, ledger.airborneBelowOnLeft(6))
        assertEquals(2, ledger.airborneAboveOnRight(4))
        assertEquals(0, ledger.airborneAboveOnRight(6))
    }

    @Test
    fun `interleaved grab and land sequence conserves sheets`() {
        val ledger = TurnLedger(sheetCount = 6, initialSpread = 0)
        repeat(6) {
            val s = ledger.grabForward()!!
            ledger.land(s, left = true)
        }
        assertEquals(6, ledger.restingSpread)
        assertNull(ledger.grabForward())
        assertTrue(ledger.airborne.isEmpty())
    }
}
