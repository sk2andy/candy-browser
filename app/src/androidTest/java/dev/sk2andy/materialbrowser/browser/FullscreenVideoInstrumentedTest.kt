package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullscreenVideoInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    @Test
    fun regularFullscreenVideoSurvivesTabSwitchAndClosesExactlyOnce() {
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val controller = BrowserController(activity).also { this.controller = it }
            controller.onResume()
            val sourceWebView = controller.selectedWebViewForTesting()
            var callbackCount = 0

            controller.showFullscreenVideoForTesting(
                view = View(activity),
                callback = WebChromeClient.CustomViewCallback { callbackCount++ },
            )

            assertEquals(
                FullscreenVideoPlacement.Expanded,
                controller.fullscreenVideoPlacement(videoOnlyPresentation = false),
            )
            assertTrue(controller.isPictureInPictureEligible)

            controller.createTab()

            assertEquals(
                FullscreenVideoPlacement.MiniPlayer,
                controller.fullscreenVideoPlacement(videoOnlyPresentation = false),
            )
            assertEquals(0, callbackCount)
            assertFalse(sourceWebView.settings.mediaPlaybackRequiresUserGesture)

            controller.exitFullscreenVideo()
            controller.exitFullscreenVideo()

            assertNull(controller.fullscreenVideoState)
            assertEquals(1, callbackCount)
            assertTrue(sourceWebView.settings.mediaPlaybackRequiresUserGesture)
        }
    }

    @Test
    fun privateFullscreenVideoEndsInsteadOfFloatingAcrossTabs() {
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val controller = BrowserController(activity).also { this.controller = it }
            assumeTrue(controller.isProfileIsolationSupported)
            assertTrue(controller.setBlankTabIncognito(true))
            controller.onResume()
            var callbackCount = 0

            controller.showFullscreenVideoForTesting(
                view = View(activity),
                callback = WebChromeClient.CustomViewCallback { callbackCount++ },
            )

            assertFalse(controller.isPictureInPictureEligible)
            assertFalse(controller.canMinimizeFullscreenVideo)

            controller.onPause()

            assertNull(controller.fullscreenVideoState)
            assertEquals(1, callbackCount)
        }
    }

    private fun clearSession(activity: ComponentActivity) {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
