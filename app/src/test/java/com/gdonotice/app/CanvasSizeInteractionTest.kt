package com.gdonotice.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasSizeInteractionTest {
    @Test fun createAreaStartsAboveVisualButtonForSafeTap() {
        assertTrue(CanvasSizeInteraction.shouldCreate(560f))
        assertTrue(CanvasSizeInteraction.shouldCreate(680f))
        assertFalse(CanvasSizeInteraction.shouldCreate(559f))
    }

    @Test fun presetGridMapsBothRows() {
        assertEquals(0, CanvasSizeInteraction.presetIndex(60f, 160f))
        assertEquals(4, CanvasSizeInteraction.presetIndex(180f, 330f))
    }
}
