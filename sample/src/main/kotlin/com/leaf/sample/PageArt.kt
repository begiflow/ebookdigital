package com.leaf.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlin.random.Random

/**
 * Generates vaccination-record pages so the open spread can be judged for
 * color fidelity before the camera exists (M12). Cream paper, printed table,
 * handwriting-ish entries — page index is drawn so spread jumps are
 * verifiable in screenshots.
 */
object PageArt {

    fun page(index: Int, width: Int = 512, height: Int = 726): Bitmap {
        // Sheet 1 front carries the color-fidelity chart (M9): hold the
        // device next to a printed checker under the same lamp — the neutral
        // pipeline (docs/04 §5) must keep them matching.
        if (index == 2) return colorChecker(width, height)
        return recordPage(index, width, height)
    }

    private fun recordPage(index: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val random = Random(index.toLong() * 31 + 7)

        // Cream paper with faint age tinting toward the spine edge.
        canvas.drawColor(0xFFF4EEDE.toInt())
        paint.color = 0x0A806840
        repeat(5) {
            canvas.drawCircle(
                random.nextFloat() * width,
                random.nextFloat() * height,
                width * (0.1f + random.nextFloat() * 0.2f),
                paint,
            )
        }

        // Header.
        paint.color = 0xFF3A4A6B.toInt()
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = width * 0.055f
        canvas.drawText("THEO DÕI TIÊM CHỦNG", width / 2f, height * 0.09f, paint)

        // Printed table.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0xFF9AA4B8.toInt()
        val top = height * 0.14f
        val bottom = height * 0.90f
        val left = width * 0.08f
        val right = width * 0.92f
        val rowCount = 8
        for (r in 0..rowCount) {
            val y = top + (bottom - top) * r / rowCount
            canvas.drawLine(left, y, right, y, paint)
        }
        for (fx in floatArrayOf(left, left + (right - left) * 0.3f, left + (right - left) * 0.62f, right)) {
            canvas.drawLine(fx, top, fx, bottom, paint)
        }

        // Handwriting-ish entries in blue ink.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width * 0.004f
        paint.color = 0xFF2B4A9E.toInt()
        val entries = 2 + (index % 5)
        for (r in 0 until entries) {
            val y0 = top + (bottom - top) * (r + 0.55f) / rowCount
            var x = left + width * 0.02f
            val path = Path()
            path.moveTo(x, y0)
            while (x < right - width * 0.06f) {
                val nx = x + width * (0.02f + random.nextFloat() * 0.03f)
                path.quadTo(
                    (x + nx) / 2f,
                    y0 + (random.nextFloat() - 0.5f) * height * 0.012f,
                    nx,
                    y0,
                )
                x = nx
            }
            canvas.drawPath(path, paint)
        }

        // Page number: verification anchor for spread jumps.
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.SERIF
        paint.color = 0xFF6B675C.toInt()
        paint.textSize = width * 0.04f
        canvas.drawText("${index + 1}", width / 2f, height * 0.965f, paint)

        return bitmap
    }

    /**
     * ColorChecker-style chart + gray ramp for the M9 side-by-side color
     * validation (docs/04 §5 exit: scanned page on screen matches paper
     * under a lamp). sRGB patch values are the classic 24-target set.
     */
    private fun colorChecker(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.drawColor(0xFFF4EEDE.toInt())
        paint.color = 0xFF3A4A6B.toInt()
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = width * 0.045f
        canvas.drawText("COLOR FIDELITY", width / 2f, height * 0.08f, paint)

        val patches = intArrayOf(
            0xFF735244.toInt(), 0xFFC29682.toInt(), 0xFF627A9D.toInt(),
            0xFF576C43.toInt(), 0xFF8580B1.toInt(), 0xFF67BDAA.toInt(),
            0xFFD67E2C.toInt(), 0xFF505BA6.toInt(), 0xFFC15A63.toInt(),
            0xFF5E3C6C.toInt(), 0xFF9DBC40.toInt(), 0xFFE0A32E.toInt(),
            0xFF383D96.toInt(), 0xFF469449.toInt(), 0xFFAF363C.toInt(),
            0xFFE7C71F.toInt(), 0xFFBB5695.toInt(), 0xFF0885A1.toInt(),
            0xFFF3F3F2.toInt(), 0xFFC8C8C8.toInt(), 0xFFA0A0A0.toInt(),
            0xFF7A7A79.toInt(), 0xFF555555.toInt(), 0xFF343434.toInt(),
        )
        val cols = 4
        val rows = 6
        val gridLeft = width * 0.10f
        val gridTop = height * 0.13f
        val gridRight = width * 0.90f
        val gridBottom = height * 0.72f
        val cellW = (gridRight - gridLeft) / cols
        val cellH = (gridBottom - gridTop) / rows
        paint.style = Paint.Style.FILL
        for (i in patches.indices) {
            val c = i % cols
            val r = i / cols
            paint.color = patches[i]
            canvas.drawRect(
                gridLeft + c * cellW + 2f,
                gridTop + r * cellH + 2f,
                gridLeft + (c + 1) * cellW - 2f,
                gridTop + (r + 1) * cellH - 2f,
                paint,
            )
        }

        // 11-step gray ramp: tone-mapping curvature shows up here first.
        val rampTop = height * 0.76f
        val rampBottom = height * 0.86f
        for (i in 0..10) {
            val v = (255 * i / 10)
            paint.color = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            canvas.drawRect(
                gridLeft + (gridRight - gridLeft) * i / 11f,
                rampTop,
                gridLeft + (gridRight - gridLeft) * (i + 1) / 11f,
                rampBottom,
                paint,
            )
        }

        paint.color = 0xFF6B675C.toInt()
        paint.typeface = Typeface.SERIF
        paint.textSize = width * 0.035f
        canvas.drawText("3", width / 2f, height * 0.965f, paint)
        return bitmap
    }
}
