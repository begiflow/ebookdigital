package com.leaf.renderer

import com.leaf.physics.math.Vec3
import kotlin.math.tan

/**
 * Screen point -> world ray, computed analytically from the orbit rig's pose
 * (eye, look-at center, y-up) and the host's fixed vertical FOV — no matrix
 * round-trips through Filament (docs/03-RENDERER.md §6).
 */
class Raycaster(private val verticalFovDegrees: Float) {

    var origin = Vec3.ZERO
        private set

    /**
     * Computes the ray through screen pixel ([sx], [sy]); returns direction.
     */
    fun ray(
        eye: FloatArray, center: FloatArray,
        sx: Float, sy: Float,
        viewWidth: Float, viewHeight: Float,
    ): Vec3 {
        origin = Vec3(eye[0], eye[1], eye[2])
        val forward = Vec3(center[0] - eye[0], center[1] - eye[1], center[2] - eye[2]).normalized()
        val right = (forward cross Vec3(0f, 1f, 0f)).normalized()
        val up = right cross forward

        val tanV = tan(Math.toRadians(verticalFovDegrees / 2.0)).toFloat()
        val tanH = tanV * (viewWidth / viewHeight)
        val ndcX = 2f * sx / viewWidth - 1f
        val ndcY = 1f - 2f * sy / viewHeight

        return (forward + right * (ndcX * tanH) + up * (ndcY * tanV)).normalized()
    }

    /** Intersection with plane z = [z]; null if the ray runs parallel/away. */
    fun hitPlaneZ(dir: Vec3, z: Float): Vec3? {
        if (kotlin.math.abs(dir.z) < 1e-6f) return null
        val t = (z - origin.z) / dir.z
        if (t <= 0f) return null
        return origin + dir * t
    }

    /** Intersection with plane y = [y]; null if the ray runs parallel/away. */
    fun hitPlaneY(dir: Vec3, y: Float): Vec3? {
        if (kotlin.math.abs(dir.y) < 1e-6f) return null
        val t = (y - origin.y) / dir.y
        if (t <= 0f) return null
        return origin + dir * t
    }
}
