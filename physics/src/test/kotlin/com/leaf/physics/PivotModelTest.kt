package com.leaf.physics

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Binding pivots (M11, docs/03 §4): GLUED refuses to lie flat, STAPLED opens
 * flat with a tight root, SPIRAL rides the wire with visible slop.
 */
class PivotModelTest {

    private val dt = 1f / 120f

    private fun strip(pivot: PivotModel) =
        PageStrip(PageParams(widthMeters = 0.14f), pivot).apply {
            setSurfaces(left = 0.004f, right = 0.008f, slopeLeft = -0.19f)
            resetFlat(onRight = true, bowHeight = 0.0035f)
        }

    /** Peak lift of the near-spine region above its stack — the V hump. */
    private fun PageStrip.spineHump(): Float =
        (1 until 5).maxOf { pz[it] - surface(px[it]) }

    private fun PageStrip.settleFully(maxSteps: Int = 3_000) {
        var steps = 0
        while (settle == PageStrip.Settle.IN_FLIGHT && steps < maxSteps) {
            step(dt)
            steps++
        }
    }

    private fun PageStrip.turnAndRelease(targetX: Float, hint: Int) {
        grab(0.9f)
        repeat(240) { drag(targetX, 0.06f); step(dt) }
        release(hint)
        settleFully()
    }

    // ------------------------------- GLUED ---------------------------------

    private val glued = PivotModel.Hinge(rootBendStiffness = 0.85f, foldAngle = 0.9f)

    @Test
    fun `glued page keeps a near-spine hump where a sewn page lies flat`() {
        // Turn both a SEWN and a GLUED page to the left and let them settle:
        // the glue's clamped fold must keep the root region humped up.
        val sewn = strip(PivotModel.Hinge())
        sewn.turnAndRelease(targetX = -0.09f, hint = -1)
        assertEquals(PageStrip.Settle.SETTLED_LEFT, sewn.settle)

        val gluedStrip = strip(glued)
        gluedStrip.turnAndRelease(targetX = -0.09f, hint = -1)
        assertEquals(PageStrip.Settle.SETTLED_LEFT, gluedStrip.settle)

        assertTrue(sewn.spineHump() < 0.0012f, "sewn should lie flat: ${sewn.spineHump()}")
        assertTrue(
            gluedStrip.spineHump() > 0.0025f,
            "glued must not lie flat: hump=${gluedStrip.spineHump()}",
        )
    }

    @Test
    fun `glued page still turns fully and stays inextensible`() {
        val s = strip(glued)
        s.turnAndRelease(targetX = -0.1f, hint = -1)
        assertEquals(PageStrip.Settle.SETTLED_LEFT, s.settle)
        val seg = s.params.widthMeters / (s.n - 1)
        var err = 0f
        for (i in 0 until s.n - 1) {
            val dx = s.px[i + 1] - s.px[i]
            val dz = s.pz[i + 1] - s.pz[i]
            err += abs(sqrt(dx * dx + dz * dz) - seg)
        }
        assertTrue(err < 1e-3f, "glued paper stretched: $err")
    }

    // ------------------------------ STAPLED --------------------------------

    @Test
    fun `stapled page opens flat and settles`() {
        val s = strip(PivotModel.Hinge(rootBendStiffness = 0.5f, foldAngle = 0f))
        s.settleFully()
        assertEquals(PageStrip.Settle.SETTLED_RIGHT, s.settle)
        val tipLift = s.pz[s.n - 1] - s.surface(s.px[s.n - 1])
        assertTrue(tipLift < 0.002f, "stapled page should lie flat: $tipLift")
        assertTrue(s.spineHump() < 0.002f, "stapled near-spine should stay low: ${s.spineHump()}")

        // And it turns fully despite the tight root.
        val turned = strip(PivotModel.Hinge(rootBendStiffness = 0.5f, foldAngle = 0f))
        turned.turnAndRelease(targetX = -0.1f, hint = -1)
        assertEquals(PageStrip.Settle.SETTLED_LEFT, turned.settle)
    }

    // ------------------------------ SPIRAL ---------------------------------

    // Book-realistic coil: axis at mid-thickness, radius past the block.
    private val wire = PivotModel.Wire(centerZ = 0.0068f, radius = 0.0108f, slack = 0.0015f)

    @Test
    fun `spiral root always rides the wire circle within the slop`() {
        val s = strip(wire)
        s.grab(0.95f)
        var t = 0f
        repeat(1_200) {
            t += dt
            s.drag(0.12f * kotlin.math.cos(t * 8f) - 0.01f, 0.03f + 0.05f * abs(kotlin.math.sin(t * 6f)))
            s.step(dt)
            val d = sqrt(s.px[0] * s.px[0] + (s.pz[0] - wire.centerZ) * (s.pz[0] - wire.centerZ))
            assertTrue(
                abs(d - wire.radius) <= wire.slack + 1e-5f,
                "root left the wire annulus: d=$d",
            )
        }
    }

    @Test
    fun `spiral slop lets the root travel — the loose page feel`() {
        val s = strip(wire)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        s.grab(0.9f)
        // Wiggle the page side to side.
        repeat(240) { s.drag(0.10f, 0.05f); s.step(dt) }
        repeat(60) { minX = minOf(minX, s.px[0]); maxX = maxOf(maxX, s.px[0]); s.step(dt) }
        repeat(240) { s.drag(-0.10f, 0.05f); s.step(dt) }
        repeat(60) { minX = minOf(minX, s.px[0]); maxX = maxOf(maxX, s.px[0]); s.step(dt) }
        assertTrue(maxX - minX > 0.0008f, "spiral root never moved (${maxX - minX} m) — no slop")
    }

    @Test
    fun `spiral page turns fully and settles`() {
        val s = strip(wire)
        s.turnAndRelease(targetX = -0.1f, hint = -1)
        assertEquals(PageStrip.Settle.SETTLED_LEFT, s.settle)
    }

    @Test
    fun `spiral stays deterministic`() {
        fun run(): Pair<Float, Float> {
            val s = strip(wire)
            s.grab(0.85f)
            repeat(200) { s.drag(-0.02f, 0.05f); s.step(dt) }
            s.release(0)
            repeat(400) { s.step(dt) }
            return s.px[s.n - 1] to s.pz[s.n - 1]
        }
        val a = run()
        val b = run()
        assertTrue(
            a.first.toRawBits() == b.first.toRawBits() && a.second.toRawBits() == b.second.toRawBits(),
        )
    }
}
