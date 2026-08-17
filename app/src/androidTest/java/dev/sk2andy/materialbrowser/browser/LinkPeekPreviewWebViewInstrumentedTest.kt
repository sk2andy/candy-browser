package dev.sk2andy.materialbrowser.browser

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkPeekPreviewWebViewInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    @Test
    fun releaseIsIdempotentAndControllerDestroyDrainsActivePreviews() {
        activityRule.scenario.onActivity { activity ->
            val controller = BrowserController(activity)
            val first = controller.createLinkPeekPreviewWebView(
                url = "https://example.com/first",
                onProgressChanged = {},
                onCommittedUrlChanged = {},
            )
            assertEquals(1, controller.activeLinkPeekPreviewCountForTesting)

            controller.onPause()
            controller.onResume()
            controller.releaseLinkPeekPreviewWebView(first)
            controller.releaseLinkPeekPreviewWebView(first)
            assertEquals(0, controller.activeLinkPeekPreviewCountForTesting)

            controller.createLinkPeekPreviewWebView(
                url = "https://example.com/second",
                onProgressChanged = {},
                onCommittedUrlChanged = {},
            )
            assertEquals(1, controller.activeLinkPeekPreviewCountForTesting)
            controller.destroy()
            assertEquals(0, controller.activeLinkPeekPreviewCountForTesting)
        }
    }

    @Test
    fun previewBlocksListedSubresourcesWithoutAttributingThemToSourceTab() {
        activityRule.scenario.onActivity { activity ->
            val controller = BrowserController(activity)
            val preview = controller.createLinkPeekPreviewWebView(
                url = "https://example.com/article",
                onProgressChanged = {},
                onCommittedUrlChanged = {},
            )

            val response = preview.webViewClient.shouldInterceptRequest(
                preview,
                subresourceRequest("https://doubleclick.net/tracker.js"),
            )

            assertNotNull(response)
            assertEquals(0, controller.selectedTab.blockedCount)
            controller.destroy()
        }
    }

    @Test
    fun previewBlocksConsentRuntimeWhenAdBlockerIsDisabled() {
        activityRule.scenario.onActivity { activity ->
            val controller = BrowserController(activity)
            val originalSettings = controller.blockerSettings
            try {
                controller.updateBlockerSettings(
                    BlockerSettings(
                        blockAdsAndTrackers = false,
                        hideCookieConsent = true,
                        blockThirdPartyCookies = originalSettings.blockThirdPartyCookies,
                    ),
                )
                val preview = controller.createLinkPeekPreviewWebView(
                    url = "https://example.com/article",
                    onProgressChanged = {},
                    onCommittedUrlChanged = {},
                )

                assertNotNull(
                    preview.webViewClient.shouldInterceptRequest(
                        preview,
                        subresourceRequest("https://cmp.inmobi.com/choice.js"),
                    ),
                )
                assertNull(
                    preview.webViewClient.shouldInterceptRequest(
                        preview,
                        subresourceRequest("https://doubleclick.net/tracker.js"),
                    ),
                )
                controller.updateBlockerSettings(
                    controller.blockerSettings.copy(hideCookieConsent = false),
                )
                assertNull(
                    preview.webViewClient.shouldInterceptRequest(
                        preview,
                        subresourceRequest("https://cmp.inmobi.com/choice.js"),
                    ),
                )
            } finally {
                controller.updateBlockerSettings(originalSettings)
                controller.destroy()
            }
        }
    }

    private fun subresourceRequest(url: String): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = false
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }
}
