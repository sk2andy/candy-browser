package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerTabResidencyInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
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
    fun oldestWebViewIsEvictedWhileTabAndPreviewRemain() {
        lateinit var firstTabId: String
        lateinit var secondTabId: String
        lateinit var thirdTabId: String
        lateinit var preview: Bitmap
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            browserController.updateResidentTabLimit(2)
            firstTabId = browserController.selectedTabId
            browserController.selectedWebViewForTesting()
            preview = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            browserController.previews[firstTabId] = preview

            secondTabId = browserController.createTab()
            browserController.selectedWebViewForTesting()
            thirdTabId = browserController.createTab()
            browserController.selectedWebViewForTesting()
        }

        instrumentation.waitForIdleSync()

        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            assertEquals(3, browserController.tabs.size)
            assertTrue(browserController.tabs.any { tab -> tab.id == firstTabId })
            assertEquals(
                setOf(secondTabId, thirdTabId),
                browserController.residentTabIdsForTesting(),
            )
            assertSame(preview, browserController.previews[firstTabId])
            assertEquals(2, BrowserSessionStore(activity).loadResidentTabLimit())
        }
    }

    @Test
    fun moreThanTwelveTabsCanRemainOpen() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)

            repeat(12) { browserController.createTab() }

            assertEquals(13, browserController.tabs.size)
        }
    }

    @Test
    fun protectedFullscreenTabIsTrimmedAfterProtectionEnds() {
        lateinit var firstTabId: String
        lateinit var secondTabId: String
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            browserController.updateResidentTabLimit(1)
            firstTabId = browserController.selectedTabId
            browserController.selectedWebViewForTesting()
            browserController.showFullscreenVideoForTesting(
                view = View(activity),
                callback = WebChromeClient.CustomViewCallback {},
            )
            secondTabId = browserController.createTab()
            browserController.selectedWebViewForTesting()
        }

        instrumentation.waitForIdleSync()
        activityRule.scenario.onActivity {
            assertEquals(
                setOf(firstTabId, secondTabId),
                requireNotNull(controller).residentTabIdsForTesting(),
            )
            requireNotNull(controller).hideFullscreenVideoForTesting()
        }
        instrumentation.waitForIdleSync()
        activityRule.scenario.onActivity {
            assertEquals(
                setOf(secondTabId),
                requireNotNull(controller).residentTabIdsForTesting(),
            )
        }
    }

    private fun freshController(activity: ComponentActivity): BrowserController {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        return BrowserController(activity).also { controller = it }
    }
}
