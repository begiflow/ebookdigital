package com.leaf.editor

import com.leaf.domain.model.EditParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditDraftTest {

    @Test
    fun `rotation cycles through quarter turns`() {
        val draft = EditDraft(EditParams.NONE)
        repeat(3) { draft.rotateCw() }
        assertEquals(270, draft.params.rotationDeg)
        draft.rotateCw()
        assertEquals(0, draft.params.rotationDeg)
    }

    @Test
    fun `brightness and contrast clamp to the valid range`() {
        val draft = EditDraft(EditParams.NONE)
        draft.setBrightness(5f)
        draft.setContrast(-3f)
        assertEquals(1f, draft.params.brightness)
        assertEquals(-1f, draft.params.contrast)
    }

    @Test
    fun `first corner drag starts from the full page`() {
        val draft = EditDraft(EditParams.NONE)
        draft.setCropCorner(0, 0.1f, 0.15f)
        val quad = draft.params.cropQuad!!
        assertEquals(0.1f, quad.topLeft.x)
        assertEquals(1f, quad.bottomRight.x) // untouched corners = full page
        draft.setCropCorner(2, 2f, -1f) // clamps
        assertEquals(1f, draft.params.cropQuad!!.bottomRight.x)
        assertEquals(0f, draft.params.cropQuad!!.bottomRight.y)
    }

    @Test
    fun `dirty tracking and reset`() {
        val draft = EditDraft(EditParams.NONE)
        assertFalse(draft.isDirty)
        draft.setBrightness(0.3f)
        assertTrue(draft.isDirty)
        draft.reset()
        assertFalse(draft.isDirty)
        draft.setCropCorner(1, 0.9f, 0.1f)
        draft.clearCrop()
        assertNull(draft.params.cropQuad)
        assertFalse(draft.isDirty)
    }
}
