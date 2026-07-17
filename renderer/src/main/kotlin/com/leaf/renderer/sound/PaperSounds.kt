package com.leaf.renderer.sound

import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural paper sounds (M8): short mono 16-bit PCM clips built from
 * band-passed noise with hand-shaped envelopes — a page flip is a filtered
 * noise swell, a landing is two quick flaps, a riffle tick is a bright snap,
 * the cover is a low thump with a noise transient. Deterministic per seed,
 * synthesized once at startup, zero APK asset bytes (docs/04 §6 budget).
 */
object PaperSounds {

    const val SAMPLE_RATE = 44_100

    /** Page released with momentum: airy whoosh, band sweeping down. */
    fun flip(seed: Long, durationMs: Int = 190): ShortArray {
        val n = samples(durationMs)
        val random = Random(seed)
        val filter = Biquad()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i / (n - 1f)
            // Band center glides 2.4k -> 900 Hz as the page decelerates.
            filter.bandpass(2_400f - 1_500f * t, q = 1.1f)
            val env = hump(t, peak = 0.35f) // swell early, tail late
            out[i] = filter.process(random.nextFloat() * 2f - 1f) * env
        }
        return toPcm(out, gain = 0.8f)
    }

    /** Settle: two quick lowpassed flaps (paper slapping the stack). */
    fun landFlap(seed: Long, durationMs: Int = 110): ShortArray {
        val n = samples(durationMs)
        val random = Random(seed)
        val filter = Biquad().apply { bandpass(650f, q = 0.8f) }
        val out = FloatArray(n)
        val second = (n * 0.42f).toInt()
        for (i in 0 until n) {
            val a = burst(i, start = 0, length = n / 3)
            val b = burst(i, start = second, length = n / 3) * 0.7f
            out[i] = filter.process((random.nextFloat() * 2f - 1f)) * (a + b)
        }
        return toPcm(out, gain = 0.9f)
    }

    /** One page of a thumb riffle: bright, tiny snap. */
    fun riffleTick(seed: Long, durationMs: Int = 36): ShortArray {
        val n = samples(durationMs)
        val random = Random(seed)
        val filter = Biquad().apply { bandpass(3_600f, q = 1.4f) }
        val out = FloatArray(n)
        for (i in 0 until n) {
            val env = exp(-7f * i / n.toFloat())
            out[i] = filter.process(random.nextFloat() * 2f - 1f) * env
        }
        return toPcm(out, gain = 0.7f)
    }

    /** Finger meets paper: almost subliminal dry tick. */
    fun grabTick(seed: Long, durationMs: Int = 22): ShortArray {
        val n = samples(durationMs)
        val random = Random(seed)
        val filter = Biquad().apply { bandpass(2_000f, q = 0.9f) }
        val out = FloatArray(n)
        for (i in 0 until n) {
            val env = exp(-10f * i / n.toFloat())
            out[i] = filter.process(random.nextFloat() * 2f - 1f) * env
        }
        return toPcm(out, gain = 0.5f)
    }

    /** Cover detent: low body resonance + a noise transient at contact. */
    fun coverThump(seed: Long, open: Boolean, durationMs: Int = 140): ShortArray {
        val n = samples(durationMs)
        val random = Random(seed)
        val filter = Biquad().apply { bandpass(900f, q = 0.7f) }
        val out = FloatArray(n)
        val body = if (open) 110f else 85f
        var phase = 0f
        for (i in 0 until n) {
            val t = i / n.toFloat()
            phase += (2f * PI.toFloat() * body) / SAMPLE_RATE
            val tone = sin(phase) * exp(-9f * t) * 0.8f
            val contact = filter.process(random.nextFloat() * 2f - 1f) * exp(-22f * t) * 0.5f
            out[i] = tone + contact
        }
        return toPcm(out, gain = 0.9f)
    }

    /** Minimal RIFF/WAVE writer (PCM 16-bit mono). */
    fun writeWav(file: File, pcm: ShortArray) {
        val dataBytes = pcm.size * 2
        val header = ByteArray(44)
        fun put(offset: Int, value: Int, bytes: Int) {
            for (b in 0 until bytes) header[offset + b] = (value shr (8 * b)).toByte()
        }
        "RIFF".forEachIndexed { i, c -> header[i] = c.code.toByte() }
        put(4, 36 + dataBytes, 4)
        "WAVE".forEachIndexed { i, c -> header[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> header[12 + i] = c.code.toByte() }
        put(16, 16, 4) // fmt chunk size
        put(20, 1, 2) // PCM
        put(22, 1, 2) // mono
        put(24, SAMPLE_RATE, 4)
        put(28, SAMPLE_RATE * 2, 4) // byte rate
        put(32, 2, 2) // block align
        put(34, 16, 2) // bits per sample
        "data".forEachIndexed { i, c -> header[36 + i] = c.code.toByte() }
        put(40, dataBytes, 4)

        val body = ByteArray(dataBytes)
        for (i in pcm.indices) {
            body[2 * i] = (pcm[i].toInt() and 0xFF).toByte()
            body[2 * i + 1] = (pcm[i].toInt() shr 8).toByte()
        }
        file.outputStream().use {
            it.write(header)
            it.write(body)
        }
    }

    // ------------------------------ helpers --------------------------------

    private fun samples(durationMs: Int) = SAMPLE_RATE * durationMs / 1000

    /** Fast-attack hump peaking at [peak] of the clip, smooth decay after. */
    private fun hump(t: Float, peak: Float): Float =
        if (t < peak) {
            val u = t / peak
            u * u * (3f - 2f * u)
        } else {
            val u = (t - peak) / (1f - peak)
            exp(-3.2f * u) * (1f - u * 0.15f)
        }

    private fun burst(i: Int, start: Int, length: Int): Float {
        if (i < start || i >= start + length) return 0f
        val t = (i - start) / length.toFloat()
        return exp(-6f * t) * minOf(1f, t * 12f)
    }

    private fun toPcm(signal: FloatArray, gain: Float): ShortArray {
        var maxAbs = 1e-6f
        for (s in signal) if (kotlin.math.abs(s) > maxAbs) maxAbs = kotlin.math.abs(s)
        val scale = gain / maxAbs * Short.MAX_VALUE
        return ShortArray(signal.size) { i ->
            (signal[i] * scale).toInt().coerceIn(-32_768, 32_767).toShort()
        }
    }

    /** Direct-form-I band-pass biquad (constant-skirt), retunable per sample. */
    private class Biquad {
        private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
        private var a1 = 0f; private var a2 = 0f
        private var x1 = 0f; private var x2 = 0f
        private var y1 = 0f; private var y2 = 0f

        fun bandpass(centerHz: Float, q: Float) {
            val w = 2f * PI.toFloat() * centerHz / SAMPLE_RATE
            val alpha = sin(w) / (2f * q)
            val a0 = 1f + alpha
            b0 = alpha / a0
            b1 = 0f
            b2 = -alpha / a0
            a1 = (-2f * cos(w)) / a0
            a2 = (1f - alpha) / a0
        }

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x
            y2 = y1; y1 = y
            return y
        }
    }
}
