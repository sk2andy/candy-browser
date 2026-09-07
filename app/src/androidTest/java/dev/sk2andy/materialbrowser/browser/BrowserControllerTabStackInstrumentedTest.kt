package dev.sk2andy.materialbrowser.browser

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerTabStackInstrumentedTest {
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
    fun collapsedStackUsesChosenPreviewAndSelectedIdentityAfterRestore() {
        activityRule.scenario.onActivity { activity ->
            seedTabs(activity)
            val firstController = BrowserController(activity).also { controller = it }
            firstController.selectTab("two")
            val stackId = firstController.createTabStack(
                tabIds = listOf("one", "two"),
                name = "Research",
                color = TabStackColor.Blueberry,
                previewTabId = "one",
            )
            assertNotNull(stackId)
            assertTrue(
                firstController.toggleTabStackCollapsed(
                    stackId = requireNotNull(stackId),
                    triggerTabId = "two",
                ),
            )
            assertEquals(
                listOf("two", "three"),
                firstController.gridOverviewTabs.map(BrowserTab::id),
            )

            firstController.destroy()
            val restoredController = BrowserController(activity).also { controller = it }

            assertEquals("Research", restoredController.activeTabStacks.single().name)
            assertEquals("one", restoredController.activeTabStacks.single().previewTabId)
            assertEquals("two", restoredController.activeTabStacks.single().collapsedAnchorTabId)
            assertTrue(restoredController.activeTabStacks.single().isCollapsed)
            assertEquals(
                listOf("two", "three"),
                restoredController.gridOverviewTabs.map(BrowserTab::id),
            )
        }
    }

    @Test
    fun closingMemberDissolvesTwoTabStack() {
        activityRule.scenario.onActivity { activity ->
            seedTabs(activity)
            val browserController = BrowserController(activity).also { controller = it }
            assertNotNull(
                browserController.createTabStack(
                    tabIds = listOf("one", "two"),
                    name = "Research",
                    color = TabStackColor.Grape,
                ),
            )

            browserController.closeTab("two")

            assertTrue(browserController.activeTabStacks.isEmpty())
            assertTrue(BrowserSessionStore(activity).loadTabStacks(browserController.tabs).isEmpty())
        }
    }

    @Test
    fun undoSnoozeRestoresStackMembershipAndCover() {
        activityRule.scenario.onActivity { activity ->
            seedTabs(activity)
            val browserController = BrowserController(activity).also { controller = it }
            val stackId = requireNotNull(
                browserController.createTabStack(
                    tabIds = listOf("one", "two"),
                    name = "Research",
                    color = TabStackColor.Blueberry,
                    previewTabId = "two",
                ),
            )
            val nowMillis = System.currentTimeMillis()

            val token = requireNotNull(
                browserController.snoozeTab(
                    tabId = "two",
                    wakeAtMillis = nowMillis + 60_000L,
                    nowMillis = nowMillis,
                ),
            )
            assertTrue(browserController.activeTabStacks.isEmpty())

            assertTrue(browserController.undoSnooze(token, nowMillis + 1L))

            val restoredStack = browserController.activeTabStacks.single()
            assertEquals(stackId, restoredStack.id)
            assertEquals(listOf("one", "two"), restoredStack.tabIds)
            assertEquals("two", restoredStack.previewTabId)
        }
    }

    @Test
    fun creatingStackPreservesOtherProfileStacks() {
        activityRule.scenario.onActivity { activity ->
            val store = BrowserSessionStore(activity)
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            val tabs = listOf(
                BrowserTab(id = "home-one", lastAccessedAt = 1L, profileId = "home"),
                BrowserTab(id = "home-two", lastAccessedAt = 2L, profileId = "home"),
                BrowserTab(id = "work-one", lastAccessedAt = 3L, profileId = "work"),
                BrowserTab(id = "work-two", lastAccessedAt = 4L, profileId = "work"),
            )
            store.saveProfiles(
                profiles = listOf(
                    BrowserProfile(id = "home", emoji = "🏠", selectedTabId = "home-one"),
                    BrowserProfile(id = "work", emoji = "💼", selectedTabId = "work-one"),
                ),
                activeProfileId = "home",
            )
            store.saveTabs(tabs, selectedTabId = "home-one")
            store.saveTabStacks(
                stacks = listOf(
                    TabStack(
                        id = "work-stack",
                        profileId = "work",
                        name = "Work",
                        color = TabStackColor.Cherry,
                        tabIds = listOf("work-one", "work-two"),
                    ),
                ),
                tabs = tabs,
            )
            val browserController = BrowserController(activity).also { controller = it }

            assertNotNull(
                browserController.createTabStack(
                    tabIds = listOf("home-one", "home-two"),
                    name = "Home",
                    color = TabStackColor.Lime,
                ),
            )

            assertEquals(setOf("Home", "Work"), browserController.tabStacks.map { it.name }.toSet())
        }
    }

    private fun seedTabs(activity: ComponentActivity) {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        BrowserSessionStore(activity).saveTabs(
            tabs = listOf(
                BrowserTab(id = "one", lastAccessedAt = 1L),
                BrowserTab(id = "two", lastAccessedAt = 2L),
                BrowserTab(id = "three", lastAccessedAt = 3L),
            ),
            selectedTabId = "one",
        )
    }
}
