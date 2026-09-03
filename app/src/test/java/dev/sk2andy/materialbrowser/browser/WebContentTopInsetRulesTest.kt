package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class WebContentTopInsetRulesTest {
    @Test
    fun `explicit cover uses edge to edge`() {
        assertEquals(
            WebContentTopInsetMode.EdgeToEdge,
            resolve(drawsEdgeToEdge = true),
        )
    }

    @Test
    fun `normal page uses scrollable document inset`() {
        assertEquals(
            WebContentTopInsetMode.ScrollableDocument,
            resolve(),
        )
    }

    @Test
    fun `forced safe area uses native margin`() {
        assertEquals(
            WebContentTopInsetMode.NativeSafeArea,
            resolve(forceSafeArea = true),
        )
    }

    @Test
    fun `missing document start support falls back to native margin`() {
        assertEquals(
            WebContentTopInsetMode.NativeSafeArea,
            resolve(documentStartAvailable = false),
        )
    }

    private fun resolve(
        drawsEdgeToEdge: Boolean = false,
        forceSafeArea: Boolean = false,
        scrollableDocumentEnabled: Boolean = true,
        documentStartAvailable: Boolean = true,
    ): WebContentTopInsetMode = WebContentTopInsetRules.resolve(
        drawsEdgeToEdge = drawsEdgeToEdge,
        forceSafeArea = forceSafeArea,
        scrollableDocumentEnabled = scrollableDocumentEnabled,
        documentStartAvailable = documentStartAvailable,
    )
}
