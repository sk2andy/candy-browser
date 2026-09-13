package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GeckoSessionStateStore
import dev.sk2andy.materialbrowser.browser.gecko.GeckoSessionStateSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerClosedTabUndoInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    @Test
    fun switchingOrCreatingProfilesClearsUndo() {
        activityRule.scenario.onActivity { activity ->
            val browser = freshController(activity)
            browser.updateClosedTabUndoEnabled(true)
            val firstProfileId = browser.activeProfileId
            browser.closeTabFromUser(browser.createTab())
            val firstToken = requireNotNull(browser.closedTabUndoOffer)
            val newProfileId = requireNotNull(browser.createProfile("🍬"))
            assertNull(browser.closedTabUndoOffer)
            assertFalse(browser.undoClosedTab(firstToken))
            browser.closeTabFromUser(browser.selectedTabId)
            assertNotNull(browser.closedTabUndoOffer)
            assertTrue(browser.selectProfile(firstProfileId))
            assertNull(browser.closedTabUndoOffer)
            assertTrue(browser.selectProfile(newProfileId))
        }
    }

    @Test
    fun undoRetainsEngineStateAndPreviewUntilDismissal() {
        activityRule.scenario.onActivity { activity ->
            val browser = freshController(activity)
            browser.updateClosedTabUndoEnabled(true)
            val tabId = browser.createTab(initialUrl = "https://example.com/restore")
            val snapshot = GeckoSessionStateSnapshot(tabId, browser.activeProfileId, "{state}")
            val store = GeckoSessionStateStore(activity)
            assertTrue(store.save(snapshot))
            val preview = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            browser.previews[tabId] = preview
            browser.favicons[tabId] = preview

            browser.closeTabFromUser(tabId)
            assertEquals(snapshot, store.load(tabId))
            assertTrue(browser.undoClosedTab(requireNotNull(browser.closedTabUndoOffer)))
            assertEquals(preview, browser.previews[tabId])
            assertEquals(preview, browser.favicons[tabId])
            assertEquals(snapshot, store.load(tabId))

            browser.closeTabFromUser(tabId)
            browser.dismissClosedTabUndo()
            assertNull(store.load(tabId))
        }
    }

    @Test
    fun undoIsOptInAndPreferencePersists() {
        activityRule.scenario.onActivity { activity ->
            val browser = freshController(activity)
            assertFalse(browser.isClosedTabUndoEnabled)
            browser.closeTabFromUser(browser.selectedTabId)
            assertNull(browser.closedTabUndoOffer)

            browser.updateClosedTabUndoEnabled(true)
            assertTrue(BrowserSessionStore(activity).loadClosedTabUndoEnabled())
            browser.destroy()
            controller = BrowserController(activity)
            assertTrue(requireNotNull(controller).isClosedTabUndoEnabled)
        }
    }

    @Test
    fun undoRestoresLastTabAndRemovesBlankReplacement() {
        activityRule.scenario.onActivity { activity ->
            val browser = freshController(activity)
            browser.updateClosedTabUndoEnabled(true)
            val tabId = browser.selectedTabId
            browser.closeTabFromUser(tabId)
            assertFalse(browser.tabs.any { it.id == tabId })
            val token = requireNotNull(browser.closedTabUndoOffer)

            assertTrue(browser.undoClosedTab(token))
            assertEquals(listOf(tabId), browser.tabs.map(BrowserTab::id))
            assertEquals(tabId, browser.selectedTabId)
            assertNull(browser.closedTabUndoOffer)
            assertFalse(browser.undoClosedTab(token))
        }
    }

    @Test
    fun privateCloseAndUndoNeverEnterPersistentTabs() {
        activityRule.scenario.onActivity { activity ->
            val browser = freshController(activity)
            browser.updateClosedTabUndoEnabled(true)
            val privateTabId = browser.createTab(isIncognito = true)
            browser.closeTabFromUser(privateTabId)
            val token = requireNotNull(browser.closedTabUndoOffer)
            assertTrue(token.tab.isIncognito)
            assertTrue(browser.undoClosedTab(token))
            assertTrue(browser.tabs.first { it.id == privateTabId }.isIncognito)
            assertTrue(BrowserSessionStore(activity).loadTabs().first.none(BrowserTab::isIncognito))
        }
    }

    @Test
    fun newCloseDisableAndAutomaticCloseRejectStaleUndo() {
        activityRule.scenario.onActivity { activity ->
            val browser = freshController(activity)
            browser.updateClosedTabUndoEnabled(true)
            browser.closeTabFromUser(browser.createTab())
            val oldToken = requireNotNull(browser.closedTabUndoOffer)
            browser.closeTabFromUser(browser.createTab())
            assertFalse(browser.undoClosedTab(oldToken))
            assertNotNull(browser.closedTabUndoOffer)
            browser.updateClosedTabUndoEnabled(false)
            assertNull(browser.closedTabUndoOffer)
            browser.updateClosedTabUndoEnabled(true)
            browser.closeTab(browser.createTab())
            assertNull(browser.closedTabUndoOffer)
            browser.closeAllTabs()
            assertNull(browser.closedTabUndoOffer)
        }
    }

    @Test
    fun offerExpiresAfterThreeSeconds() {
        activityRule.scenario.onActivity { activity ->
            val browser = freshController(activity)
            browser.updateClosedTabUndoEnabled(true)
            browser.closeTabFromUser(browser.createTab())
            assertNotNull(browser.closedTabUndoOffer)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        var expired = false
        while (!expired && android.os.SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { expired = controller?.closedTabUndoOffer == null }
            if (!expired) android.os.SystemClock.sleep(50)
        }
        assertTrue(expired)
    }

    @Test
    fun stoppingActivityClearsUndo() {
        activityRule.scenario.onActivity { activity ->
            val browser = freshController(activity)
            browser.updateClosedTabUndoEnabled(true)
            browser.closeTabFromUser(browser.createTab())
            browser.onStop()
            assertNull(browser.closedTabUndoOffer)
        }
    }

    private fun freshController(activity: ComponentActivity): BrowserController {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE,
        ).edit().clear().commit()
        return BrowserController(activity).also { controller = it }
    }
}
