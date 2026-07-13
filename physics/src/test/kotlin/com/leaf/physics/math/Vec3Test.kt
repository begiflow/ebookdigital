package com.leaf.physics.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Vec3Test {

    @Test
    fun `arithmetic operators`() {
        val a = Vec3(1f, 2f, 3f)
        val b = Vec3(4f, 5f, 6f)
        assertEquals(Vec3(5f, 7f, 9f), a + b)
        assertEquals(Vec3(-3f, -3f, -3f), a - b)
        assertEquals(Vec3(2f, 4f, 6f), a * 2f)
        assertEquals(Vec3(0.5f, 1f, 1.5f), a / 2f)
    }

    @Test
    fun `dot product`() {
        assertEquals(32f, Vec3(1f, 2f, 3f) dot Vec3(4f, 5f, 6f))
    }

    @Test
    fun `cross product follows right-hand rule`() {
        val x = Vec3(1f, 0f, 0f)
        val y = Vec3(0f, 1f, 0f)
        assertEquals(Vec3(0f, 0f, 1f), x cross y)
        assertEquals(Vec3(0f, 0f, -1f), y cross x)
    }

    @Test
    fun `normalized has unit length`() {
        val n = Vec3(3f, 4f, 0f).normalized()
        assertTrue(kotlin.math.abs(n.length() - 1f) < Vec3.EPSILON)
        assertEquals(Vec3(0.6f, 0.8f, 0f), n)
    }

    @Test
    fun `normalizing zero vector returns zero, not NaN`() {
        assertEquals(Vec3.ZERO, Vec3.ZERO.normalized())
    }
}
