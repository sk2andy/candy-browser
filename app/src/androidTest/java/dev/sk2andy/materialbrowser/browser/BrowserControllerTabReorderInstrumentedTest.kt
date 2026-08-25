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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerTabReorderInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            clear(activity)
        }
    }

    @Test
    fun reorderClampsToPinGroupAndPersists() {
        activityRule.scenario.onActivity { activity ->
            clear(activity)
            BrowserSessionStore(activity).saveTabs(
                tabs = listOf(
                    tab("pin-a", pinned = true),
                    tab("pin-b", pinned = true),
                    tab("regular-a"),
                    tab("regular-b"),
                ),
                selectedTabId = "regular-a",
            )
            val firstController = BrowserController(activity).also { controller = it }

            assertTrue(firstController.reorderTab("regular-b", 0))
            assertEquals(
                listOf("pin-a", "pin-b", "regular-b", "regular-a"),
                firstController.activeTabs.map(BrowserTab::id),
            )
            assertFalse(firstController.reorderTab("pin-b", 3))
            assertEquals(
                listOf("pin-a", "pin-b", "regular-b", "regular-a"),
                firstController.activeTabs.map(BrowserTab::id),
            )

            firstController.destroy()
            val restoredController = BrowserController(activity).also { controller = it }
            assertEquals(
                listOf("pin-a", "pin-b", "regular-b", "regular-a"),
                restoredController.activeTabs.map(BrowserTab::id),
            )
        }
    }

    @Test
    fun automaticSortingKeepsPinsFirstAndDisablesManualReorder() {
        activityRule.scenario.onActivity { activity ->
            clear(activity)
            val store = BrowserSessionStore(activity)
            store.saveTabs(
                tabs = listOf(
                    tab("pin", pinned = true),
                    tab("regular-a"),
                    tab("regular-b"),
                ),
                selectedTabId = "regular-b",
            )
            store.saveAutomaticTabSortingEnabled(true)
            val browserController = BrowserController(activity).also { controller = it }

            browserController.selectTab("regular-a")

            assertEquals(
                listOf("pin", "regular-b", "regular-a"),
                browserController.activeTabs.map(BrowserTab::id),
            )
            assertFalse(browserController.reorderTab("regular-a", 1))
        }
    }

    private fun tab(id: String, pinned: Boolean = false) = BrowserTab(
        id = id,
        lastAccessedAt = 1L,
        isPinned = pinned,
    )

    private fun clear(activity: ComponentActivity) {
        activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
