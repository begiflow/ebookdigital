package com.leaf.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame-loop benchmark for the dynamic-mesh workload. "LeafFrame" wraps
 * sim + vertex upload + render submission in FilamentHost; section count over
 * the measured window gives achieved fps, section duration gives CPU frame
 * cost (budget: docs/03-RENDERER.md §8). HWUI-based FrameTimingMetric can't
 * see Filament's SurfaceView frames, hence trace sections.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class RibbonFrameBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun ribbonSteadyStateFrames() = measureActivity("com.leaf.sample.RibbonActivity")

    @Test
    fun bookSteadyStateFrames() = measureActivity("com.leaf.sample.BookActivity")

    private fun measureActivity(activityClass: String) {
        benchmarkRule.measureRepeated(
            packageName = "com.leaf.sample",
            metrics = listOf(TraceSectionMetric("LeafFrame", TraceSectionMetric.Mode.Sum)),
            iterations = 3,
            startupMode = StartupMode.COLD,
        ) {
            startActivityAndWait(
                android.content.Intent().setClassName("com.leaf.sample", activityClass),
            )
            // Measure 5 seconds of steady-state frame production.
            Thread.sleep(5_000)
        }
    }
}
