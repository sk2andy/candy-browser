package dev.sk2andy.materialbrowser.browser.actions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPeekTargetSessionRulesTest {
    @Test
    fun `active source session accepts focused-node result`() {
        assertTrue(
            canApply(
                capturedPointerSessionId = 7L,
                activePointerSessionId = 7L,
            ),
        )
    }

    @Test
    fun `release before async result rejects stale target`() {
        assertFalse(
            canApply(
                capturedPointerSessionId = 7L,
                activePointerSessionId = null,
            ),
        )
    }

    @Test
    fun `new gesture and tab switch reject old result`() {
        assertFalse(
            canApply(
                capturedPointerSessionId = 7L,
                activePointerSessionId = 8L,
            ),
        )
        assertFalse(
            canApply(
                capturedPointerSessionId = 7L,
                activePointerSessionId = 7L,
                selectedTabId = "other-tab",
            ),
        )
        assertFalse(
            canApply(
                capturedPointerSessionId = 7L,
                activePointerSessionId = 7L,
                sourceWebViewAttached = false,
            ),
        )
    }

    @Test
    fun `accessibility long click without pointer stays supported`() {
        assertTrue(
            canApply(
                capturedPointerSessionId = null,
                activePointerSessionId = null,
            ),
        )
    }

    private fun canApply(
        capturedPointerSessionId: Long?,
        activePointerSessionId: Long?,
        selectedTabId: String = "source-tab",
        sourceWebViewAttached: Boolean = true,
    ) = LinkPeekTargetSessionRules.canApply(
        capturedPointerSessionId = capturedPointerSessionId,
        activePointerSessionId = activePointerSessionId,
        sourceTabId = "source-tab",
        selectedTabId = selectedTabId,
        sourceWebViewAttached = sourceWebViewAttached,
    )
}

