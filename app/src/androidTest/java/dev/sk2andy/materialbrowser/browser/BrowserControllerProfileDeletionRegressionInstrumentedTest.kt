package dev.sk2andy.materialbrowser.browser

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncCacheStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncVaultStore
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDescriptor
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState
import dev.sk2andy.materialbrowser.sync.SyncStatus
import dev.sk2andy.materialbrowser.sync.SyncTab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerProfileDeletionRegressionInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @Test
    fun deletingLocalProfileWhileViewingSyncedProfileKeepsRemoteSelection() {
        activityRule.scenario.onActivity { activity ->
            listOf(BrowserSessionStore.PREFERENCES_NAME, "candy_sync_settings").forEach { name ->
                activity.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            }
            AndroidSyncVaultStore(activity).clear()
            AndroidSyncCacheStore(activity).clear()
            val store = BrowserSessionStore(activity)
            store.saveProfilesEnabled(true)
            store.saveProfiles(
                profiles = listOf(
                    BrowserProfile(id = "home", emoji = "🏠", selectedTabId = "home-tab"),
                    BrowserProfile(id = "work", emoji = "💼", selectedTabId = "work-tab"),
                ),
                activeProfileId = "home",
            )
            store.saveTabs(
                tabs = listOf(
                    BrowserTab(id = "home-tab", lastAccessedAt = 1L, profileId = "home"),
                    BrowserTab(id = "work-tab", lastAccessedAt = 2L, profileId = "work"),
                ),
                selectedTabId = "home-tab",
            )
            val browser = BrowserController(activity).also { controller = it }
            browser.applySyncRepositoryStateForTesting(
                SyncRepositoryState(
                    settings = null,
                    pendingCount = 0,
                    lastCursor = "epoch.0",
                    lastSuccessAt = "2026-11-14T12:00:00Z",
                    status = SyncStatus.Ready,
                    currentDeviceId = "phone",
                    profiles = listOf(
                        SyncProfile(
                            deviceId = "desktop",
                            displayName = "Desktop",
                            icon = SyncDeviceIconDescriptor("computer", 210),
                            revision = 0,
                            tabs = listOf(
                                SyncTab(
                                    candyId = "desktop-page",
                                    windowId = 0,
                                    index = 0,
                                    groupId = null,
                                    active = true,
                                    pinned = false,
                                    title = "Example",
                                    url = "https://example.com/",
                                ),
                            ),
                            lastSeenAt = "2026-11-14T12:00:00Z",
                        ),
                    ),
                ),
            )
            val remoteProfileId = SyncedProfileRuntimeRules.profileId("desktop")
            assertTrue(browser.selectProfile(remoteProfileId))
            val selectedTabId = browser.selectedTabId

            // Non-isolated deletion reaches the same mutation used by the UI's async callback.
            assertTrue(browser.deleteProfile("work"))

            assertFalse(browser.profiles.any { it.id == "work" })
            assertEquals(remoteProfileId, browser.activeProfileId)
            assertEquals(selectedTabId, browser.selectedTabId)
            assertTrue(browser.tabs.any { it.id == "work-tab" && it.profileId == "home" })
        }
    }
}
