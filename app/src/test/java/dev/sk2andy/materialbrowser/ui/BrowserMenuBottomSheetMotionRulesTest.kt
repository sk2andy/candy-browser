package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserMenuBottomSheetMotionRulesTest {
    @Test
    fun `opening moves address from measured anchor to fixed sheet header`() {
        assertEquals(276f, translation(0f), 0f)
        assertEquals(138f, translation(0.5f), 0f)
        assertEquals(0f, translation(1f), 0f)
        assertEquals(276f, translation(-1f), 0f)
        assertEquals(0f, translation(2f), 0f)
    }

    @Test
    fun `unmeasured anchor and short window have bounded collapsed offsets`() {
        assertEquals(
            508f,
            BrowserMenuBottomSheetMotionRules.translationY(0f, 600f, null, 0f, 1f),
            0f,
        )
        assertEquals(
            0f,
            BrowserMenuBottomSheetMotionRules.translationY(0f, 60f, null, 0f, 1f),
            0f,
        )
    }

    @Test
    fun `short windows use full safe height instead of collapsing content`() {
        assertTrue(BrowserMenuBottomSheetMotionRules.isCompactHeight(320f))
        assertTrue(BrowserMenuBottomSheetMotionRules.isCompactHeight(480f))
        assertFalse(BrowserMenuBottomSheetMotionRules.isCompactHeight(481f))
        assertEquals(1f, BrowserMenuBottomSheetMotionRules.heightFraction(320f), 0f)
        assertEquals(0.85f, BrowserMenuBottomSheetMotionRules.heightFraction(800f), 0f)
    }

    @Test
    fun `handle tracks downward travel and cannot rise above expanded header`() {
        assertEquals(0.75f, BrowserMenuBottomSheetMotionRules.dragProgress(1f, 69f, 276f), 0f)
        assertEquals(1f, BrowserMenuBottomSheetMotionRules.dragProgress(1f, -100f, 276f), 0f)
        assertEquals(0f, BrowserMenuBottomSheetMotionRules.dragProgress(0.5f, 500f, 276f), 0f)
        assertEquals(0.75f, BrowserMenuBottomSheetMotionRules.dragProgress(0.5f, -69f, 276f), 0f)
        assertEquals(1f, BrowserMenuBottomSheetMotionRules.dragProgress(1f, 100f, 0f), 0f)
        assertEquals(1f, BrowserMenuBottomSheetMotionRules.dragProgress(1f, 100f, -100f), 0f)
        assertEquals(0f, BrowserMenuBottomSheetMotionRules.translationY(0f, 600f, 400f, 500f, 1f), 0f)
    }

    @Test
    fun `sheet fades only near collapsed address position`() {
        assertEquals(1f, BrowserMenuBottomSheetMotionRules.surfaceAlpha(1f), 0f)
        assertEquals(1f, BrowserMenuBottomSheetMotionRules.surfaceAlpha(0.35f), 0f)
        assertEquals(0.5f, BrowserMenuBottomSheetMotionRules.surfaceAlpha(0.175f), 0.0001f)
        assertEquals(0f, BrowserMenuBottomSheetMotionRules.surfaceAlpha(0f), 0f)
        assertEquals(0f, BrowserMenuBottomSheetMotionRules.surfaceAlpha(-1f), 0f)
        assertEquals(1f, BrowserMenuBottomSheetMotionRules.surfaceAlpha(2f), 0f)
    }

    @Test
    fun `short pulls return and reaching fade region or downward fling dismisses`() {
        assertFalse(BrowserMenuBottomSheetMotionRules.shouldDismiss(0.8f, 0f))
        assertFalse(BrowserMenuBottomSheetMotionRules.shouldDismiss(0.8f, 1_000f))
        assertFalse(BrowserMenuBottomSheetMotionRules.shouldDismiss(1f, 2_000f))
        assertFalse(BrowserMenuBottomSheetMotionRules.shouldDismiss(0.8f, -2_000f))
        assertTrue(BrowserMenuBottomSheetMotionRules.shouldDismiss(0.35f, 0f))
        assertTrue(BrowserMenuBottomSheetMotionRules.shouldDismiss(0f, 0f))
        assertTrue(BrowserMenuBottomSheetMotionRules.shouldDismiss(0.8f, 1_001f))
    }

    private fun translation(progress: Float): Float = BrowserMenuBottomSheetMotionRules.translationY(
        progress = progress,
        sheetHeightPx = 600f,
        anchorTopPx = 800f,
        sheetTopPx = 500f,
        density = 1f,
    )
}
