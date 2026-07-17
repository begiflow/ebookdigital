package com.leaf.camera.scan

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuadTest {

    @Test
    fun `corners are canonicalized to TL TR BR BL from any order`() {
        val shuffled = listOf(
            0.9f to 0.8f, // BR
            0.1f to 0.1f, // TL
            0.1f to 0.8f, // BL
            0.9f to 0.1f, // TR
        )
        val q = Quad.ordered(shuffled)
        assertEquals(0.1f, q.x0); assertEquals(0.1f, q.y0)
        assertEquals(0.9f, q.x1); assertEquals(0.1f, q.y1)
        assertEquals(0.9f, q.x2); assertEquals(0.8f, q.y2)
        assertEquals(0.1f, q.x3); assertEquals(0.8f, q.y3)
    }

    @Test
    fun `area matches the rectangle it encloses`() {
        val q = Quad.of(0.1f, 0.1f, 0.9f, 0.1f, 0.9f, 0.8f, 0.1f, 0.8f)
        assertTrue(abs(q.area() - 0.8f * 0.7f) < 1e-5f)
    }

    @Test
    fun `convexity detects bow-ties`() {
        val convex = Quad.of(0.1f, 0.1f, 0.9f, 0.15f, 0.85f, 0.8f, 0.15f, 0.75f)
        assertTrue(convex.isConvex())
        // A dented quad: one corner pushed deep inside the hull.
        val dented = Quad.of(0.1f, 0.1f, 0.9f, 0.1f, 0.45f, 0.45f, 0.1f, 0.9f)
        assertFalse(dented.isConvex())
    }

    @Test
    fun `corner distance measures the worst corner`() {
        val a = Quad.of(0.1f, 0.1f, 0.9f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f)
        val b = Quad.of(0.1f, 0.1f, 0.9f, 0.1f, 0.9f, 0.9f, 0.1f, 0.95f)
        assertTrue(abs(a.maxCornerDistance(b) - 0.05f) < 1e-5f)
    }
}
