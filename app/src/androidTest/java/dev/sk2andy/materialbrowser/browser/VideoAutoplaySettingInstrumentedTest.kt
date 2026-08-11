package dev.sk2andy.materialbrowser.browser

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoAutoplaySettingInstrumentedTest {
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
    fun toggleInstallsAndRemovesScriptWithoutChangingExistingMediaPolicy() {
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            val controller = BrowserController(activity).also { this.controller = it }
            assumeTrue(controller.isVideoAutoplayBlockingSupported)
            controller.onResume()
            val webView = controller.selectedWebViewForTesting()

            assertFalse(controller.isVideoAutoplayBlocked)
            assertEquals(0, controller.videoAutoplayScriptHandlerCountForTesting)
            assertFalse(webView.settings.mediaPlaybackRequiresUserGesture)

            controller.updateVideoAutoplayBlocked(true)

            assertTrue(controller.isVideoAutoplayBlocked)
            assertEquals(1, controller.videoAutoplayScriptHandlerCountForTesting)
            assertFalse(webView.settings.mediaPlaybackRequiresUserGesture)

            controller.updateVideoAutoplayBlocked(false)

            assertFalse(controller.isVideoAutoplayBlocked)
            assertEquals(0, controller.videoAutoplayScriptHandlerCountForTesting)
            assertFalse(webView.settings.mediaPlaybackRequiresUserGesture)
        }
    }

    @Test
    fun persistedBlockingAppliesToNewWebViewBeforePlayback() {
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            BrowserSessionStore(activity).saveVideoAutoplayBlocked(true)
            val controller = BrowserController(activity).also { this.controller = it }
            assumeTrue(controller.isVideoAutoplayBlockingSupported)
            controller.onResume()

            assertTrue(controller.isVideoAutoplayBlocked)
            val webView = controller.selectedWebViewForTesting()
            assertEquals(1, controller.videoAutoplayScriptHandlerCountForTesting)
            assertFalse(webView.settings.mediaPlaybackRequiresUserGesture)
        }
    }
}
