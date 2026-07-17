package com.leaf.editor

import com.leaf.domain.model.CropQuad
import com.leaf.domain.model.EditParams
import com.leaf.domain.model.NormPoint

/**
 * A page's pending edit session (docs/01-PRD.md §5.5): pure parameter
 * bookkeeping — clamping, rotation cycling, crop-corner dragging — with
 * nothing applied until the caller saves. Pixels are never touched here;
 * derivatives regenerate downstream from the untouched original.
 */
class EditDraft(private val initial: EditParams) {

    var params: EditParams = initial
        private set

    val isDirty: Boolean get() = params != initial

    fun rotateCw() {
        params = params.copy(rotationDeg = (params.rotationDeg + 90) % 360)
    }

    fun setBrightness(value: Float) {
        params = params.copy(brightness = value.coerceIn(-1f, 1f))
    }

    fun setContrast(value: Float) {
        params = params.copy(contrast = value.coerceIn(-1f, 1f))
    }

    /**
     * Drags crop corner [index] (0 TL, 1 TR, 2 BR, 3 BL) to normalized
     * ([x], [y]); a missing crop starts from the full page.
     */
    fun setCropCorner(index: Int, x: Float, y: Float) {
        val quad = params.cropQuad ?: FULL_PAGE
        val p = NormPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        params = params.copy(
            cropQuad = when (index) {
                0 -> quad.copy(topLeft = p)
                1 -> quad.copy(topRight = p)
                2 -> quad.copy(bottomRight = p)
                else -> quad.copy(bottomLeft = p)
            },
        )
    }

    fun clearCrop() {
        params = params.copy(cropQuad = null)
    }

    fun reset() {
        params = initial
    }

    companion object {
        val FULL_PAGE = CropQuad(
            topLeft = NormPoint(0f, 0f),
            topRight = NormPoint(1f, 0f),
            bottomRight = NormPoint(1f, 1f),
            bottomLeft = NormPoint(0f, 1f),
        )
    }
}
