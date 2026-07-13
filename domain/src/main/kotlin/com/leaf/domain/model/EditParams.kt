package com.leaf.domain.model

/**
 * Non-destructive edits. Applied to derivatives (texture, thumbnail) by the
 * texture pipeline; the original image is never modified.
 */
data class EditParams(
    val cropQuad: CropQuad? = null,
    val rotationDeg: Int = 0,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
) {
    companion object {
        val NONE = EditParams()
    }
}

/** Perspective-correction quad in normalized image coordinates (0..1). */
data class CropQuad(
    val topLeft: NormPoint,
    val topRight: NormPoint,
    val bottomRight: NormPoint,
    val bottomLeft: NormPoint,
)

data class NormPoint(val x: Float, val y: Float)
