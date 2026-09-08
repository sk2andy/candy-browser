package dev.sk2andy.materialbrowser.browser.systemwebview

import android.content.Context
import android.webkit.WebView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.gecko.GeckoRuntimeOwner
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SystemWebViewBrowserEngineInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @Before
    fun setUp() {
        composeRule.activity
            .getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(
                BrowserSessionStore.KEY_ANDROID_BROWSER_ENGINE,
                AndroidBrowserEngineKind.SystemWebView.stableId,
            )
            .commit()
        GeckoRuntimeOwner.resetForTesting()
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            composeRule.activity
                .getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        GeckoRuntimeOwner.resetForTesting()
    }

    @Test
    fun selectedTabUsesSystemWebViewWithoutStartingGecko() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val browserController = BrowserController(activity)
            controller = browserController
            val host = FrameLayout(activity)
            activity.addContentView(
                host,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )

            val engineView = browserController.attachSelectedBrowserEngineView(host)

            assertFalse(browserController.usesGeckoEngine)
            assertNotNull(engineView)
            assertTrue(engineView.containsWebView())
            assertFalse(GeckoRuntimeOwner.hasRuntimeForTesting())
        }
    }

    @Test
    fun edgeToEdgeKeepsRendererAtTopAndPublishesSafeAreaInset() {
        lateinit var webView: WebView
        composeRule.runOnIdle {
            val (browserController, createdWebView) = createControllerWithView()
            controller = browserController
            webView = createdWebView
            browserController.onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setInsets(
                        WindowInsetsCompat.Type.systemBars() or
                            WindowInsetsCompat.Type.displayCutout(),
                        Insets.of(0, EXPECTED_TOP_INSET, 0, 0),
                    )
                    .build(),
            )
            webView.loadDataWithBaseURL(
                "https://safe-area.test/",
                "<html><body>safe area</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        composeRule.runOnIdle {
            assertEquals(0, (webView.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
            webView.evaluateJavascript(
                "${dev.sk2andy.materialbrowser.browser.WebContentTopInsetScript.bridgeName}.topInsetPx()",
            ) { value ->
                result.set(value)
                completed.countDown()
            }
        }

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertTrue(result.get().toInt() >= EXPECTED_TOP_INSET)
    }

    @Test
    fun autoplayBlockingIsEnabledInControllerAndSystemProviderByDefault() {
        composeRule.runOnIdle {
            val (browserController, webView) = createControllerWithView()
            controller = browserController

            assertTrue(browserController.isVideoAutoplayBlocked)
            assertTrue(webView.settings.mediaPlaybackRequiresUserGesture)
        }
    }

    private fun createControllerWithView(): Pair<BrowserController, WebView> {
        val activity = composeRule.activity
        val browserController = BrowserController(activity)
        val host = FrameLayout(activity)
        activity.addContentView(
            host,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val engineView = requireNotNull(browserController.attachSelectedBrowserEngineView(host))
        return browserController to requireNotNull(engineView.findWebView())
    }

    private fun android.view.View?.containsWebView(): Boolean = when (this) {
        is WebView -> true
        is ViewGroup -> (0 until childCount).any { index -> getChildAt(index).containsWebView() }
        else -> false
    }

    private fun android.view.View?.findWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findWebView()
        }
        else -> null
    }

    private companion object {
        const val EXPECTED_TOP_INSET = 96
    }
}
