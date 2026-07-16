package com.leaf.camera.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuadStabilityTrackerTest {

    private val steady = Quad.of(0.1f, 0.1f, 0.9f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f)

    private fun jiggled(amount: Float) =
        Quad.of(0.1f + amount, 0.1f, 0.9f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f)

    @Test
    fun `fires once after the required stable hold`() {
        val tracker = QuadStabilityTracker(requiredStableFrames = 5, cooldownFrames = 10)
        var fires = 0
        repeat(8) { if (tracker.feed(steady)) fires++ }
        assertEquals(1, fires, "one hold = one capture")
    }

    @Test
    fun `movement resets the hold`() {
        val tracker = QuadStabilityTracker(requiredStableFrames = 5, cornerTolerance = 0.01f)
        repeat(4) { assertTrue(!tracker.feed(steady)) }
        tracker.feed(jiggled(0.05f)) // jerk
        var fires = 0
        repeat(4) { if (tracker.feed(steady)) fires++ }
        assertEquals(0, fires, "hold must restart after movement")
    }

    @Test
    fun `cooldown blocks refire until it elapses`() {
        val tracker = QuadStabilityTracker(requiredStableFrames = 3, cooldownFrames = 20)
        var fires = 0
        repeat(40) { if (tracker.feed(steady)) fires++ }
        assertEquals(2, fires, "steady quad refires only after cooldown")
    }

    @Test
    fun `losing the quad clears the overlay`() {
        val tracker = QuadStabilityTracker()
        tracker.feed(steady)
        assertNotNull(tracker.overlay)
        tracker.feed(null)
        assertNull(tracker.overlay)
    }

    @Test
    fun `overlay smooths toward the detection`() {
        val tracker = QuadStabilityTracker(smoothing = 0.5f)
        tracker.feed(steady)
        tracker.feed(jiggled(0.2f))
        val overlay = tracker.overlay!!
        // One blend step at 0.5: overlay TL x should be halfway.
        assertTrue(overlay.x0 > 0.1f && overlay.x0 < 0.3f, "overlay jumped: ${overlay.x0}")
    }
}
