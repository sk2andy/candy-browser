package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserMainMenuPositionProviderTest {
    @Test
    fun `moves a bottom edge fallback down without changing right alignment`() {
        assertEquals(
            IntOffset(632, 176),
            provider(containerHeight = 800).calculatePosition(
                anchorBounds = IntRect(904, 680, 952, 728),
                windowSize = IntSize(1_000, 700),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = IntSize(320, 500),
            ),
        )
    }

    @Test
    fun `keeps RTL start alignment while moving the same fallback down`() {
        assertEquals(
            IntOffset(24, 176),
            provider(containerHeight = 800).calculatePosition(
                anchorBounds = IntRect(24, 680, 72, 728),
                windowSize = IntSize(1_000, 700),
                layoutDirection = LayoutDirection.Rtl,
                popupContentSize = IntSize(320, 500),
            ),
        )
    }

    @Test
    fun `clamps the downward shift above a large navigation inset`() {
        assertEquals(
            IntOffset(632, 152),
            provider(containerHeight = 800, bottomInset = 148).calculatePosition(
                anchorBounds = IntRect(904, 680, 952, 728),
                windowSize = IntSize(1_000, 700),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = IntSize(320, 500),
            ),
        )
    }

    @Test
    fun `clamps a top edge fallback below the display cutout`() {
        assertEquals(
            IntOffset(632, 90),
            provider(containerHeight = 800, topInset = 90).calculatePosition(
                anchorBounds = IntRect(904, 10, 952, 58),
                windowSize = IntSize(1_000, 700),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = IntSize(320, 600),
            ),
        )
    }

    @Test
    fun `short windows retain an in-bounds scrollable popup`() {
        assertEquals(
            IntOffset(24, 72),
            provider(containerHeight = 400).calculatePosition(
                anchorBounds = IntRect(296, 296, 344, 344),
                windowSize = IntSize(400, 300),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = IntSize(320, 204),
            ),
        )
    }

    private fun provider(
        containerHeight: Int,
        topInset: Int = 24,
        bottomInset: Int = 24,
    ) = BrowserMainMenuPositionProvider(
        containerHeight = containerHeight,
        topInset = topInset,
        bottomInset = bottomInset,
        verticalMargin = 48,
        downwardOffset = 24,
    )
}
