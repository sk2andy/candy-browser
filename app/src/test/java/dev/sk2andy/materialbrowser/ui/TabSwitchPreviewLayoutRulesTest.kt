package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabSwitchPreviewLayoutRulesTest {
    @Test
    fun `preview starts below web view top inset and stops before address bar`() {
        assertEquals(
            TabSwitchPreviewLayout(
                topInsetPx = 72f,
                visibleHeightPx = 2_088f,
            ),
            TabSwitchPreviewLayoutRules.resolve(
                rootHeightPx = 2_400f,
                previewTopInsetPx = 72,
                bottomBarTopPx = 2_160f,
            ),
        )
    }

    @Test
    fun `edge to edge preview keeps zero top inset`() {
        assertEquals(
            TabSwitchPreviewLayout(
                topInsetPx = 0f,
                visibleHeightPx = 2_160f,
            ),
            TabSwitchPreviewLayoutRules.resolve(
                rootHeightPx = 2_400f,
                previewTopInsetPx = 0,
                bottomBarTopPx = 2_160f,
            ),
        )
    }

    @Test
    fun `missing address bar bounds keep full preview height`() {
        assertEquals(
            TabSwitchPreviewLayout(
                topInsetPx = 72f,
                visibleHeightPx = 2_328f,
            ),
            TabSwitchPreviewLayoutRules.resolve(
                rootHeightPx = 2_400f,
                previewTopInsetPx = 72,
                bottomBarTopPx = Float.NaN,
            ),
        )
    }

    @Test
    fun `captured bitmap height stays stable while address bar animates`() {
        assertEquals(
            TabSwitchPreviewLayout(
                topInsetPx = 72f,
                visibleHeightPx = 1_920f,
            ),
            TabSwitchPreviewLayoutRules.resolve(
                rootHeightPx = 2_400f,
                previewTopInsetPx = 72,
                bottomBarTopPx = 1_760f,
                capturedHeightPx = 1_920f,
            ),
        )
    }
}
