package com.leaf.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.random.Random

/**
 * Generates a test vaccination-booklet cover so M3 can be judged against the
 * success metric without the camera pipeline (arrives M12). Exercises the
 * same Bitmap -> GPU texture path real captured covers will use.
 */
object CoverArt {

    fun vaccinationBooklet(width: Int = 512, height: Int = 726): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Leatherette base.
        canvas.drawColor(0xFF6D4A2F.toInt())

        // Grain speckle.
        val random = Random(42)
        paint.strokeWidth = 1f
        repeat(9_000) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            val lighten = random.nextFloat() < 0.5f
            paint.color = if (lighten) 0x14FFFFFF else 0x16000000
            canvas.drawPoint(x, y, paint)
        }

        // Debossed border.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width * 0.008f
        paint.color = 0x66402A18
        val inset = width * 0.06f
        canvas.drawRect(inset, inset, width - inset, height - inset, paint)

        // Gold-foil title block.
        paint.style = Paint.Style.FILL
        paint.color = 0xFFD9B36A.toInt()
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = width * 0.085f
        canvas.drawText("SỔ TIÊM CHỦNG", width / 2f, height * 0.32f, paint)
        paint.textSize = width * 0.05f
        canvas.drawText("VACCINATION RECORD", width / 2f, height * 0.39f, paint)

        // Foil rule under the title.
        paint.strokeWidth = height * 0.004f
        canvas.drawLine(width * 0.22f, height * 0.425f, width * 0.78f, height * 0.425f, paint)

        paint.color = Color.argb(200, 217, 179, 106)
        paint.textSize = width * 0.042f
        canvas.drawText("LEAF", width / 2f, height * 0.88f, paint)

        return bitmap
    }
}
