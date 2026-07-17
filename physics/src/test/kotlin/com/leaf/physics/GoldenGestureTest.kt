package com.leaf.physics

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden-gesture tests (M8, docs/05-PHYSICS.md §6): scripted gesture traces
 * replayed against committed tip trajectories. These catch FEEL regressions —
 * a constant retune that changes how a released page flies fails here even
 * when every invariant test stays green.
 *
 * Goldens are the free-edge tip (x, z) sampled every 6 steps for the 60 steps
 * after release, plus the settle verdict after 3 seconds. Tolerance is loose
 * enough for libm variance across JVMs (exp/sin are not bit-specified), tight
 * enough that any intentional physics change must re-bless the data.
 * Re-bless by printing the same samples from a scratch runner.
 */
class GoldenGestureTest {

    private val dt = 1f / 120f

    private fun newStrip() = PageStrip(PageParams(widthMeters = 0.14f)).apply {
        setSurfaces(left = 0.004f, right = 0.008f, slopeLeft = 0.19f)
        resetFlat(onRight = true, bowHeight = 0.0035f)
    }

    private fun assertTrace(
        prepare: PageStrip.() -> Unit,
        tipX: FloatArray,
        tipZ: FloatArray,
        settle: PageStrip.Settle,
    ) {
        val strip = newStrip()
        strip.prepare()
        var sample = 0
        repeat(60) { i ->
            strip.step(dt)
            if ((i + 1) % 6 == 0) {
                val x = strip.px[strip.n - 1]
                val z = strip.pz[strip.n - 1]
                assertTrue(
                    abs(x - tipX[sample]) < TOLERANCE && abs(z - tipZ[sample]) < TOLERANCE,
                    "trajectory diverged at sample $sample: ($x, $z) expected " +
                        "(${tipX[sample]}, ${tipZ[sample]})",
                )
                sample++
            }
        }
        repeat(300) { strip.step(dt) }
        assertEquals(settle, strip.settle, "settle verdict changed")
    }

    @Test
    fun `carry past the spine and drop`() = assertTrace(
        prepare = {
            grab(0.9f)
            repeat(240) { drag(-0.09f, 0.05f); step(dt) }
            release(0)
        },
        tipX = floatArrayOf(-0.103769f, -0.136657f, -0.136656f, -0.136432f, -0.136460f, -0.136488f, -0.136489f, -0.136487f, -0.136486f, -0.136486f),
        tipZ = floatArrayOf(0.047634f, 0.029965f, 0.029965f, 0.029922f, 0.029927f, 0.029933f, 0.029933f, 0.029932f, 0.029932f, 0.029932f),
        settle = PageStrip.Settle.SETTLED_LEFT,
    )

    @Test
    fun `lift on the right and let go`() = assertTrace(
        prepare = {
            grab(0.9f)
            repeat(240) { drag(0.09f, 0.06f); step(dt) }
            release(0)
        },
        tipX = floatArrayOf(0.129326f, 0.136465f, 0.135386f, 0.137097f, 0.136413f, 0.136592f, 0.136513f, 0.136565f, 0.136527f, 0.136556f),
        tipZ = floatArrayOf(0.047835f, 0.008000f, 0.008000f, 0.008000f, 0.008000f, 0.008000f, 0.008000f, 0.008000f, 0.008000f, 0.008000f),
        settle = PageStrip.Settle.SETTLED_RIGHT,
    )

    @Test
    fun `flick from the right side finishes the turn`() = assertTrace(
        prepare = {
            grab(0.9f)
            repeat(240) { drag(0.09f, 0.06f); step(dt) }
            release(-1)
        },
        tipX = floatArrayOf(0.082590f, 0.024617f, -0.033555f, -0.106610f, -0.135145f, -0.136816f, -0.136310f, -0.136423f, -0.136494f, -0.136494f),
        tipZ = floatArrayOf(0.050895f, 0.034648f, 0.034201f, 0.047539f, 0.029678f, 0.029995f, 0.029899f, 0.029920f, 0.029934f, 0.029934f),
        settle = PageStrip.Settle.SETTLED_LEFT,
    )

    @Test
    fun `riffle kick flips the page unheld`() = assertTrace(
        prepare = {
            // Mirrors BookScene.riffleTurn's kick.
            grab(0.92f)
            fling(speedX = -1.4f, lift = 0.55f)
            release(-1)
        },
        tipX = floatArrayOf(0.092741f, 0.041979f, -0.003890f, -0.047785f, -0.090190f, -0.137406f, -0.135556f, -0.136620f, -0.136563f, -0.136485f),
        tipZ = floatArrayOf(0.017122f, 0.008000f, 0.007472f, 0.013097f, 0.029700f, 0.035277f, 0.029756f, 0.029958f, 0.029947f, 0.029932f),
        settle = PageStrip.Settle.SETTLED_LEFT,
    )

    @Test
    fun `buckled page relaxes back to its side`() = assertTrace(
        prepare = {
            grab(1f)
            repeat(360) { drag(0.042f, 0.02f); step(dt) }
            release(0)
        },
        tipX = floatArrayOf(0.054593f, 0.072516f, 0.088577f, 0.089021f, 0.079168f, 0.136109f, 0.135230f, 0.135707f, 0.136777f, 0.136486f),
        tipZ = floatArrayOf(0.008000f, 0.008000f, 0.008000f, 0.008005f, 0.022307f, 0.024574f, 0.008000f, 0.008000f, 0.008000f, 0.008000f),
        settle = PageStrip.Settle.SETTLED_RIGHT,
    )

    @Test
    fun `released pages settle within half a second`() {
        // The pool (M8) recycles slots only after settle detection — it must
        // trail the visual rest by frames, not seconds.
        val strip = newStrip()
        strip.grab(0.9f)
        repeat(240) { strip.drag(-0.09f, 0.05f); strip.step(dt) }
        strip.release(0)
        var steps = 0
        while (strip.settle == PageStrip.Settle.IN_FLIGHT && steps < 240) {
            strip.step(dt)
            steps++
        }
        assertTrue(steps < 60, "settle detection too slow: $steps steps")
    }

    private companion object {
        const val TOLERANCE = 2e-3f
    }
}
