package dev.sk2andy.materialbrowser.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TabSwitchPreviewLayoutRulesTest {
    @Test
    fun `safe-area inset and captured viewport reconstruct live page bounds`() {
        assertEquals(
            TabSwitchPreviewLayout(topInsetPx = 72f, visibleHeightPx = 2_088f),
            TabSwitchPreviewLayoutRules.resolve(
                rootHeightPx = 2_400f,
                previewTopInsetPx = 72,
                bottomBarTopPx = 1_760f,
                capturedHeightPx = 2_088f,
            ),
        )
    }

    @Test
    fun `captured viewport is clipped below oversized safe-area inset`() {
        assertEquals(
            TabSwitchPreviewLayout(topInsetPx = 100f, visibleHeightPx = 0f),
            TabSwitchPreviewLayoutRules.resolve(
                rootHeightPx = 100f,
                previewTopInsetPx = 140,
                bottomBarTopPx = 90f,
                capturedHeightPx = 80f,
            ),
        )
    }

    @Test
    fun `invalid root and transient bar geometry collapse deterministically`() {
        assertEquals(
            TabSwitchPreviewLayout(topInsetPx = 0f, visibleHeightPx = 0f),
            TabSwitchPreviewLayoutRules.resolve(
                rootHeightPx = Float.NaN,
                previewTopInsetPx = 72,
                bottomBarTopPx = Float.POSITIVE_INFINITY,
                capturedHeightPx = Float.NaN,
            ),
        )
    }

    @Test
    fun `missing capture uses current content bottom`() {
        assertEquals(
            TabSwitchPreviewLayout(topInsetPx = 72f, visibleHeightPx = 2_088f),
            TabSwitchPreviewLayoutRules.resolve(
                rootHeightPx = 2_400f,
                previewTopInsetPx = 72,
                bottomBarTopPx = 2_160f,
            ),
        )
    }
}
