package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoInternalPageRulesTest {
    @Test
    fun `normalizes only bounded about page names with valid query or fragment`() {
        assertEquals("about:config", GeckoInternalPageRules.normalizeUrl(" ABOUT:CONFIG "))
        assertEquals("about:buildconfig#Build", GeckoInternalPageRules.normalizeUrl("about:buildconfig#Build"))
        assertEquals("about:networking?test=1", GeckoInternalPageRules.normalizeUrl("about:networking?test=1"))
        listOf(
            null,
            "",
            "about:",
            "about://config",
            "about:config/path",
            "about:config test",
            "about:config?bad=%zz",
            "about:config?x=\u0000",
            "https://example.com",
            "chrome://geckoview/content/config.xhtml",
            "about:" + "x".repeat(32_768),
        ).forEach { url ->
            assertNull(url, GeckoInternalPageRules.normalizeUrl(url))
        }
    }

    @Test
    fun `internal urls remain excluded from external web entrypoints`() {
        assertNull(BrowserUriPolicy.normalizeHttpUrl("about:config"))
        assertNull(BrowserUriPolicy.normalizeExternalUri("about:config"))
    }

    @Test
    fun `only browser navigation or current internal page can open internal destination`() {
        assertTrue(canNavigate(isDirectNavigation = true))
        assertTrue(canNavigate(currentUrl = "about:buildconfig"))
        assertFalse(canNavigate())
        assertFalse(canNavigate(currentUrl = "about:blank"))
        assertFalse(canNavigate(currentUrl = "about:blank#website"))
        assertFalse(canNavigate(currentUrl = "about:srcdoc"))
        assertFalse(canNavigate(isDirectNavigation = true, isRedirect = true))
        assertFalse(canNavigate(isDirectNavigation = true, target = BrowserEngineNavigationTarget.New))
        assertFalse(canNavigate(isDirectNavigation = true, target = BrowserEngineNavigationTarget.None))
        assertFalse(canNavigate(url = "about://config", isDirectNavigation = true))
    }

    @Test
    fun `system webview restores gecko internal tabs as blank while preserving tab identity`() {
        val tab = BrowserTab(
            id = "settings",
            lastAccessedAt = 42L,
            profileId = "work",
            isPinned = true,
            url = "about:config",
            title = "Configuration",
            canGoBack = true,
        )
        assertSame(tab, GeckoInternalPageRules.restoreTab(tab, AndroidBrowserEngineKind.GeckoView))
        val restored = GeckoInternalPageRules.restoreTab(tab, AndroidBrowserEngineKind.SystemWebView)
        assertEquals("settings", restored.id)
        assertEquals("work", restored.profileId)
        assertTrue(restored.isPinned)
        assertTrue(restored.isFreshBlankTab)
        val webTab = tab.copy(url = "https://example.com")
        assertSame(webTab, GeckoInternalPageRules.restoreTab(webTab, AndroidBrowserEngineKind.SystemWebView))
    }

    private fun canNavigate(
        url: String = "about:config",
        currentUrl: String = "https://example.com",
        isDirectNavigation: Boolean = false,
        isRedirect: Boolean = false,
        target: BrowserEngineNavigationTarget = BrowserEngineNavigationTarget.Current,
    ): Boolean = GeckoInternalPageRules.canNavigate(url, currentUrl, isDirectNavigation, isRedirect, target)
}
