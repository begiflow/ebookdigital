package com.leaf.renderer

import com.google.android.filament.Camera
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Inspection camera for the closed book: constrained orbit + dolly zoom with
 * exponential smoothing, so motion eases out like a hand settling
 * (docs/03-RENDERER.md §1). Angles in radians, distances in meters.
 */
class OrbitCameraRig(
    initialDistance: Float,
    private val minDistance: Float,
    private val maxDistance: Float,
) {
    private var azimuth = 0f
    private var elevation = DEFAULT_ELEVATION
    private var distance = initialDistance

    private var targetAzimuth = azimuth
    private var targetElevation = elevation
    private var targetDistance = distance

    fun dragBy(deltaAzimuth: Float, deltaElevation: Float) {
        targetAzimuth = (targetAzimuth + deltaAzimuth).coerceIn(-MAX_AZIMUTH, MAX_AZIMUTH)
        targetElevation = (targetElevation + deltaElevation).coerceIn(MIN_ELEVATION, MAX_ELEVATION)
    }

    fun zoomBy(scaleFactor: Float) {
        if (scaleFactor > 0f) {
            targetDistance = (targetDistance / scaleFactor).coerceIn(minDistance, maxDistance)
        }
    }

    fun update(dtSeconds: Float) {
        // Exponential approach: frame-rate independent critically-damped feel.
        val k = 1f - exp(-SMOOTHING_RATE * dtSeconds)
        azimuth += (targetAzimuth - azimuth) * k
        elevation += (targetElevation - elevation) * k
        distance += (targetDistance - distance) * k
    }

    /** Last applied camera pose; consumed by the renderer's ray caster. */
    val lastEye = FloatArray(3)
    val lastCenter = FloatArray(3)

    fun applyTo(
        camera: Camera,
        centerX: Float, centerY: Float, centerZ: Float,
        distanceScale: Float = 1f,
    ) {
        val d = distance * distanceScale
        val cosEl = cos(elevation)
        val eyeX = centerX + d * cosEl * sin(azimuth)
        val eyeY = centerY + d * sin(elevation)
        val eyeZ = centerZ + d * cosEl * cos(azimuth)
        lastEye[0] = eyeX; lastEye[1] = eyeY; lastEye[2] = eyeZ
        lastCenter[0] = centerX; lastCenter[1] = centerY; lastCenter[2] = centerZ
        camera.lookAt(
            eyeX.toDouble(), eyeY.toDouble(), eyeZ.toDouble(),
            centerX.toDouble(), centerY.toDouble(), centerZ.toDouble(),
            0.0, 1.0, 0.0,
        )
    }

    private companion object {
        const val DEFAULT_ELEVATION = 0.18f
        const val MAX_AZIMUTH = 1.35f          // ~77 deg: enough to inspect the spine
        const val MIN_ELEVATION = -0.55f
        const val MAX_ELEVATION = 0.95f
        const val SMOOTHING_RATE = 12f
    }
}
