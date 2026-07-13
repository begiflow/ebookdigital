package com.leaf.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline startup benchmark for the engine sample. From M2 onward this
 * module gains FrameTimingMetric benchmarks over recorded page-turn gestures
 * (docs/07-ROADMAP.md) — frame timing gates every engine milestone.
 */
@RunWith(AndroidJUnit4::class)
class SampleStartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = "com.leaf.sample",
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}
