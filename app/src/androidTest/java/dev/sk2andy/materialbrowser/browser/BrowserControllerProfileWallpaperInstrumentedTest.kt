package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.ProfileWallpaperStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerProfileWallpaperInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null
    private val profileIds = listOf("home", "work")

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            profileIds.forEach(ProfileWallpaperStore(activity)::delete)
            preferences(activity).edit().clear().commit()
        }
    }

    @Test
    fun profileSwitchLoadsOnlySelectedProfileWallpaper() {
        activityRule.scenario.onActivity { activity ->
            seedProfiles(activity, wallpaper = ProfileWallpaper())
            saveBitmap(
                activity,
                profileIds[0],
                ProfileWallpaperTarget.NewTab,
                width = 40,
                height = 24,
            )
            saveBitmap(
                activity,
                profileIds[1],
                ProfileWallpaperTarget.NewTab,
                width = 72,
                height = 48,
            )
            saveBitmap(
                activity,
                profileIds[0],
                ProfileWallpaperTarget.TabSwitcher,
                width = 56,
                height = 32,
            )
            saveBitmap(
                activity,
                profileIds[1],
                ProfileWallpaperTarget.TabSwitcher,
                width = 88,
                height = 52,
            )
            controller = BrowserController(activity)
            controller?.loadActiveProfileTabSwitcherWallpaper()
        }

        waitUntil { controller?.activeProfileWallpaperBitmap?.width == 40 }
        waitUntil { controller?.activeProfileTabSwitcherWallpaperBitmap?.width == 56 }
        activityRule.scenario.onActivity {
            assertTrue(requireNotNull(controller).selectProfile(profileIds[1]))
            controller?.loadActiveProfileTabSwitcherWallpaper()
        }
        waitUntil { controller?.activeProfileWallpaperBitmap?.width == 72 }
        waitUntil { controller?.activeProfileTabSwitcherWallpaperBitmap?.width == 88 }

        activityRule.scenario.onActivity {
            assertEquals(profileIds[1], requireNotNull(controller).activeProfileId)
            assertEquals(72, requireNotNull(controller).activeProfileWallpaperBitmap?.width)
            assertEquals(
                88,
                requireNotNull(controller).activeProfileTabSwitcherWallpaperBitmap?.width,
            )
            assertTrue(requireNotNull(controller).deleteProfile(profileIds[1]))
        }
        waitUntil { controller?.activeProfileTabSwitcherWallpaperBitmap?.width == 56 }

        activityRule.scenario.onActivity {
            assertEquals(profileIds[0], requireNotNull(controller).activeProfileId)
            assertEquals(
                56,
                requireNotNull(controller).activeProfileTabSwitcherWallpaperBitmap?.width,
            )
        }
    }

    @Test
    fun missingNewTabFileClearsOnlyItsMetadata() {
        activityRule.scenario.onActivity { activity ->
            seedProfiles(activity, wallpaper = ProfileWallpaper(zoom = 2f))
            profileIds.forEach { profileId ->
                saveBitmap(
                    activity,
                    profileId,
                    ProfileWallpaperTarget.TabSwitcher,
                    width = 36,
                    height = 24,
                )
            }
            controller = BrowserController(activity)
        }

        waitUntil {
            controller?.profiles?.firstOrNull { it.id == profileIds[0] }
                ?.newTabWallpaper == null
        }

        activityRule.scenario.onActivity { activity ->
            val runtimeProfile = requireNotNull(controller).profiles.first()
            val storedProfile = BrowserSessionStore(activity).loadProfiles().first.first()
            assertNull(runtimeProfile.newTabWallpaper)
            assertNull(storedProfile.newTabWallpaper)
            assertEquals(ProfileWallpaper(zoom = 2f), runtimeProfile.tabSwitcherWallpaper)
            assertEquals(ProfileWallpaper(zoom = 2f), storedProfile.tabSwitcherWallpaper)
        }
    }

    private fun seedProfiles(activity: ComponentActivity, wallpaper: ProfileWallpaper) {
        preferences(activity).edit().clear().commit()
        val profiles = listOf(
            BrowserProfile(
                "home",
                "🏠",
                selectedTabId = "home-tab",
                newTabWallpaper = wallpaper,
                tabSwitcherWallpaper = wallpaper,
            ),
            BrowserProfile(
                "work",
                "💼",
                selectedTabId = "work-tab",
                newTabWallpaper = wallpaper,
                tabSwitcherWallpaper = wallpaper,
            ),
        )
        BrowserSessionStore(activity).apply {
            saveProfiles(profiles, activeProfileId = profiles.first().id)
            saveTabs(
                tabs = listOf(
                    BrowserTab("home-tab", lastAccessedAt = 1L, profileId = "home"),
                    BrowserTab("work-tab", lastAccessedAt = 2L, profileId = "work"),
                ),
                selectedTabId = "home-tab",
            )
        }
    }

    private fun saveBitmap(
        activity: ComponentActivity,
        profileId: String,
        wallpaperTarget: ProfileWallpaperTarget,
        width: Int,
        height: Int,
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(ProfileWallpaperStore(activity).save(profileId, wallpaperTarget, bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val timeoutAt = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < timeoutAt) {
            var satisfied = false
            activityRule.scenario.onActivity { satisfied = predicate() }
            if (satisfied) return
            SystemClock.sleep(25L)
        }
        error("Timed out waiting for profile wallpaper state")
    }

    private fun preferences(activity: ComponentActivity) = activity.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
}
