package com.gdonotice.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SketchbookDesignTest {
    @Test fun windowModesFollowAvailableWidth() {
        assertEquals(WindowMode.COMPACT, SketchbookDesign.windowMode(599))
        assertEquals(WindowMode.MEDIUM, SketchbookDesign.windowMode(600))
        assertEquals(WindowMode.MEDIUM, SketchbookDesign.windowMode(839))
        assertEquals(WindowMode.EXPANDED, SketchbookDesign.windowMode(840))
    }

    @Test fun documentAndWebPresetsUseDifferentIconKinds() {
        assertEquals(CanvasKind.DOCUMENT, CanvasPreset.A4.kind)
        assertEquals(CanvasKind.DOCUMENT, CanvasPreset.B5.kind)
        assertEquals(CanvasKind.WEB, CanvasPreset.SQUARE.kind)
        assertEquals(CanvasKind.WEB, CanvasPreset.WIDE.kind)
    }

    @Test fun customCanvasValidationRejectsUnsafeSizes() {
        assertFalse(SketchbookDesign.validCustomSize(0, 1000))
        assertFalse(SketchbookDesign.validCustomSize(200, 200))
        assertTrue(SketchbookDesign.validCustomSize(1080, 1920))
        assertFalse(SketchbookDesign.validCustomSize(9000, 9000))
    }

    @Test fun signedInUserStillSeesCoverUntilEnter() {
        assertEquals(EntryRoute.COVER, SketchbookDesign.entryRoute(true, false))
        assertEquals(EntryRoute.HOME, SketchbookDesign.entryRoute(true, true))
        assertEquals(EntryRoute.COVER, SketchbookDesign.entryRoute(false, true))
    }

    @Test fun canvasPixelsPreservePresetRatio() {
        val a5 = SketchbookDesign.pixelSize(CanvasPreset.A5)
        assertEquals(1000, a5.first)
        assertEquals(CanvasPreset.A5.ratio, a5.first.toFloat() / a5.second, 0.002f)
        assertEquals(1600 to 900, SketchbookDesign.pixelSize(CanvasPreset.WIDE))
    }

    @Test fun compactAndExpandedScreensUsePurposeBuiltLayouts() {
        val compact = SketchbookDesign.layoutFor(412)
        assertEquals(1, compact.homeColumns)
        assertEquals(2, compact.libraryColumns)
        assertFalse(compact.usesTwoPaneHome)

        val expanded = SketchbookDesign.layoutFor(1100)
        assertEquals(2, expanded.homeColumns)
        assertEquals(4, expanded.libraryColumns)
        assertTrue(expanded.usesTwoPaneHome)
        assertEquals(2f / 5f, expanded.leadingPaneWeight, 0.001f)
        assertEquals(3f / 5f, expanded.trailingPaneWeight, 0.001f)
    }

    @Test fun postEntryNavigationHasDedicatedSketchbookRoutes() {
        assertEquals(AppRoute.HOME, AppRoute.fromKey("home"))
        assertEquals(AppRoute.LIBRARY, AppRoute.fromKey("library"))
        assertEquals(AppRoute.CREATE, AppRoute.fromKey("create"))
        assertEquals(AppRoute.ACCOUNT, AppRoute.fromKey("account"))
        assertEquals(AppRoute.HOME, AppRoute.fromKey("unknown"))
    }
}
