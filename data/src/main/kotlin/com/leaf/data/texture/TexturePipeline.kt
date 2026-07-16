package com.leaf.data.texture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.leaf.data.files.FileStore
import com.leaf.domain.model.EditParams
import java.io.ByteArrayOutputStream
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * original + EditParams → GPU derivative + thumbnail (docs/02 §3).
 * Crop-quad dewarp and brightness/contrast run in OpenCV, the mip chain and
 * container are the pure [MipChain]/[Ktx1] stages. The original file is
 * only ever *read* — write-once integrity belongs to [FileStore].
 * Runs on Dispatchers.Default at import and on edit; never on the render
 * thread.
 */
class TexturePipeline(private val store: FileStore) {

    /** Regenerates texture + thumb for [pageId]. False if no original. */
    fun regenerate(pageId: String, edits: EditParams): Boolean {
        val original = store.originalFile(pageId)
        if (!original.exists()) return false
        var bitmap = BitmapFactory.decodeFile(original.absolutePath) ?: return false

        edits.cropQuad?.let { bitmap = dewarp(bitmap, it) }
        if (edits.rotationDeg % 360 != 0) {
            val m = Matrix().apply { postRotate(edits.rotationDeg.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }
        if (edits.brightness != 0f || edits.contrast != 0f) {
            bitmap = colorAdjust(bitmap, edits.brightness, edits.contrast)
        }
        bitmap = downscale(bitmap, MAX_TEXTURE_SIZE)

        // Texture: RGBA mip chain in a KTX container.
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val levels = MipChain.build(pixels, bitmap.width, bitmap.height)
        store.writeTexture(pageId, Ktx1.write(levels))

        // Thumbnail.
        val thumb = downscale(bitmap, THUMB_SIZE)
        val out = ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.JPEG, 85, out)
        store.writeThumb(pageId, out.toByteArray())
        return true
    }

    private fun dewarp(source: Bitmap, quad: com.leaf.domain.model.CropQuad): Bitmap {
        val w = source.width.toDouble()
        val h = source.height.toDouble()
        val srcQuad = MatOfPoint2f(
            Point(quad.topLeft.x * w, quad.topLeft.y * h),
            Point(quad.topRight.x * w, quad.topRight.y * h),
            Point(quad.bottomRight.x * w, quad.bottomRight.y * h),
            Point(quad.bottomLeft.x * w, quad.bottomLeft.y * h),
        )
        val outW = source.width
        val outH = source.height
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
        src.release(); dst.release(); transform.release(); srcQuad.release(); dstQuad.release()
        return out
    }

    /** dst = src * (1 + contrast) + brightness*128, per channel. */
    private fun colorAdjust(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(source, src)
        src.convertTo(src, -1, (1f + contrast).toDouble(), (brightness * 128f).toDouble())
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(src, out)
        src.release()
        return out
    }

    private fun downscale(source: Bitmap, maxSide: Int): Bitmap {
        val side = maxOf(source.width, source.height)
        if (side <= maxSide) return source
        val scale = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(
            source,
            maxOf(1, (source.width * scale).toInt()),
            maxOf(1, (source.height * scale).toInt()),
            true,
        )
    }

    private companion object {
        const val MAX_TEXTURE_SIZE = 2048
        const val THUMB_SIZE = 256
    }
}
