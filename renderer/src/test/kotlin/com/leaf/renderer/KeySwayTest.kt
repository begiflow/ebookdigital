package com.leaf.renderer

import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class KeySwayTest {

    private val dt = 1f / 120f

    private fun newSway() = KeySway(0.45f, -0.75f, -0.5f)

    private fun angleFromBase(dir: FloatArray): Float {
        val len = sqrt(0.45f * 0.45f + 0.75f * 0.75f + 0.5f * 0.5f)
        val dot = (dir[0] * 0.45f + dir[1] * -0.75f + dir[2] * -0.5f) / len
        return acos(dot.coerceIn(-1f, 1f))
    }

    @Test
    fun `first sample defines rest - no startup lurch`() {
        val sway = newSway()
        sway.setTilt(0f, 5f, 8f) // held at a reading angle from the start
        var dir = floatArrayOf(0f, 0f, 0f)
        repeat(120) { dir = sway.update(dt) }
        assertTrue(angleFromBase(dir) < 1e-3f, "light lurched at startup: ${angleFromBase(dir)}")
    }

    @Test
    fun `tilting sways immediately then settles back to rest`() {
        val sway = newSway()
        sway.setTilt(0f, 0f, 9.8f)
        repeat(120) { sway.update(dt) }

        // Tilt the device and hold.
        sway.setTilt(0f, 2.5f, 9.5f)
        var dir = floatArrayOf(0f, 0f, 0f)
        repeat(36) { dir = sway.update(dt) } // 0.3 s
        val peak = angleFromBase(dir)
        assertTrue(peak > 0.004f, "expected a visible sway, got $peak rad")

        // Held pose becomes the new rest: sway decays away.
        repeat((12f / dt).toInt()) { dir = sway.update(dt) }
        val settled = angleFromBase(dir)
        assertTrue(settled < 0.003f, "sway failed to settle: $settled rad")
        assertTrue(settled < peak / 2f)
    }

    @Test
    fun `sway never exceeds the max angle`() {
        val sway = newSway()
        sway.setTilt(0f, 0f, 9.8f)
        repeat(120) { sway.update(dt) }
        sway.setTilt(9.8f, 3f, 0.5f) // violent reorientation
        var worst = 0f
        repeat(240) {
            val a = angleFromBase(sway.update(dt))
            if (a > worst) worst = a
        }
        // Two axes can each clamp at max: bound is max * sqrt(2), plus slack.
        assertTrue(worst <= 0.02f * sqrt(2f) * 1.05f, "sway exceeded clamp: $worst rad")
    }

    @Test
    fun `output stays normalized`() {
        val sway = newSway()
        sway.setTilt(0f, 0f, 9.8f)
        repeat(60) { sway.update(dt) }
        sway.setTilt(5f, -5f, 7f)
        repeat(300) {
            val d = sway.update(dt)
            val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
            assertTrue(len in 0.999f..1.001f, "direction denormalized: $len")
        }
    }
}
