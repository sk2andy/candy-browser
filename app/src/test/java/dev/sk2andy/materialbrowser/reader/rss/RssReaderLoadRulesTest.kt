package dev.sk2andy.materialbrowser.reader.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class RssReaderLoadRulesTest {
    @Test
    fun waitsForRequestedTabAndExtractsItsFinalRedirectUrl() {
        assertEquals(
            RssReaderLoadAction.Wait,
            RssReaderLoadRules.action(
                requestedTabId = "tab-1",
                selectedTabId = "tab-1",
                url = "https://example.com/redirecting",
                isLoading = true,
                isPrivate = false,
                hasError = false,
            ),
        )
        assertEquals(
            RssReaderLoadAction.Extract,
            RssReaderLoadRules.action(
                requestedTabId = "tab-1",
                selectedTabId = "tab-1",
                url = "https://example.com/final-article",
                isLoading = false,
                isPrivate = false,
                hasError = false,
            ),
        )
    }

    @Test
    fun cancelsWhenNavigationMovesToAnotherTabOrPrivateTab() {
        assertEquals(
            RssReaderLoadAction.Cancel,
            RssReaderLoadRules.action(
                requestedTabId = "tab-1",
                selectedTabId = "tab-2",
                url = "https://example.com/article",
                isLoading = false,
                isPrivate = false,
                hasError = false,
            ),
        )
        assertEquals(
            RssReaderLoadAction.Cancel,
            RssReaderLoadRules.action(
                requestedTabId = "tab-1",
                selectedTabId = "tab-1",
                url = "https://example.com/article",
                isLoading = false,
                isPrivate = true,
                hasError = false,
            ),
        )
    }

    @Test
    fun reportsLoadErrorBeforeCheckingLoadingOrUrlSupport() {
        assertEquals(
            RssReaderLoadAction.Failed,
            RssReaderLoadRules.action(
                requestedTabId = "tab-1",
                selectedTabId = "tab-1",
                url = "https://example.com/article",
                isLoading = true,
                isPrivate = false,
                hasError = true,
            ),
        )
    }

    @Test
    fun rejectsUnsupportedCompletedUrl() {
        assertEquals(
            RssReaderLoadAction.Unsupported,
            RssReaderLoadRules.action(
                requestedTabId = "tab-1",
                selectedTabId = "tab-1",
                url = "file:///private/article.html",
                isLoading = false,
                isPrivate = false,
                hasError = false,
            ),
        )
    }
}
