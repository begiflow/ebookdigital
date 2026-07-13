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
}
