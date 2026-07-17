package com.leaf.camera.detect

import android.graphics.Bitmap
import com.leaf.camera.scan.Quad
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Perspective correction (docs/01-PRD.md §5.4): warps the detected quad to a
 * flat page image. Output size follows the quad's own average edge lengths,
 * so a page fills its pixels regardless of shot angle; the original frame is
 * preserved by the caller (originals are write-once, docs/02 §3).
 */
object Dewarper {

    fun dewarp(source: Bitmap, quad: Quad, maxOutputWidth: Int = 2048): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val px = FloatArray(8)
        for (i in 0 until 4) {
            val (x, y) = quad.corner(i)
            px[2 * i] = x * w
            px[2 * i + 1] = y * h
        }

        val topW = dist(px, 0, 1)
        val bottomW = dist(px, 3, 2)
        val leftH = dist(px, 0, 3)
        val rightH = dist(px, 1, 2)
        var outW = ((topW + bottomW) / 2f).toInt().coerceAtLeast(8)
        var outH = ((leftH + rightH) / 2f).toInt().coerceAtLeast(8)
        if (outW > maxOutputWidth) {
            outH = outH * maxOutputWidth / outW
            outW = maxOutputWidth
        }

        val srcQuad = MatOfPoint2f(
            Point(px[0].toDouble(), px[1].toDouble()),
            Point(px[2].toDouble(), px[3].toDouble()),
            Point(px[4].toDouble(), px[5].toDouble()),
            Point(px[6].toDouble(), px[7].toDouble()),
        )
        val dstQuad = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outW - 1.0, 0.0),
            Point(outW - 1.0, outH - 1.0),
            Point(0.0, outH - 1.0),
        )

        val src = Mat()
        Utils.bitmapToMat(source, src)
        val transform = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)
        val dst = Mat()
        Imgproc.warpPerspective(src, dst, transform, Size(outW.toDouble(), outH.toDouble()))

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, out)

        src.release()
        dst.release()
        transform.release()
        srcQuad.release()
        dstQuad.release()
        return out
    }

    private fun dist(px: FloatArray, a: Int, b: Int): Float {
        val dx = px[2 * a] - px[2 * b]
        val dy = px[2 * a + 1] - px[2 * b + 1]
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
