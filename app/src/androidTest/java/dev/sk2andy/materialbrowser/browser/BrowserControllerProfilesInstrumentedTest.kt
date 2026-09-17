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
import org.junit.Assume.assumeTrue
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
            ProfileProtectionSession.forget("home")
            ProfileProtectionSession.forget("work")
            clear(activity)
        }
    }

    @Test
    fun lockedProfileSwitchWaitsForStrongBiometricResult() {
        activityRule.scenario.onActivity { activity ->
            val profiles = profiles().map { profile ->
                if (profile.id == "work") {
                    profile.copy(
                        protection = ProfileProtection(ProfileLockTrigger.AppBackgrounded),
                    )
                } else {
                    profile
                }
            }
            resetAndSeed(activity, profiles, activeProfileId = "home")
            var purpose: ProfileAuthenticationPurpose? = null
            var authenticationResult: ((Boolean) -> Unit)? = null
            val controller = BrowserController(
                activity = activity,
                profileProtectionSupported = { true },
                authenticateProfile = { requestedPurpose, onResult ->
                    purpose = requestedPurpose
                    authenticationResult = onResult
                },
            ).also { this.controller = it }

            assertTrue("work" in controller.lockedProfileIds)
            var selected = false
            controller.requestProfileSelection("work") { success -> selected = success }
            assertEquals("home", controller.activeProfileId)
            assertEquals(ProfileAuthenticationPurpose.Unlock, purpose)

            requireNotNull(authenticationResult).invoke(true)

            assertTrue(selected)
            assertEquals("work", controller.activeProfileId)
            assertFalse(controller.isActiveProfileLocked)
        }
    }

    @Test
    fun authenticationResultIsRejectedAfterAppBackgrounds() {
        activityRule.scenario.onActivity { activity ->
            val protected = profiles().map { profile ->
                if (profile.id == "work") {
                    profile.copy(
                        protection = ProfileProtection(ProfileLockTrigger.AppBackgrounded),
                    )
                } else {
                    profile
                }
            }
            resetAndSeed(activity, protected, activeProfileId = "home")
            var authenticationResult: ((Boolean) -> Unit)? = null
            val controller = BrowserController(
                activity = activity,
                profileProtectionSupported = { true },
                authenticateProfile = { _, onResult -> authenticationResult = onResult },
            ).also { this.controller = it }
            var selected = false

            controller.requestProfileSelection("work") { success -> selected = success }
            controller.onAppBackgrounded(nowElapsedRealtime = 1_000L)
            requireNotNull(authenticationResult).invoke(true)

            assertFalse(selected)
            assertEquals("home", controller.activeProfileId)
            assertTrue("work" in controller.lockedProfileIds)
        }
    }

    @Test
    fun unavailableBiometricsCanLeaveLockedProfileAndExportRequiresAuthentication() {
        activityRule.scenario.onActivity { activity ->
            val protected = profiles().map { profile ->
                if (profile.id == "work") {
                    profile.copy(
                        protection = ProfileProtection(ProfileLockTrigger.AppBackgrounded),
                    )
                } else {
                    profile
                }
            }
            resetAndSeed(activity, protected, activeProfileId = "work")
            var biometricAvailable = false
            var authenticationResult: ((Boolean) -> Unit)? = null
            val controller = BrowserController(
                activity = activity,
                profileProtectionSupported = { biometricAvailable },
                authenticateProfile = { _, onResult -> authenticationResult = onResult },
            ).also { this.controller = it }

            assertTrue(controller.isActiveProfileLocked)
            assertTrue(controller.canLeaveLockedProfile)
            assertTrue(controller.leaveLockedProfile())
            assertEquals("home", controller.activeProfileId)

            biometricAvailable = true
            controller.onAppForegrounded(nowElapsedRealtime = 1_000L)
            var exported = false
            controller.authenticateProtectedProfilesForExport { success -> exported = success }
            requireNotNull(authenticationResult).invoke(true)

            assertTrue(exported)
        }
    }

    @Test
    fun leavingLockedProfileReEnablesDisabledProfiles() {
        activityRule.scenario.onActivity { activity ->
            val protected = profiles().map { profile ->
                if (profile.id == "home") {
                    profile.copy(
                        protection = ProfileProtection(ProfileLockTrigger.AppClosed),
                    )
                } else {
                    profile
                }
            }
            resetAndSeed(activity, protected, activeProfileId = "home")
            val controller = BrowserController(
                activity = activity,
                profileProtectionSupported = { false },
            ).also { this.controller = it }
            controller.updateProfilesEnabled(false)

            assertTrue(controller.isActiveProfileLocked)
            assertTrue(controller.canLeaveLockedProfile)
            assertTrue(controller.leaveLockedProfile())

            assertTrue(controller.profilesEnabled)
            assertEquals("work", controller.activeProfileId)
        }
    }

    @Test
    fun profileActionsRequireAuthenticationWithoutSwitchingProfiles() {
        activityRule.scenario.onActivity { activity ->
            val protected = profiles().map { profile ->
                if (profile.id == "work") {
                    profile.copy(
                        protection = ProfileProtection(ProfileLockTrigger.AppBackgrounded),
                    )
                } else {
                    profile
                }
            }
            resetAndSeed(activity, protected, activeProfileId = "home")
            var authenticationResult: ((Boolean) -> Unit)? = null
            val controller = BrowserController(
                activity = activity,
                profileProtectionSupported = { true },
                authenticateProfile = { _, onResult -> authenticationResult = onResult },
            ).also { this.controller = it }
            var accessGranted = false

            controller.requestProfileAccess("work") { granted -> accessGranted = granted }
            assertFalse(accessGranted)
            assertEquals("home", controller.activeProfileId)

            requireNotNull(authenticationResult).invoke(true)

            assertTrue(accessGranted)
            assertEquals("home", controller.activeProfileId)
            assertFalse("work" in controller.lockedProfileIds)

            controller.onAppBackgrounded(nowElapsedRealtime = 1_000L)

            assertTrue("work" in controller.lockedProfileIds)
            assertFalse(controller.updateProfileEmoji("work", "🔒"))
            assertFalse(
                controller.updateProfileWallpaper(
                    profileId = "work",
                    wallpaperTarget = ProfileWallpaperTarget.NewTab,
                    wallpaper = null,
                ),
            )
            assertFalse(controller.setProfileIsolation("work", enabled = true))
            var deleted = true
            controller.deleteProfileAsync("work") { success -> deleted = success }
            assertFalse(deleted)
        }
    }

    @Test
    fun backgroundAndCooldownPoliciesLockOnlyAtTheirBoundary() {
        activityRule.scenario.onActivity { activity ->
            val protected = profiles().map { profile ->
                if (profile.id == "work") {
                    profile.copy(
                        protection = ProfileProtection(
                            lockTrigger = ProfileLockTrigger.Cooldown,
                            cooldownMinutes = 2,
                        ),
                    )
                } else {
                    profile
                }
            }
            ProfileProtectionSession.unlock("work")
            resetAndSeed(activity, protected, activeProfileId = "work")
            val controller = BrowserController(
                activity = activity,
                profileProtectionSupported = { true },
            ).also { this.controller = it }

            assertFalse(controller.isActiveProfileLocked)
            controller.onAppBackgrounded(nowElapsedRealtime = 1_000L)
            controller.onAppForegrounded(nowElapsedRealtime = 120_999L)
            assertFalse(controller.isActiveProfileLocked)

            controller.onAppBackgrounded(nowElapsedRealtime = 1_000L)
            controller.onAppForegrounded(nowElapsedRealtime = 121_000L)
            assertTrue(controller.isActiveProfileLocked)
        }
    }

    @Test
    fun backgroundAndClosePoliciesUseDifferentLifecycleBoundaries() {
        activityRule.scenario.onActivity { activity ->
            val protected = profiles().map { profile ->
                when (profile.id) {
                    "home" -> profile.copy(
                        protection = ProfileProtection(ProfileLockTrigger.AppBackgrounded),
                    )
                    "work" -> profile.copy(
                        protection = ProfileProtection(ProfileLockTrigger.AppClosed),
                    )
                    else -> profile
                }
            }
            ProfileProtectionSession.unlock("home")
            ProfileProtectionSession.unlock("work")
            resetAndSeed(activity, protected, activeProfileId = "home")
            val controller = BrowserController(
                activity = activity,
                profileProtectionSupported = { true },
            ).also { this.controller = it }

            controller.onAppBackgrounded(nowElapsedRealtime = 1_000L)

            assertTrue("home" in controller.lockedProfileIds)
            assertFalse("work" in controller.lockedProfileIds)

            controller.destroy(lockClosedProfiles = true)

            assertFalse(ProfileProtectionSession.isUnlocked("work"))
            this.controller = null
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

    @Test
    fun closeAllTabsOnlyClosesDeletableTabsInActiveProfile() {
        activityRule.scenario.onActivity { activity ->
            val profiles = profiles()
            resetAndSeed(activity, profiles, profiles.last().id)
            val controller = BrowserController(activity).also { this.controller = it }
            val backgroundTabId = requireNotNull(
                controller.createBackgroundTab("https://example.com/second"),
            )
            assertTrue(controller.setTabPinned("work-tab", true))

            assertEquals(1, controller.closeAllTabs())
            assertEquals(listOf("work-tab"), controller.activeTabs.map(BrowserTab::id))
            assertTrue(controller.tabs.any { it.id == "home-tab" })
            assertTrue(controller.tabs.none { it.id == backgroundTabId })

            assertTrue(controller.setTabPinned("work-tab", false))
            assertEquals(1, controller.closeAllTabs())
            assertEquals(1, controller.activeTabs.size)
            assertEquals(BLANK_URL, controller.activeTabs.single().url)
            assertTrue(controller.activeTabs.none { it.id == "work-tab" })
            assertTrue(controller.tabs.any { it.id == "home-tab" })
        }
    }

    @Test
    fun closeAllPrivateTabsClosesPinnedPrivateTabsAcrossProfiles() {
        activityRule.scenario.onActivity { activity ->
            val profiles = profiles()
            resetAndSeed(activity, profiles, profiles.last().id)
            val controller = BrowserController(activity).also { this.controller = it }
            assumeTrue(controller.isProfileIsolationSupported)
            val workPrivateTabId = controller.createTab(
                initialUrl = "https://private.example/work",
                isIncognito = true,
            )
            assertTrue(controller.setTabPinned(workPrivateTabId, true))
            assertTrue(controller.selectProfile(profiles.first().id))
            val homePrivateTabId = controller.createTab(
                initialUrl = "https://private.example/home",
                isIncognito = true,
            )

            assertEquals(2, controller.closeAllPrivateTabs())

            assertTrue(controller.tabs.none(BrowserTab::isIncognito))
            assertTrue(controller.tabs.none { it.id == workPrivateTabId })
            assertTrue(controller.tabs.none { it.id == homePrivateTabId })
            assertEquals("home-tab", controller.selectedTabId)
            assertEquals(
                setOf("home-tab", "work-tab"),
                controller.tabs.mapTo(hashSetOf()) { it.id },
            )
        }
    }

    @Test
    fun duplicateSelectedTabKeepsProfileBeyondFiftyTabs() {
        activityRule.scenario.onActivity { activity ->
            val profiles = profiles()
            resetAndSeed(activity, profiles, profiles.last().id)
            val controller = BrowserController(activity).also { this.controller = it }

            assertNull(controller.duplicateSelectedTab())

            val sourceId = controller.createTab("https://example.com/articles/42")
            val duplicateId = requireNotNull(controller.duplicateSelectedTab())
            val duplicate = controller.activeTabs.first { it.id == duplicateId }
            assertFalse(duplicateId == sourceId)
            assertEquals("https://example.com/articles/42", duplicate.url)
            assertEquals(profiles.last().id, duplicate.profileId)
            assertFalse(duplicate.isIncognito)
            assertEquals(duplicateId, controller.selectedTabId)

            repeat(51 - controller.tabs.size) { index ->
                controller.createTab("https://capacity.example/$index")
            }
            val selectedUrlBeyondFifty = controller.selectedTab.url
            val duplicateBeyondFiftyId = requireNotNull(controller.duplicateSelectedTab())
            val duplicateBeyondFifty = controller.activeTabs.first { it.id == duplicateBeyondFiftyId }

            assertEquals(52, controller.tabs.size)
            assertEquals(duplicateBeyondFiftyId, controller.selectedTabId)
            assertEquals(profiles.last().id, duplicateBeyondFifty.profileId)
            assertEquals(selectedUrlBeyondFifty, duplicateBeyondFifty.url)
        }
    }

    @Test
    fun duplicateSelectedPrivateTabKeepsPrivacyMode() {
        activityRule.scenario.onActivity { activity ->
            val profiles = profiles()
            resetAndSeed(activity, profiles, profiles.last().id)
            val controller = BrowserController(activity).also { this.controller = it }
            assumeTrue(controller.isProfileIsolationSupported)
            val sourceId = controller.createTab(
                initialUrl = "https://private.example/path",
                isIncognito = true,
            )

            val duplicateId = requireNotNull(controller.duplicateSelectedTab())
            val duplicate = controller.activeTabs.first { it.id == duplicateId }

            assertFalse(duplicateId == sourceId)
            assertEquals("https://private.example/path", duplicate.url)
            assertEquals(profiles.last().id, duplicate.profileId)
            assertTrue(duplicate.isIncognito)
            assertEquals(duplicateId, controller.selectedTabId)
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
