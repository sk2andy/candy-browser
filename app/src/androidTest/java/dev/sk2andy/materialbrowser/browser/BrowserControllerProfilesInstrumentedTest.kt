package dev.sk2andy.materialbrowser.browser

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.SnoozedTab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerProfilesInstrumentedTest {
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
    fun disabledProfilesRestoreFirstProfileAndRejectProfileActions() {
        activityRule.scenario.onActivity { activity ->
            val profiles = profiles()
            val store = resetAndSeed(activity, profiles, profiles.last().id)
            store.saveProfilesEnabled(false)
            val controller = BrowserController(activity).also { this.controller = it }

            assertFalse(controller.profilesEnabled)
            assertEquals(profiles.first().id, controller.activeProfileId)
            assertEquals("home-tab", controller.selectedTabId)
            assertEquals(listOf("home-tab"), controller.activeTabs.map(BrowserTab::id))
            assertFalse(controller.selectProfile(profiles.last().id))
            assertFalse(controller.moveTabToProfile("home-tab", profiles.last().id))
            assertNull(controller.createProfile("✈️"))
            controller.snoozedTabs += SnoozedTab(
                tab = BrowserTab(
                    id = "hidden-snoozed-tab",
                    lastAccessedAt = 30L,
                    profileId = profiles.last().id,
                ),
                wakeAtMillis = Long.MAX_VALUE,
                createdAtMillis = 30L,
            )
            assertFalse(controller.openSnoozedTabNow("hidden-snoozed-tab", nowMillis = 40L))
            assertEquals(profiles.first().id, controller.activeProfileId)
        }
    }

    @Test
    fun disablingProfilesSwitchesToFirstWithoutDeletingSavedProfiles() {
        activityRule.scenario.onActivity { activity ->
            val profiles = profiles()
            resetAndSeed(activity, profiles, profiles.last().id)
            val controller = BrowserController(activity).also { this.controller = it }

            assertEquals(profiles.last().id, controller.activeProfileId)

            controller.updateProfilesEnabled(false)

            assertEquals(profiles.first().id, controller.activeProfileId)
            assertEquals(profiles, controller.profiles.toList())
            assertFalse(controller.selectProfile(profiles.last().id))

            controller.updateProfilesEnabled(true)

            assertTrue(controller.selectProfile(profiles.last().id))
            assertEquals(profiles.last().id, controller.activeProfileId)
        }
    }

    private fun resetAndSeed(
        activity: ComponentActivity,
        profiles: List<BrowserProfile>,
        activeProfileId: String,
    ): BrowserSessionStore {
        clear(activity)
        return BrowserSessionStore(activity).also { store ->
            store.saveProfiles(profiles, activeProfileId)
            store.saveTabs(
                tabs = listOf(
                    BrowserTab(
                        id = "home-tab",
                        lastAccessedAt = 10L,
                        profileId = profiles.first().id,
                    ),
                    BrowserTab(
                        id = "work-tab",
                        lastAccessedAt = 20L,
                        profileId = profiles.last().id,
                    ),
                ),
                selectedTabId = "work-tab",
            )
        }
    }

    private fun clear(activity: ComponentActivity) {
        activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun profiles() = listOf(
        BrowserProfile(id = "home", emoji = "🏠", selectedTabId = "home-tab"),
        BrowserProfile(id = "work", emoji = "💼", selectedTabId = "work-tab"),
    )
}
