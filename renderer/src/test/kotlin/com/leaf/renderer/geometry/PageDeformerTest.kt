package com.leaf.renderer.geometry

import com.leaf.physics.PageParams
import com.leaf.physics.PageStrip
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class PageDeformerTest {

    private val dt = 1f / 120f
    private val width = 0.14f

    /** A strip dragged into a healthy mid-turn curl. */
    private fun curledStrip(): PageStrip {
        val strip = PageStrip(PageParams(widthMeters = width))
        strip.setSurfaces(left = 0.004f, right = 0.008f, slopeLeft = -0.19f)
        strip.resetFlat(onRight = true, bowHeight = 0.0035f)
        strip.grab(1f)
        repeat(300) { strip.drag(0.02f, 0.08f); strip.step(dt) }
        return strip
    }

    private fun PageDeformer.rowChordLength(r: Int): Float {
        var sum = 0f
        for (c in 0 until cols - 1) {
            val i = r * cols + c
            val dx = posX[i + 1] - posX[i]
            val dz = posZ[i + 1] - posZ[i]
            sum += sqrt(dx * dx + dz * dz)
        }
        return sum
    }

    @Test
    fun `zero skew extrudes a pure ruled surface`() {
        val deformer = PageDeformer(cols = 32, rows = 8)
        deformer.deform(curledStrip(), grabV = 1f, skew = 0f, restAngle = 0f)

        for (r in 1 until deformer.rows) {
            for (c in 0 until deformer.cols) {
                val i = r * deformer.cols + c
                assertTrue(abs(deformer.posX[i] - deformer.posX[c]) < 1e-6f, "x differs at r=$r c=$c")
                assertTrue(abs(deformer.posZ[i] - deformer.posZ[c]) < 1e-6f, "z differs at r=$r c=$c")
                assertTrue(abs(deformer.dPosXdv[i]) < 1e-6f && abs(deformer.dPosZdv[i]) < 1e-6f)
            }
        }
    }

    @Test
    fun `skew preserves arc length on every row`() {
        val deformer = PageDeformer(cols = 32, rows = 8)
        deformer.deform(curledStrip(), grabV = 1f, skew = 0.65f, restAngle = 0f)

        val base = deformer.rowChordLength(deformer.rows - 1) // grab row
        for (r in 0 until deformer.rows) {
            val len = deformer.rowChordLength(r)
            assertTrue(
                abs(len - base) < 1e-4f,
                "row $r stretched under skew: $len vs $base",
            )
        }
    }

    @Test
    fun `grabbed corner leads and the far edge lags`() {
        // Carry the page past the spine, held at the top corner (v=1).
        val strip = PageStrip(PageParams(widthMeters = width))
        strip.setSurfaces(left = 0.004f, right = 0.008f, slopeLeft = -0.19f)
        strip.resetFlat(onRight = true, bowHeight = 0.0035f)
        strip.grab(1f)
        repeat(300) { strip.drag(-0.05f, 0.06f); strip.step(dt) }

        val deformer = PageDeformer(cols = 32, rows = 8)
        deformer.deform(strip, grabV = 1f, skew = 0.65f, restAngle = 0f)

        // Turn progress per row = how far the free edge has swung left.
        fun tipX(r: Int) = deformer.posX[r * deformer.cols + deformer.cols - 1]

        val grabTip = tipX(deformer.rows - 1)
        val farTip = tipX(0)
        assertTrue(
            farTip > grabTip + 0.015f,
            "far edge should trail the grabbed corner: far=$farTip grab=$grabTip",
        )
        // Rows lag monotonically toward the far edge.
        var prev = grabTip
        for (r in deformer.rows - 2 downTo 0) {
            val tip = tipX(r)
            assertTrue(tip >= prev - 1e-5f, "turn progress not monotonic at r=$r")
            prev = tip
        }
    }

    @Test
    fun `grab row is unaffected by skew`() {
        val strip = curledStrip()
        val plain = PageDeformer(cols = 32, rows = 8)
        val skewed = PageDeformer(cols = 32, rows = 8)
        plain.deform(strip, grabV = 1f, skew = 0f, restAngle = 0f)
        skewed.deform(strip, grabV = 1f, skew = 0.8f, restAngle = 0f)

        val r = plain.rows - 1
        for (c in 0 until plain.cols) {
            val i = r * plain.cols + c
            assertTrue(abs(plain.posX[i] - skewed.posX[i]) < 1e-5f)
            assertTrue(abs(plain.posZ[i] - skewed.posZ[i]) < 1e-5f)
        }
    }

    @Test
    fun `analytic row derivative matches finite differences`() {
        // grabV=1 keeps dw/dv constant across rows (no kink); a fine row grid
        // makes the central difference meaningful.
        val deformer = PageDeformer(cols = 32, rows = 33)
        deformer.deform(curledStrip(), grabV = 1f, skew = 0.5f, restAngle = 0f)

        val dv = 1f / (deformer.rows - 1)
        var checked = 0
        for (r in 1 until deformer.rows - 1) {
            for (c in 8 until deformer.cols step 6) {
                val i = r * deformer.cols + c
                val up = (r + 1) * deformer.cols + c
                val dn = (r - 1) * deformer.cols + c
                val fdX = (deformer.posX[up] - deformer.posX[dn]) / (2f * dv)
                val fdZ = (deformer.posZ[up] - deformer.posZ[dn]) / (2f * dv)
                val scale = maxOf(abs(fdX), abs(fdZ), 1e-3f)
                assertTrue(
                    abs(deformer.dPosXdv[i] - fdX) < scale * 0.08f + 1e-4f,
                    "dX/dv mismatch r=$r c=$c analytic=${deformer.dPosXdv[i]} fd=$fdX",
                )
                assertTrue(
                    abs(deformer.dPosZdv[i] - fdZ) < scale * 0.08f + 1e-4f,
                    "dZ/dv mismatch r=$r c=$c analytic=${deformer.dPosZdv[i]} fd=$fdZ",
                )
                checked++
            }
        }
        assertTrue(checked > 50, "test should cover the grid, checked=$checked")
    }

    @Test
    fun `column tangents stay unit length`() {
        val deformer = PageDeformer(cols = 32, rows = 8)
        deformer.deform(curledStrip(), grabV = 0.2f, skew = 0.7f, restAngle = 0f)
        for (i in 0 until deformer.cols * deformer.rows) {
            val len = sqrt(deformer.tanX[i] * deformer.tanX[i] + deformer.tanZ[i] * deformer.tanZ[i])
            assertTrue(abs(len - 1f) < 1e-4f, "tangent not unit at $i: $len")
        }
    }
}
