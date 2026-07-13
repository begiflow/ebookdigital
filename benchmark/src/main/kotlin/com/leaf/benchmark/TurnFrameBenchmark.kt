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
 * M10 hero-shadow workload: BookActivity in autopilot (cover open + endless
 * page turns) with shadow-mapped flight pages casting onto the spread.
 * "LeafFrame" section durations against the 8.3 ms budget verify the exit
 * criterion (hero shadow at 120 fps); acne/peter-panning across curl
 * extremes is eyeballed on the same run (docs/04 §3).
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class TurnFrameBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun shadowedTurningFrames() {
        benchmarkRule.measureRepeated(
            packageName = "com.leaf.sample",
            metrics = listOf(TraceSectionMetric("LeafFrame", TraceSectionMetric.Mode.Sum)),
            iterations = 3,
            startupMode = StartupMode.COLD,
        ) {
            startActivityAndWait(
                android.content.Intent()
                    .setClassName("com.leaf.sample", "com.leaf.sample.BookActivity")
                    .putExtra("autoplay", true),
            )
            // Cover open (~3 s) + a dozen shadowed page turns.
            Thread.sleep(15_000)
        }
    }
}
