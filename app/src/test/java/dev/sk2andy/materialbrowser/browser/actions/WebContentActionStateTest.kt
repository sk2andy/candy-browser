package dev.sk2andy.materialbrowser.browser.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebContentActionStateTest {
    @Test
    fun `new link resets peek while focused-node enrichment preserves progress`() {
        val state = WebContentActionState()
        state.show(WebContentTarget(linkUrl = "https://example.com/article"))
        state.updateLinkPeek(progress = 0.75f, armed = false)

        state.show(
            WebContentTarget(
                linkUrl = "https://example.com/article",
                imageUrl = "https://example.com/preview.png",
            ),
        )

        assertEquals(0.75f, state.linkPeekProgress, 0.001f)
        assertTrue(state.target?.canDownloadImage == true)

        state.show(WebContentTarget(linkUrl = "https://example.com/other"))
        assertEquals(0f, state.linkPeekProgress, 0.001f)
        assertFalse(state.isLinkPeekArmed)
        assertFalse(state.isLinkPeekCommitting)
    }

    @Test
    fun `dismiss atomically clears target and gesture state`() {
        val state = WebContentActionState()
        state.show(WebContentTarget(linkUrl = "https://example.com"))
        state.updateLinkPeek(progress = 1f, armed = true)

        state.dismiss()

        assertFalse(state.isVisible)
        assertFalse(state.isLinkPeekVisible)
        assertEquals(0f, state.linkPeekProgress, 0.001f)
        assertFalse(state.isLinkPeekArmed)
        assertFalse(state.isLinkPeekCommitting)
    }

    @Test
    fun `commit is single use and freezes armed progress until dismissal`() {
        val state = WebContentActionState()
        state.show(WebContentTarget(linkUrl = "https://example.com"))

        state.startLinkPeekCommit()
        state.startLinkPeekCommit()
        state.updateLinkPeek(progress = 0f, armed = false)

        assertTrue(state.isLinkPeekCommitting)
        assertTrue(state.isLinkPeekArmed)
        assertEquals(1f, state.linkPeekProgress, 0.001f)

        state.dismiss()
        assertFalse(state.isLinkPeekCommitting)
    }

    @Test
    fun `new tab pulse nonce increments only when explicitly requested`() {
        val state = WebContentActionState()

        state.requestLinkPeekNewTabPulse()
        state.requestLinkPeekNewTabPulse()

        assertEquals(2, state.linkPeekNewTabPulseNonce)
    }

    @Test
    fun `show and dismiss invalidate pending content replies`() {
        val state = WebContentActionState()
        val initialRevision = state.revision

        state.show(WebContentTarget(linkUrl = "https://example.com"))
        val shownRevision = state.revision
        state.dismiss()

        assertTrue(shownRevision > initialRevision)
        assertTrue(state.revision > shownRevision)
    }

}
