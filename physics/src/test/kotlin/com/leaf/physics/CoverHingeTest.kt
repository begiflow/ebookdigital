package com.leaf.physics

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverHingeTest {

    private val dt = 1f / 120f

    private fun CoverHinge.settle(maxSteps: Int = 5_000): Int {
        var steps = 0
        while (!isSettled && steps < maxSteps) {
            step(dt)
            steps++
        }
        return steps
    }

    @Test
    fun `released slightly open falls closed`() {
        val hinge = CoverHinge()
        hinge.grab(); hinge.drag(0.4f)
        repeat(240) { hinge.step(dt) }
        hinge.release()
        hinge.settle()
        assertTrue(hinge.isSettled, "should settle")
        assertEquals(0f, hinge.angle, 0.05f)
    }

    @Test
    fun `released past the decision angle falls open to rest pose`() {
        val hinge = CoverHinge()
        hinge.grab(); hinge.drag(1.6f)
        repeat(240) { hinge.step(dt) }
        hinge.release()
        hinge.settle()
        assertTrue(hinge.isSettled, "should settle")
        assertEquals(hinge.openRestAngle, hinge.angle, 0.08f)
    }

    @Test
    fun `detent snaps the last degrees shut`() {
        val hinge = CoverHinge()
        hinge.grab(); hinge.drag(0.12f)
        repeat(240) { hinge.step(dt) }
        hinge.release()
        // Within a quarter second the detent must have seated the cover.
        repeat(30) { hinge.step(dt) }
        assertTrue(hinge.angle < 0.02f, "detent should snap shut fast, angle=${hinge.angle}")
    }

    @Test
    fun `drag tracks the target closely`() {
        val hinge = CoverHinge()
        hinge.grab()
        hinge.drag(2.0f)
        repeat(120) { hinge.step(dt) }
        assertEquals(2.0f, hinge.angle, 0.05f)
    }

    @Test
    fun `angle stays in bounds under violent dragging`() {
        val hinge = CoverHinge()
        hinge.grab()
        repeat(600) { i ->
            hinge.drag(if (i % 2 == 0) -5f else 9f)
            hinge.step(dt)
            assertTrue(hinge.angle in 0f..hinge.maxAngle)
            assertTrue(hinge.angle.isFinite() && hinge.velocity.isFinite())
        }
    }

    @Test
    fun `simulation is deterministic`() {
        fun run(): Float {
            val hinge = CoverHinge()
            hinge.grab(); hinge.drag(1.2f)
            repeat(200) { hinge.step(dt) }
            hinge.release()
            repeat(500) { hinge.step(dt) }
            return hinge.angle
        }
        val a = run(); val b = run()
        assertTrue(a.toRawBits() == b.toRawBits(), "bitwise determinism required")
        assertTrue(abs(a) >= 0f)
    }
}
