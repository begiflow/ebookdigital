package com.leaf.physics.math

import kotlin.math.sqrt

/**
 * Minimal 3D vector for the paper simulation. Immutable by design: the PBD
 * solver (M6+) keeps particle state in flat FloatArrays for cache locality and
 * uses Vec3 at API boundaries only.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    infix fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    infix fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    fun lengthSquared(): Float = this dot this

    fun length(): Float = sqrt(lengthSquared())

    fun normalized(): Vec3 {
        val len = length()
        return if (len <= EPSILON) ZERO else this / len
    }

    companion object {
        const val EPSILON = 1e-6f
        val ZERO = Vec3(0f, 0f, 0f)
    }
}
