package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageErrorFeedbackRulesTest {
    @Test
    fun `passive error becomes visible without creating retry effects`() {
        val state = PageErrorFeedbackRules.observe(
            current = PageErrorFeedbackState.Hidden(),
            error = "Network unavailable",
            isLoading = false,
        )

        assertEquals(PageErrorFeedbackState.Error("Network unavailable"), state)
        val passiveTransition = PageErrorFeedbackRules.requestRetry(
            PageErrorFeedbackState.Hidden(),
        )
        assertFalse(passiveTransition.shouldReload)
        assertFalse(passiveTransition.emitConfirmHaptic)
    }

    @Test
    fun `user retry enters progress and requests one reload and haptic`() {
        val transition = PageErrorFeedbackRules.requestRetry(
            PageErrorFeedbackState.Error("Network unavailable"),
        )

        assertEquals(PageErrorFeedbackState.Retrying("Network unavailable"), transition.state)
        assertTrue(transition.shouldReload)
        assertTrue(transition.emitConfirmHaptic)
    }

    @Test
    fun `duplicate retry is ignored`() {
        val transition = PageErrorFeedbackRules.requestRetry(
            PageErrorFeedbackState.Retrying("Network unavailable"),
        )

        assertEquals(PageErrorFeedbackState.Retrying("Network unavailable"), transition.state)
        assertFalse(transition.shouldReload)
        assertFalse(transition.emitConfirmHaptic)
    }

    @Test
    fun `retry remains visible until navigation starts`() {
        val retrying = PageErrorFeedbackState.Retrying("Network unavailable")

        assertEquals(
            retrying,
            PageErrorFeedbackRules.observe(
                current = retrying,
                error = null,
                isLoading = false,
            ),
        )
        assertEquals(
            PageErrorFeedbackState.Hidden("Network unavailable"),
            PageErrorFeedbackRules.observe(
                current = retrying,
                error = null,
                isLoading = true,
            ),
        )
    }

    @Test
    fun `retry failure preserves new message and enables another attempt`() {
        val state = PageErrorFeedbackRules.observe(
            current = PageErrorFeedbackState.Retrying("Old error"),
            error = "Still offline",
            isLoading = false,
        )

        assertEquals(PageErrorFeedbackState.Error("Still offline"), state)
        assertTrue(PageErrorFeedbackRules.requestRetry(state).shouldReload)
    }
}
