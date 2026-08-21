package dev.sk2andy.materialbrowser.ui

import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserWebView
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewScrollBarInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun progressActionScrollsLongPageDirectly() {
        lateinit var webView: BrowserWebView
        var attachedWebView by mutableStateOf<BrowserWebView?>(null)
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { viewContext ->
                            BrowserWebView(viewContext, "scroll-bar-test").also { view ->
                                webView = view
                                view.webViewClient = WebViewClient()
                                view.loadDataWithBaseURL(
                                    "https://example.test/",
                                    "<html><head><meta name='viewport' " +
                                        "content='width=device-width,initial-scale=1'></head>" +
                                        "<body><div style='height:12000px;width:4000px'></div></body></html>",
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                                attachedWebView = view
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    attachedWebView?.let { view ->
                        WebViewScrollBar(
                            webView = view,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            webView.scrollMetricsSnapshot().let { it.rangePx > it.extentPx && it.extentPx > 0 }
        }
        composeRule.runOnIdle {
            assertFalse(webView.isVerticalScrollBarEnabled)
            webView.scrollToVerticalOffset(100)
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule
                .onAllNodesWithContentDescription(
                    context.getString(R.string.scroll_bar_content_description),
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule
            .onNodeWithContentDescription(
                context.getString(R.string.scroll_bar_content_description),
            )
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(0.5f)
            }

        composeRule.runOnIdle {
            val metrics = webView.scrollMetricsSnapshot()
            val expectedMiddle = (metrics.rangePx - metrics.extentPx) / 2
            assertTrue(webView.scrollY in expectedMiddle - 2..expectedMiddle + 2)
        }
    }
}
