package com.leaf.camera.detect

import android.graphics.Bitmap
import com.leaf.camera.scan.Quad
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Document quad detection (docs/01-PRD.md §5.4): downscale → grayscale →
 * blur → Canny → contours → 4-corner convex polygons, largest plausible one
 * wins. Runs on the analysis stream (small frames), so plain OpenCV calls
 * are comfortably within the frame budget.
 */
class OpenCvQuadDetector {

    private val gray = Mat()
    private val blurred = Mat()
    private val edges = Mat()

    /**
     * Returns the best document quad in normalized coordinates, or null.
     * [bitmap] is the analysis frame, already rotated upright.
     */
    fun detect(bitmap: Bitmap): Quad? {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        try {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, CANNY_LOW, CANNY_HIGH)
            Imgproc.dilate(edges, edges, Mat())

            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(
                edges,
                contours,
                Mat(),
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE,
            )

            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            var best: Quad? = null
            var bestArea = MIN_AREA_FRACTION
            for (contour in contours) {
                val quad = approxQuad(contour, w, h) ?: continue
                val area = quad.area()
                if (area > bestArea && quad.isConvex()) {
                    best = quad
                    bestArea = area
                }
                contour.release()
            }
            return best
        } finally {
            src.release()
        }
    }

    private fun approxQuad(contour: MatOfPoint, w: Float, h: Float): Quad? {
        val curve = MatOfPoint2f(*contour.toArray())
        val approx = MatOfPoint2f()
        val perimeter = Imgproc.arcLength(curve, true)
        Imgproc.approxPolyDP(curve, approx, APPROX_EPSILON * perimeter, true)
        val points = approx.toArray()
        curve.release()
        approx.release()
        if (points.size != 4) return null
        return Quad.ordered(
            points.map { (it.x / w).toFloat() to (it.y / h).toFloat() },
        )
    }

    private companion object {
        const val CANNY_LOW = 40.0
        const val CANNY_HIGH = 120.0
        const val APPROX_EPSILON = 0.03
        /** Ignore quads smaller than this fraction of the frame. */
        const val MIN_AREA_FRACTION = 0.12f
    }
}
