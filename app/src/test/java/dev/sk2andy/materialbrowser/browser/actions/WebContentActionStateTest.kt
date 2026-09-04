package dev.sk2andy.materialbrowser.browser.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        state.show(
            target = WebContentTarget(linkUrl = "https://example.com"),
            sourceTabId = "source-tab",
        )
        state.updateLinkPeek(progress = 1f, armed = true)

        state.dismiss()

        assertFalse(state.isVisible)
        assertFalse(state.isLinkPeekVisible)
        assertNull(state.sourceTabId)
        assertEquals(0f, state.linkPeekProgress, 0.001f)
        assertFalse(state.isLinkPeekArmed)
        assertFalse(state.isLinkPeekCommitting)
    }

    @Test
    fun `source tab change invalidates link peek progress`() {
        val state = WebContentActionState()
        val target = WebContentTarget(linkUrl = "https://example.com/file.json")
        state.show(target = target, sourceTabId = "regular-tab")
        state.updateLinkPeek(progress = 0.75f, armed = true)

        state.show(target = target, sourceTabId = "private-tab")

        assertEquals("private-tab", state.sourceTabId)
        assertEquals(0f, state.linkPeekProgress, 0.001f)
        assertFalse(state.isLinkPeekArmed)
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
