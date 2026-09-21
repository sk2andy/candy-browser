package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FailedPageRetryRulesTest {
    @Test
    fun `transport failure retries exact address`() {
        val tab = tab(
            error = "Network unavailable",
        )

        assertEquals(
            BrowserEngineCommands.retryFailedPage(PAGE_URL),
            FailedPageRetryRules.commandFor(tab),
        )
    }

    @Test
    fun `committed HTTP failure retries exact address`() {
        val tab = tab(httpStatusCode = 404)

        assertEquals(
            BrowserEngineCommands.retryFailedPage(PAGE_URL),
            FailedPageRetryRules.commandFor(tab),
        )
    }

    @Test
    fun `loading and successful pages reject failed page retry`() {
        assertNull(FailedPageRetryRules.commandFor(tab(isLoading = true, error = "Offline")))
        assertNull(FailedPageRetryRules.commandFor(tab()))
    }

    @Test
    fun `transport failure with non-web address is rejected`() {
        assertNull(
            FailedPageRetryRules.commandFor(
                tab(error = "Failed").copy(url = BLANK_URL),
            ),
        )
    }

    private fun tab(
        isLoading: Boolean = false,
        error: String? = null,
        httpStatusCode: Int? = null,
    ) = BrowserTab(
        id = "tab",
        lastAccessedAt = 1L,
        url = PAGE_URL,
        isLoading = isLoading,
        error = error,
        httpStatusCode = httpStatusCode,
    )

    private companion object {
        const val PAGE_URL = "https://example.com/offline"
    }
}
