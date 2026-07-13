package com.leaf.physics

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageStripTest {

    private val dt = 1f / 120f

    private fun newStrip(stiffness: Float = 0.5f) =
        PageStrip(PageParams(widthMeters = 0.14f, stiffness = stiffness)).apply {
            setSurfaces(left = 0.004f, right = 0.008f, slopeLeft = 0.19f)
            resetFlat(onRight = true, bowHeight = 0.0035f)
        }

    private fun PageStrip.totalLengthError(): Float {
        val seg = params.widthMeters / (n - 1)
        var err = 0f
        for (i in 0 until n - 1) {
            val dx = px[i + 1] - px[i]
            val dz = pz[i + 1] - pz[i]
            err += abs(sqrt(dx * dx + dz * dz) - seg)
        }
        return err
    }

    private fun PageStrip.runUntilSettled(maxSteps: Int = 3_000): Int {
        var steps = 0
        while (settle == PageStrip.Settle.IN_FLIGHT && steps < maxSteps) {
            step(dt); steps++
        }
        return steps
    }

    @Test
    fun `inextensibility survives a violent drag session`() {
        val strip = newStrip()
        strip.grab(0.95f)
        var t = 0f
        repeat(2_000) { i ->
            t += dt
            // Finger thrashing around the spine.
            strip.drag(
                0.13f * kotlin.math.cos(t * 9f) - 0.02f,
                0.02f + 0.05f * abs(kotlin.math.sin(t * 7f)),
            )
            strip.step(dt)
            assertTrue(strip.px.all { it.isFinite() } && strip.pz.all { it.isFinite() }, "NaN at step $i")
        }
        assertTrue(
            strip.totalLengthError() < 1e-3f,
            "paper stretched: total segment error=${strip.totalLengthError()}",
        )
    }

    @Test
    fun `released past the spine falls left`() {
        val strip = newStrip()
        strip.grab(0.9f)
        // Carry the free edge well past the spine.
        repeat(240) { strip.drag(-0.09f, 0.05f); strip.step(dt) }
        strip.release(directionHint = 0)
        strip.runUntilSettled()
        assertEquals(PageStrip.Settle.SETTLED_LEFT, strip.settle)
    }

    @Test
    fun `released before the spine falls back right`() {
        val strip = newStrip()
        strip.grab(0.9f)
        // Lift, but stay clearly on the right side.
        repeat(240) { strip.drag(0.09f, 0.06f); strip.step(dt) }
        strip.release(directionHint = 0)
        strip.runUntilSettled()
        assertEquals(PageStrip.Settle.SETTLED_RIGHT, strip.settle)
    }

    @Test
    fun `flick hint overrides position`() {
        val strip = newStrip()
        strip.grab(0.9f)
        repeat(240) { strip.drag(0.09f, 0.06f); strip.step(dt) }
        // Barely lifted on the right, but flicked hard toward the left.
        strip.release(directionHint = -1)
        strip.runUntilSettled()
        assertEquals(PageStrip.Settle.SETTLED_LEFT, strip.settle)
    }

    @Test
    fun `grabbed point tracks a reachable target`() {
        val strip = newStrip()
        strip.grab(1f)
        repeat(360) { strip.drag(0.05f, 0.07f); strip.step(dt) }
        val g = strip.n - 1
        val dx = strip.px[g] - 0.05f
        val dz = strip.pz[g] - 0.07f
        assertTrue(
            sqrt(dx * dx + dz * dz) < 0.006f,
            "grab point drifted ${sqrt(dx * dx + dz * dz)}m from finger",
        )
    }

    @Test
    fun `paper buckles up instead of stretching when finger nears the spine`() {
        val strip = newStrip()
        strip.grab(1f)
        // Finger at 30% of the width from the spine: 70% of the paper must fold.
        val fingerZ = 0.02f
        repeat(360) { strip.drag(0.042f, fingerZ); strip.step(dt) }
        val apex = (1 until strip.n).maxOf { strip.pz[it] }
        // The fold's loop must rise clearly above the finger itself.
        assertTrue(apex > fingerZ + 0.005f, "expected an upward buckle, apex=$apex")
        assertTrue(strip.totalLengthError() < 1e-3f)
    }

    @Test
    fun `air drag brakes the fall of a released page`() {
        fun fallAfter(airDrag: Float, steps: Int): Float {
            val strip = PageStrip(
                PageParams(widthMeters = 0.14f, stiffness = 0.5f, damping = 0f, airDrag = airDrag),
            ).apply {
                setSurfaces(left = 0.004f, right = 0.008f, slopeLeft = 0.19f)
                resetFlat(onRight = true, bowHeight = 0.0035f)
            }
            strip.grab(0.9f)
            repeat(240) { strip.drag(0.02f, 0.12f); strip.step(dt) }
            val tip = strip.n - 1
            val z0 = strip.pz[tip]
            strip.release(directionHint = 1)
            repeat(steps) { strip.step(dt) }
            return z0 - strip.pz[tip]
        }
        val draggy = fallAfter(airDrag = 40f, steps = 20)
        val vacuum = fallAfter(airDrag = 0f, steps = 20)
        assertTrue(
            draggy < vacuum * 0.85f,
            "air drag should slow the drop: draggy=$draggy vacuum=$vacuum",
        )
    }

    @Test
    fun `deterministic across runs`() {
        fun run(): Pair<Float, Float> {
            val strip = newStrip()
            strip.grab(0.85f)
            repeat(200) { strip.drag(-0.02f, 0.05f); strip.step(dt) }
            strip.release(0)
            repeat(400) { strip.step(dt) }
            return strip.px[strip.n - 1] to strip.pz[strip.n - 1]
        }
        val a = run(); val b = run()
        assertTrue(
            a.first.toRawBits() == b.first.toRawBits() && a.second.toRawBits() == b.second.toRawBits(),
            "bitwise determinism violated",
        )
    }

    @Test
    fun `settles on the sloped left surface without sinking through`() {
        val strip = newStrip()
        strip.grab(0.9f)
        repeat(300) { strip.drag(-0.1f, 0.04f); strip.step(dt) }
        strip.release(-1)
        strip.runUntilSettled()
        assertEquals(PageStrip.Settle.SETTLED_LEFT, strip.settle)
        for (i in 1 until strip.n) {
            val floor = strip.surface(strip.px[i])
            assertTrue(strip.pz[i] >= floor - 1e-4f, "particle $i sank through the stack")
        }
    }
}
