package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.util.concurrent.atomic.AtomicInteger
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullscreenVideoOverlayInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnUiThread {
            controller?.destroy()
            controller = null
            clearSession()
        }
    }

    @Test
    fun regularVideoMovesBetweenExpandedAndMiniHost() {
        lateinit var browserController: BrowserController
        var callbackCount = 0
        val detachCount = AtomicInteger()
        val videoOnlyPresentation = mutableStateOf(false)
        composeRule.runOnUiThread {
            clearSession()
            browserController = BrowserController(composeRule.activity).also { controller = it }
            browserController.onResume()
            val sourceView = View(composeRule.activity)
            sourceView.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) = Unit

                    override fun onViewDetachedFromWindow(view: View) {
                        detachCount.incrementAndGet()
                    }
                },
            )
            browserController.showFullscreenVideoForTesting(
                view = sourceView,
                callback = WebChromeClient.CustomViewCallback { callbackCount++ },
            )
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                FullscreenVideoOverlay(
                    controller = browserController,
                    videoOnlyPresentation = videoOnlyPresentation.value,
                    onBoundsChanged = {},
                )
            }
        }

        composeRule.onNodeWithTag(FullscreenVideoTestTags.Expanded).assertIsDisplayed()
        composeRule.onNodeWithTag(FullscreenVideoTestTags.Minimize).performClick()
        composeRule.onNodeWithTag(FullscreenVideoTestTags.MiniPlayer).assertIsDisplayed()
        composeRule.runOnUiThread { videoOnlyPresentation.value = true }
        composeRule.onNodeWithTag(FullscreenVideoTestTags.Expanded).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, detachCount.get()) }
        composeRule.runOnUiThread { videoOnlyPresentation.value = false }
        composeRule.onNodeWithTag(FullscreenVideoTestTags.MiniPlayer).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, detachCount.get()) }
        composeRule.onNodeWithTag(FullscreenVideoTestTags.Expand).performClick()
        composeRule.onNodeWithTag(FullscreenVideoTestTags.Expanded).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, detachCount.get()) }
        composeRule.onNodeWithTag(FullscreenVideoTestTags.Minimize).performClick()
        composeRule.onNodeWithTag(FullscreenVideoTestTags.Close).performClick()

        composeRule.onNodeWithTag(FullscreenVideoTestTags.MiniPlayer).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, callbackCount) }
    }

    private fun clearSession() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
