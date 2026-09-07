package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabStackFolderLayoutRulesTest {
    @Test
    fun `compact phone keeps two columns and bounded coverflow cards`() {
        val layout = TabStackFolderLayoutRules.layout(360f)

        assertEquals(360f, layout.dialogMaxWidth, 0f)
        assertEquals(2, layout.gridColumnCount)
        assertEquals(232f, layout.coverflowPageWidth, 0f)
        assertEquals(44f, layout.coverflowContentPadding, 0f)
    }

    @Test
    fun `tablet caps dialog width and shows three grid columns`() {
        val layout = TabStackFolderLayoutRules.layout(1_024f)

        assertEquals(720f, layout.dialogMaxWidth, 0f)
        assertEquals(3, layout.gridColumnCount)
        assertEquals(232f, layout.coverflowPageWidth, 0f)
        assertEquals(44f, layout.coverflowContentPadding, 0f)
    }

    @Test
    fun `narrow phone reduces padding before shrinking coverflow card`() {
        val layout = TabStackFolderLayoutRules.layout(240f)

        assertEquals(2, layout.gridColumnCount)
        assertEquals(172f, layout.coverflowPageWidth, 0f)
        assertEquals(16f, layout.coverflowContentPadding, 0f)
    }

    @Test
    fun `invalid width produces safe empty layout`() {
        val layout = TabStackFolderLayoutRules.layout(Float.NaN)

        assertEquals(0f, layout.dialogMaxWidth, 0f)
        assertEquals(2, layout.gridColumnCount)
        assertEquals(0f, layout.coverflowPageWidth, 0f)
        assertEquals(0f, layout.coverflowContentPadding, 0f)
    }

    @Test
    fun `compact outer width accounts for dialog content padding`() {
        val layout = TabStackFolderLayoutRules.layout(280f)

        assertEquals(212f, layout.coverflowPageWidth, 0f)
        assertEquals(16f, layout.coverflowContentPadding, 0f)
    }
}
