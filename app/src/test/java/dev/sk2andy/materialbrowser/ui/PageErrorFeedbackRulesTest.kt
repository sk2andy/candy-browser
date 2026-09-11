package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageErrorFeedbackRulesTest {
    @Test
    fun `HTTP 404 becomes not found after navigation finishes`() {
        val observation = PageErrorFeedbackRules.observe(
            current = PageErrorFeedbackState.Hidden,
            error = null,
            httpStatusCode = 404,
            isLoading = false,
            isOnline = true,
        )

        assertEquals(PageErrorFeedbackState.NotFound, observation.state)
        assertFalse(observation.shouldReload)
    }

    @Test
    fun `offline state replaces transport error`() {
        val observation = PageErrorFeedbackRules.observe(
            current = PageErrorFeedbackState.Hidden,
            error = "Network unavailable",
            httpStatusCode = null,
            isLoading = false,
            isOnline = false,
        )

        assertEquals(PageErrorFeedbackState.Offline(), observation.state)
    }

    @Test
    fun `engine offline error never becomes unknown host while connectivity catches up`() {
        val observation = PageErrorFeedbackRules.observe(
            current = PageErrorFeedbackState.Hidden,
            error = "Gecko navigation failed",
            httpStatusCode = null,
            isLoading = false,
            isOnline = true,
            failureKind = BrowserEngineFailureKind.Offline,
        )

        assertEquals(PageErrorFeedbackState.Offline(isOnlineReady = true), observation.state)
    }

    @Test
    fun `connection loss does not cover an already loaded page`() {
        val observation = PageErrorFeedbackRules.observe(
            current = PageErrorFeedbackState.Hidden,
            error = null,
            httpStatusCode = null,
            isLoading = false,
            isOnline = false,
        )

        assertEquals(PageErrorFeedbackState.Hidden, observation.state)
        assertFalse(observation.shouldReload)
    }

    @Test
    fun `connection return preserves automatic game and exposes ready state`() {
        val observation = PageErrorFeedbackRules.observe(
            current = PageErrorFeedbackState.Offline(),
            error = "Network unavailable",
            httpStatusCode = null,
            isLoading = false,
            isOnline = true,
        )

        assertEquals(PageErrorFeedbackState.Offline(isOnlineReady = true), observation.state)
        assertFalse(observation.shouldReload)
    }

    @Test
    fun `cleared transport error does not imply reconnect while network remains offline`() {
        val current = PageErrorFeedbackState.Offline(
            isOnlineReady = true,
        )

        val observation = PageErrorFeedbackRules.observe(
            current = current,
            error = null,
            httpStatusCode = null,
            isLoading = true,
            isOnline = false,
        )

        assertEquals(current.copy(isOnlineReady = false), observation.state)
        assertFalse(observation.shouldReload)
    }

    @Test
    fun `connection return preserves running game and exposes ready state`() {
        val observation = PageErrorFeedbackRules.observe(
            current = PageErrorFeedbackState.Offline(),
            error = "Network unavailable",
            httpStatusCode = null,
            isLoading = false,
            isOnline = true,
        )

        assertEquals(
            PageErrorFeedbackState.Offline(isOnlineReady = true),
            observation.state,
        )
        assertFalse(observation.shouldReload)
    }

    @Test
    fun `network loss removes ready banner without resetting game`() {
        val observation = PageErrorFeedbackRules.observe(
            current = PageErrorFeedbackState.Offline(
                isOnlineReady = true,
                game = CandyCircuitGameState(
                    movesRemaining = 10,
                    score = 400,
                    bestScore = 400,
                    combo = 1,
                ),
            ),
            error = "Network unavailable",
            httpStatusCode = null,
            isLoading = false,
            isOnline = false,
        )

        assertEquals(
            PageErrorFeedbackState.Offline(
                game = CandyCircuitGameState(
                    movesRemaining = 10,
                    score = 400,
                    bestScore = 400,
                    combo = 1,
                ),
            ),
            observation.state,
        )
    }

    @Test
    fun `online game retry requests one explicit reload`() {
        val first = PageErrorFeedbackRules.requestRetry(
            PageErrorFeedbackState.Offline(isOnlineReady = true),
        )
        val duplicate = PageErrorFeedbackRules.requestRetry(first.state)

        assertEquals(PageErrorFeedbackState.Retrying, first.state)
        assertTrue(first.shouldReload)
        assertTrue(first.emitConfirmHaptic)
        assertFalse(duplicate.shouldReload)
    }

    @Test
    fun `online then offline keeps complete Candy Circuit state`() {
        val game = CandyCircuitRules.rotate(CandyCircuitGameState(), tileIndex = 5)
        val online = PageErrorFeedbackRules.observe(
            current = PageErrorFeedbackState.Offline(game = game),
            error = "Network unavailable",
            httpStatusCode = null,
            isLoading = false,
            isOnline = true,
        ).state
        val offlineAgain = PageErrorFeedbackRules.observe(
            current = online,
            error = "Network unavailable",
            httpStatusCode = null,
            isLoading = false,
            isOnline = false,
        ).state

        assertEquals(
            PageErrorFeedbackState.Offline(game = game),
            offlineAgain,
        )
    }
}
