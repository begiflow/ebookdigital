package com.leaf.renderer

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import com.leaf.renderer.sound.PaperSounds
import java.io.File

/**
 * Maps [FeelEvent]s to sound + haptics (M8). Sounds are synthesized paper
 * noise (see [PaperSounds]) written once to cache as WAV and played through
 * SoundPool with slight rate jitter so repeats never sound stamped. Haptics
 * use the platform's predefined ticks — short and dry, like paper.
 *
 * Zero APK asset bytes: the ~12 clips are generated at first attach
 * (deterministic seeds) and reused from cache afterwards.
 */
class FeelFeedback(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // Variant pools per event family; round-robined at play time.
    private val flips = IntArray(3)
    private val lands = IntArray(3)
    private val ticks = IntArray(3)
    private var grab = 0
    private var coverOpen = 0
    private var coverClose = 0

    private var flipCursor = 0
    private var landCursor = 0
    private var tickCursor = 0
    private var jitterPhase = 0

    init {
        val dir = File(context.cacheDir, "leaf-sounds").apply { mkdirs() }
        fun load(name: String, pcm: ShortArray): Int {
            val file = File(dir, "$name.wav")
            if (!file.exists()) PaperSounds.writeWav(file, pcm)
            return pool.load(file.absolutePath, 1)
        }
        for (i in 0 until 3) {
            flips[i] = load("flip$i", PaperSounds.flip(seed = 20L + i))
            lands[i] = load("land$i", PaperSounds.landFlap(seed = 40L + i))
            ticks[i] = load("tick$i", PaperSounds.riffleTick(seed = 60L + i))
        }
        grab = load("grab", PaperSounds.grabTick(seed = 5L))
        coverOpen = load("cover-open", PaperSounds.coverThump(seed = 80L, open = true))
        coverClose = load("cover-close", PaperSounds.coverThump(seed = 81L, open = false))
    }

    fun on(event: FeelEvent) {
        when (event) {
            FeelEvent.PAGE_GRAB -> {
                play(grab, volume = 0.35f)
                tick(VibrationEffect.EFFECT_TICK)
            }
            FeelEvent.PAGE_FLICK -> play(nextFlip(), volume = 0.6f)
            FeelEvent.PAGE_LAND_SOFT -> {
                play(nextLand(), volume = 0.5f)
                tick(VibrationEffect.EFFECT_TICK)
            }
            FeelEvent.PAGE_LAND_FLICK -> {
                play(nextLand(), volume = 0.7f)
                tick(VibrationEffect.EFFECT_CLICK)
            }
            FeelEvent.RIFFLE_TICK -> {
                play(nextTick(), volume = 0.45f)
                tick(VibrationEffect.EFFECT_TICK)
            }
            FeelEvent.COVER_OPEN -> {
                play(coverOpen, volume = 0.55f)
                tick(VibrationEffect.EFFECT_CLICK)
            }
            FeelEvent.COVER_CLOSE -> {
                play(coverClose, volume = 0.7f)
                tick(VibrationEffect.EFFECT_HEAVY_CLICK)
            }
        }
    }

    fun release() {
        pool.release()
    }

    private fun nextFlip(): Int {
        flipCursor = (flipCursor + 1) % flips.size
        return flips[flipCursor]
    }

    private fun nextLand(): Int {
        landCursor = (landCursor + 1) % lands.size
        return lands[landCursor]
    }

    private fun nextTick(): Int {
        tickCursor = (tickCursor + 1) % ticks.size
        return ticks[tickCursor]
    }

    private fun play(id: Int, volume: Float) {
        // ±4% rate jitter, cycling — cheap decorrelation, no allocation.
        jitterPhase = (jitterPhase + 1) % JITTER.size
        pool.play(id, volume, volume, 1, 0, JITTER[jitterPhase])
    }

    private fun tick(effect: Int) {
        vibrator?.vibrate(VibrationEffect.createPredefined(effect))
    }

    private companion object {
        val JITTER = floatArrayOf(1f, 1.04f, 0.96f, 1.02f, 0.98f)
    }
}
