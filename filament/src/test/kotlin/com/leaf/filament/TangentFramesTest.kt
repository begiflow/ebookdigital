package com.leaf.filament

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class TangentFramesTest {

    @Test
    fun `identity frame packs to identity quaternion`() {
        val q = pack(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        assertQuatEquals(floatArrayOf(0f, 0f, 0f, 1f), q)
    }

    @Test
    fun `90 degree rotation about x`() {
        // X->X, Y->Z, Z->-Y  =>  q = (sin45, 0, 0, cos45)
        val q = pack(1f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, 0f)
        val s = sqrt(0.5f)
        assertQuatEquals(floatArrayOf(s, 0f, 0f, s), q)
    }

    @Test
    fun `packed quaternion rotates +Z onto the normal for arbitrary frames`() {
        // Frames like the wave extruder produces: tilted around both axes.
        for (angle in listOf(0.1f, 0.7f, 1.3f, 2.9f)) {
            val c = cos(angle); val s = sin(angle)
            // Rotate the identity frame about Y by angle: X->(c,0,-s), Z->(s,0,c)
            val q = pack(c, 0f, -s, 0f, 1f, 0f, s, 0f, c)
            val rotated = rotateZ(q)
            assertTrue(
                abs(rotated[0] - s) < EPS && abs(rotated[1]) < EPS && abs(rotated[2] - c) < EPS,
                "angle=$angle got=${rotated.toList()}",
            )
        }
    }

    @Test
    fun `output is always unit length with non-negative w`() {
        val q = pack(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f) // 90 deg about z
        val len = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        assertTrue(abs(len - 1f) < EPS)
        assertTrue(q[3] >= 0f)
    }

    private fun pack(
        tx: Float, ty: Float, tz: Float,
        bx: Float, by: Float, bz: Float,
        nx: Float, ny: Float, nz: Float,
    ): FloatArray {
        val out = FloatArray(4)
        TangentFrames.packQuat(tx, ty, tz, bx, by, bz, nx, ny, nz, out, 0)
        return out
    }

    /** Rotates (0,0,1) by quaternion q. */
    private fun rotateZ(q: FloatArray): FloatArray {
        val (x, y, z, w) = q
        return floatArrayOf(
            2f * (x * z + w * y),
            2f * (y * z - w * x),
            1f - 2f * (x * x + y * y),
        )
    }

    private fun assertQuatEquals(expected: FloatArray, actual: FloatArray) {
        for (i in 0..3) {
            assertTrue(
                abs(expected[i] - actual[i]) < EPS,
                "component $i: expected=${expected.toList()} actual=${actual.toList()}",
            )
        }
    }

    private operator fun FloatArray.component4(): Float = this[3]

    private companion object {
        const val EPS = 1e-4f
    }
}
