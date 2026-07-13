package com.leaf.filament

import kotlin.math.sqrt

/**
 * Packs an orthonormal TBN basis into the quaternion Filament expects for the
 * TANGENTS attribute (the shader reconstructs the normal by rotating +Z).
 * Inputs must be normalized and right-handed (b = n x t); the paper extruder
 * produces analytic frames, so no Gram-Schmidt is done here.
 */
object TangentFrames {

    /** Writes quaternion (x, y, z, w) into [out] at [offset]. */
    @Suppress("CyclomaticComplexMethod")
    fun packQuat(
        tx: Float, ty: Float, tz: Float,
        bx: Float, by: Float, bz: Float,
        nx: Float, ny: Float, nz: Float,
        out: FloatArray, offset: Int,
    ) {
        // Rotation matrix columns: X->t, Y->b, Z->n (Shepperd's method).
        val m00 = tx; val m01 = bx; val m02 = nx
        val m10 = ty; val m11 = by; val m12 = ny
        val m20 = tz; val m21 = bz; val m22 = nz

        val trace = m00 + m11 + m22
        var qx: Float; var qy: Float; var qz: Float; var qw: Float
        when {
            trace > 0f -> {
                val s = sqrt(trace + 1f) * 2f
                qw = 0.25f * s
                qx = (m21 - m12) / s
                qy = (m02 - m20) / s
                qz = (m10 - m01) / s
            }
            m00 > m11 && m00 > m22 -> {
                val s = sqrt(1f + m00 - m11 - m22) * 2f
                qw = (m21 - m12) / s
                qx = 0.25f * s
                qy = (m01 + m10) / s
                qz = (m02 + m20) / s
            }
            m11 > m22 -> {
                val s = sqrt(1f + m11 - m00 - m22) * 2f
                qw = (m02 - m20) / s
                qx = (m01 + m10) / s
                qy = 0.25f * s
                qz = (m12 + m21) / s
            }
            else -> {
                val s = sqrt(1f + m22 - m00 - m11) * 2f
                qw = (m10 - m01) / s
                qx = (m02 + m20) / s
                qy = (m12 + m21) / s
                qz = 0.25f * s
            }
        }

        // Canonical sign (q and -q encode the same rotation).
        if (qw < 0f) {
            qx = -qx; qy = -qy; qz = -qz; qw = -qw
        }

        val invLen = 1f / sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
        out[offset] = qx * invLen
        out[offset + 1] = qy * invLen
        out[offset + 2] = qz * invLen
        out[offset + 3] = qw * invLen
    }
}
