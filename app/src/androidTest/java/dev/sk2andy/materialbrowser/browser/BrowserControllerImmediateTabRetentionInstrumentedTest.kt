package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerImmediateTabRetentionInstrumentedTest {
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
    fun immediateLifetimeClearsTabSessionWhenBrowserStops() {
        activityRule.scenario.onActivity { activity ->
            val store = BrowserSessionStore(activity)
            val profiles = listOf(
                BrowserProfile(id = "home", emoji = "🏠", selectedTabId = "second-tab"),
                BrowserProfile(id = "work", emoji = "💼", selectedTabId = "work-tab"),
            )
            val originalTabs = listOf(
                BrowserTab(
                    id = "first-tab",
                    lastAccessedAt = 10L,
                    profileId = profiles.first().id,
                    isPinned = true,
                    url = "https://example.com/first",
                ),
                BrowserTab(
                    id = "second-tab",
                    lastAccessedAt = 20L,
                    profileId = profiles.first().id,
                    url = "https://example.com/second",
                ),
                BrowserTab(
                    id = "work-tab",
                    lastAccessedAt = 30L,
                    profileId = profiles.last().id,
                    url = "https://example.com/work",
                ),
            )
            store.saveProfiles(profiles, profiles.first().id)
            store.saveTabsImmediately(originalTabs, "second-tab")
            val browserController = BrowserController(activity).also { controller = it }
            browserController.updateInactiveTabLifetime(InactiveTabLifetime.Immediately)
            browserController.onStart()

            browserController.onStop(isInPictureInPictureMode = false)

            assertEquals(1, browserController.tabs.size)
            assertTrue(browserController.tabs.single().isFreshBlankTab)
            assertFalse(browserController.tabs.any { tab ->
                originalTabs.any { original -> original.id == tab.id }
            })
            val (persistedTabs, persistedSelection) = store.loadTabs(System.currentTimeMillis())
            assertEquals(browserController.tabs.toList(), persistedTabs)
            assertEquals(browserController.selectedTabId, persistedSelection)
            assertNull(browserController.profiles.single { it.id == profiles.last().id }.selectedTabId)
        }
    }

    @Test
    fun immediateLifetimeKeepsTabsDuringPictureInPictureStop() {
        activityRule.scenario.onActivity { activity ->
            val browserController = BrowserController(activity).also { controller = it }
            val originalTabId = browserController.selectedTabId
            browserController.updateInactiveTabLifetime(InactiveTabLifetime.Immediately)
            browserController.onStart()

            browserController.onStop(isInPictureInPictureMode = true)

            assertTrue(browserController.tabs.any { it.id == originalTabId })
        }
    }

    @Test
    fun immediateLifetimeClearsTabsAfterPictureInPictureTransitionTimesOut() {
        lateinit var browserController: BrowserController
        lateinit var originalTabId: String
        activityRule.scenario.onActivity { activity ->
            browserController = BrowserController(activity).also { controller = it }
            originalTabId = browserController.selectedTabId
            browserController.updateInactiveTabLifetime(InactiveTabLifetime.Immediately)
            browserController.onStart()
            browserController.prepareForPictureInPicture()
            browserController.onStop(isInPictureInPictureMode = false)
        }

        SystemClock.sleep(2_500L)

        activityRule.scenario.onActivity {
            assertFalse(browserController.tabs.any { it.id == originalTabId })
            assertTrue(browserController.tabs.single().isFreshBlankTab)
        }
    }

    @Test
    fun immediateLifetimeLeavesActiveCapsuleBeforeClearingItsTab() {
        activityRule.scenario.onActivity { activity ->
            val browserController = BrowserController(activity).also { controller = it }
            val capsule = SiteCapsule(
                id = "04a74ad8-7533-460c-bfbf-a135968940d5",
                name = "Example Capsule",
                startUrl = "https://example.com",
                profileId = browserController.activeProfileId,
                createdAtMillis = 1L,
                updatedAtMillis = 1L,
            )
            browserController.siteCapsules += capsule
            assertTrue(browserController.openSiteCapsule(capsule.id, navigateToStart = false))
            browserController.updateInactiveTabLifetime(InactiveTabLifetime.Immediately)
            browserController.onStart()

            browserController.onStop(isInPictureInPictureMode = false)

            assertNull(browserController.activeCapsuleId)
            assertNull(browserController.activeCapsuleTabId)
            assertTrue(browserController.tabs.single().isFreshBlankTab)
        }
    }
}
