package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserFindInPageInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
            clearSession()
        }
    }

    @Test
    fun nextMatchScrollsThroughBrowserScreen() {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }
        composeRule.runOnIdle {
            browserController.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://find.test/",
                """
                    <html>
                    <body style="margin:0">
                      <p>Candy alpha</p>
                      <div style="height:1800px"></div>
                      <p>Candy beta</p>
                      <div style="height:1800px"></div>
                      <p>Candy gamma</p>
                    </body>
                    </html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            browserController.selectedTab.url == "https://find.test/" &&
                browserController.selectedTab.isLoading.not()
        }

        composeRule.runOnIdle {
            assertTrue(browserController.openFindInPage())
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            ViewCompat.getRootWindowInsets(browserController.selectedWebViewForTesting())
                ?.let { insets ->
                    insets.isVisible(WindowInsetsCompat.Type.ime()) &&
                        insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0
                } == true
        }
        composeRule.onNodeWithTag(FindInPageBarTestTags.Query).performTextInput("Candy")
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            browserController.findInPageState?.let { state ->
                state.isDoneCounting && state.matchCount == 3
            } == true
        }
        composeRule.onNodeWithTag(FindInPageBarTestTags.Next).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            browserController.findInPageState?.activeMatchOrdinal == 1
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            browserController.selectedWebViewForTesting().scrollY > 0
        }

        composeRule.runOnIdle {
            assertEquals(1, browserController.findInPageState?.activeMatchOrdinal)
            assertTrue(browserController.selectedWebViewForTesting().scrollY > 0)
        }
    }

    @Test
    fun findModeForwardsImeInsetsToWebView() {
        lateinit var browserController: BrowserController
        val forwardedImeBottom = AtomicInteger(-1)
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }
        composeRule.runOnIdle {
            browserController.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://find.test/",
                "<html><body>Candy</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            browserController.selectedTab.url == "https://find.test/" &&
                browserController.selectedTab.isLoading.not()
        }
        composeRule.runOnIdle {
            ViewCompat.setOnApplyWindowInsetsListener(
                browserController.selectedWebViewForTesting(),
            ) { _, insets ->
                forwardedImeBottom.set(
                    insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
                )
                insets
            }
            browserController.onWindowInsetsChanged(imeInsets(bottom = 0, visible = false))
            assertTrue(browserController.openFindInPage())
        }
        composeRule.onNodeWithTag(FindInPageBarTestTags.Bar).assertExists()
        composeRule.runOnIdle {
            browserController.onWindowInsetsChanged(
                imeInsets(bottom = EXPECTED_IME_BOTTOM, visible = true),
            )
        }

        assertEquals(EXPECTED_IME_BOTTOM, forwardedImeBottom.get())
    }

    private fun imeInsets(bottom: Int, visible: Boolean): WindowInsetsCompat =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
            .setVisible(WindowInsetsCompat.Type.ime(), visible)
            .build()

    private fun clearSession() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private companion object {
        const val EXPECTED_IME_BOTTOM = 731
    }
}
