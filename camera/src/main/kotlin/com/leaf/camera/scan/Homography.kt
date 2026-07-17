package com.leaf.camera.scan

/**
 * Plane homography mapping the unit square to a [Quad] (docs/01 §5.4
 * dewarp). Pure Kotlin: the 8 unknowns come from the four corner
 * correspondences via Gaussian elimination — used for overlay math and for
 * feeding warp matrices without touching OpenCV from tests.
 */
class Homography private constructor(private val h: FloatArray) {

    /** Maps unit-square (u, v) into quad space. */
    fun map(u: Float, v: Float): Pair<Float, Float> {
        val w = h[6] * u + h[7] * v + 1f
        val x = (h[0] * u + h[1] * v + h[2]) / w
        val y = (h[3] * u + h[4] * v + h[5]) / w
        return x to y
    }

    /** Row-major 3x3 matrix (h33 = 1). */
    fun matrix(): FloatArray = floatArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1f)

    companion object {
        /** Homography taking (0,0),(1,0),(1,1),(0,1) to the quad's TL,TR,BR,BL. */
        fun unitSquareTo(quad: Quad): Homography {
            val src = arrayOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
            val dst = Array(4) { quad.corner(it) }
            return solve(src, dst)
        }

        /** General 4-point homography. */
        fun solve(src: Array<Pair<Float, Float>>, dst: Array<Pair<Float, Float>>): Homography {
            require(src.size == 4 && dst.size == 4)
            // Rows: for each pair, two equations in h11..h32.
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val (u, v) = src[i]
                val (x, y) = dst[i]
                a[2 * i][0] = u.toDouble(); a[2 * i][1] = v.toDouble(); a[2 * i][2] = 1.0
                a[2 * i][6] = -u.toDouble() * x; a[2 * i][7] = -v.toDouble() * x
                a[2 * i][8] = x.toDouble()
                a[2 * i + 1][3] = u.toDouble(); a[2 * i + 1][4] = v.toDouble(); a[2 * i + 1][5] = 1.0
                a[2 * i + 1][6] = -u.toDouble() * y; a[2 * i + 1][7] = -v.toDouble() * y
                a[2 * i + 1][8] = y.toDouble()
            }
            // Gaussian elimination with partial pivoting.
            for (col in 0 until 8) {
                var pivot = col
                for (r in col + 1 until 8) {
                    if (kotlin.math.abs(a[r][col]) > kotlin.math.abs(a[pivot][col])) pivot = r
                }
                require(kotlin.math.abs(a[pivot][col]) > 1e-12) { "degenerate quad" }
                val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
                for (r in 0 until 8) {
                    if (r == col) continue
                    val f = a[r][col] / a[col][col]
                    if (f == 0.0) continue
                    for (k in col until 9) a[r][k] -= f * a[col][k]
                }
            }
            val h = FloatArray(8)
            for (i in 0 until 8) h[i] = (a[i][8] / a[i][i]).toFloat()
            return Homography(h)
        }
    }
}
