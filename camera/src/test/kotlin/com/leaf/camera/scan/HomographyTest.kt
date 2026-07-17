package com.leaf.camera.scan

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class HomographyTest {

    private fun assertClose(actual: Pair<Float, Float>, x: Float, y: Float) {
        assertTrue(
            abs(actual.first - x) < 1e-4f && abs(actual.second - y) < 1e-4f,
            "expected ($x, $y) got $actual",
        )
    }

    @Test
    fun `maps unit square corners onto the quad exactly`() {
        val quad = Quad.of(0.2f, 0.1f, 0.85f, 0.15f, 0.8f, 0.9f, 0.15f, 0.8f)
        val h = Homography.unitSquareTo(quad)
        assertClose(h.map(0f, 0f), quad.x0, quad.y0)
        assertClose(h.map(1f, 0f), quad.x1, quad.y1)
        assertClose(h.map(1f, 1f), quad.x2, quad.y2)
        assertClose(h.map(0f, 1f), quad.x3, quad.y3)
    }

    @Test
    fun `identity square yields identity mapping`() {
        val h = Homography.unitSquareTo(Quad.of(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))
        assertClose(h.map(0.3f, 0.7f), 0.3f, 0.7f)
        val m = h.matrix()
        assertTrue(abs(m[0] - 1f) < 1e-5f && abs(m[4] - 1f) < 1e-5f && abs(m[8] - 1f) < 1e-5f)
    }

    @Test
    fun `interior points stay inside a convex quad`() {
        val quad = Quad.of(0.2f, 0.1f, 0.9f, 0.2f, 0.8f, 0.85f, 0.1f, 0.75f)
        val h = Homography.unitSquareTo(quad)
        for (u in 1..9) {
            for (v in 1..9) {
                val (x, y) = h.map(u / 10f, v / 10f)
                assertTrue(x in 0.05f..0.95f && y in 0.05f..0.95f, "escaped hull at ($u,$v): ($x,$y)")
            }
        }
    }

    @Test
    fun `perspective foreshortening is monotone`() {
        // A quad narrower at the top: equal v steps must shrink near the top.
        val quad = Quad.of(0.35f, 0.1f, 0.65f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f)
        val h = Homography.unitSquareTo(quad)
        val (topX0, _) = h.map(0f, 0.1f)
        val (topX1, _) = h.map(1f, 0.1f)
        val (botX0, _) = h.map(0f, 0.9f)
        val (botX1, _) = h.map(1f, 0.9f)
        assertTrue(topX1 - topX0 < botX1 - botX0, "top span should be narrower")
    }
}
