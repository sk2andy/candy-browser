package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.SystemClock
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.integration.LauncherProfileShortcut
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutPublisher
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutRules
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutState
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutTarget
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherShortcutInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val publisher = LauncherShortcutPublisher(context)

    @After
    fun clearState() {
        context.getSharedPreferences("browser_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences(
            GestureOnboardingStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }

    @Test
    fun shortcutIntentsTargetCandyAndExposeOnlyRequiredProfileId() {
        val newTab = publisher.launchIntent(LauncherShortcutTarget.NewTab)
        val profile = publisher.launchIntent(LauncherShortcutTarget.Profile("work"))

        assertEquals(MainActivity::class.java.name, newTab.component?.className)
        assertEquals(LauncherShortcutRules.ACTION_NEW_TAB, newTab.action)
        assertNull(newTab.extras)
        assertEquals(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            newTab.flags,
        )
        assertEquals(MainActivity::class.java.name, profile.component?.className)
        assertEquals(LauncherShortcutRules.ACTION_OPEN_PROFILE, profile.action)
        assertEquals("work", profile.getStringExtra(LauncherShortcutRules.EXTRA_PROFILE_ID))
        assertEquals(setOf(LauncherShortcutRules.EXTRA_PROFILE_ID), profile.extras?.keySet())
    }

    @Test
    fun publisherRegistersAtMostTwoRecentProfiles() {
        assumeTrue(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) >= 4)
        val published = publisher.publish(
            LauncherShortcutState(
                recentProfiles = listOf(
                    LauncherProfileShortcut("work", "💼"),
                    LauncherProfileShortcut("travel", "🧳"),
                ),
            ),
        )

        assertTrue(published)
        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
            .sortedBy { shortcut -> shortcut.rank }
        assertEquals(
            listOf(
                LauncherShortcutRules.shortcutId(LauncherShortcutTarget.Profile("work")),
                LauncherShortcutRules.shortcutId(LauncherShortcutTarget.Profile("travel")),
            ),
            shortcuts.map { shortcut -> shortcut.id },
        )
        assertTrue(shortcuts.all { shortcut ->
            shortcut.intent.component?.className == LauncherShortcutActivity::class.java.name
        })
        assertEquals(
            listOf("work", "travel"),
            shortcuts.map { shortcut ->
                shortcut.intent.getStringExtra(LauncherShortcutRules.EXTRA_PROFILE_ID)
            },
        )
    }

    @Test
    fun manifestRegistersFixedActionsForCurrentBuildVariant() {
        val shortcuts = context.getSystemService(ShortcutManager::class.java)
            .manifestShortcuts
            .sortedBy { shortcut -> shortcut.rank }

        assertEquals(
            listOf(
                LauncherShortcutRules.shortcutId(LauncherShortcutTarget.NewTab),
                LauncherShortcutRules.shortcutId(LauncherShortcutTarget.NewPrivateTab),
            ),
            shortcuts.map { shortcut -> shortcut.id },
        )
        assertEquals(
            listOf(
                LauncherShortcutRules.ACTION_NEW_TAB,
                LauncherShortcutRules.ACTION_NEW_PRIVATE_TAB,
            ),
            shortcuts.map { shortcut -> shortcut.intent?.action },
        )
        assertTrue(shortcuts.all { shortcut ->
            shortcut.intent?.component?.packageName == context.packageName &&
                shortcut.intent?.component?.className == LauncherShortcutActivity::class.java.name
        })
    }

    @Test
    fun newTabShortcutCreatesSelectedRegularBlankTab() {
        seedSingleProfile()

        ActivityScenario.launch<MainActivity>(
            publisher.launchIntent(LauncherShortcutTarget.NewTab),
        ).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals(2, controller.tabs.size)
                assertFalse(controller.selectedTab.isIncognito)
                assertEquals("about:blank", controller.selectedTab.url)
            }
        }
    }

    @Test
    fun privateTabShortcutCreatesSelectedPrivateBlankTab() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        seedSingleProfile()

        ActivityScenario.launch<MainActivity>(
            publisher.launchIntent(LauncherShortcutTarget.NewPrivateTab),
        ).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals(2, controller.tabs.size)
                assertTrue(controller.selectedTab.isIncognito)
                assertEquals("about:blank", controller.selectedTab.url)
            }
        }
    }

    @Test
    fun warmRegularShortcutPreservesExistingPrivateTab() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        seedSingleProfile()

        ActivityScenario.launch<MainActivity>(
            publisher.launchIntent(LauncherShortcutTarget.NewPrivateTab),
        ).use { scenario ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            lateinit var scenarioIntent: Intent
            lateinit var privateTabId: String
            scenario.onActivity { activity ->
                scenarioIntent = activity.intent
                privateTabId = activity.browserControllerForTesting().selectedTabId
            }

            scenario.onActivity { activity ->
                instrumentation.callActivityOnNewIntent(
                    activity,
                    publisher.launchIntent(LauncherShortcutTarget.NewTab),
                )
            }

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals(3, controller.tabs.size)
                assertTrue(controller.tabs.any { tab -> tab.id == privateTabId && tab.isIncognito })
                assertFalse(controller.selectedTab.isIncognito)
                activity.intent = scenarioIntent
            }
        }
    }

    @Test
    fun staleProfileShortcutLeavesPrivateTabUntouched() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        seedSingleProfile()

        ActivityScenario.launch<MainActivity>(
            publisher.launchIntent(LauncherShortcutTarget.NewPrivateTab),
        ).use { scenario ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            lateinit var scenarioIntent: Intent
            lateinit var privateTabId: String
            scenario.onActivity { activity ->
                scenarioIntent = activity.intent
                privateTabId = activity.browserControllerForTesting().selectedTabId
            }

            scenario.onActivity { activity ->
                instrumentation.callActivityOnNewIntent(
                    activity,
                    publisher.launchIntent(LauncherShortcutTarget.Profile("deleted")),
                )
            }

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals(2, controller.tabs.size)
                assertEquals(privateTabId, controller.selectedTabId)
                assertTrue(controller.selectedTab.isIncognito)
                activity.intent = scenarioIntent
            }
        }
    }

    @Test
    fun staticDispatcherPreservesPrivateTabOnWarmLaunch() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        seedSingleProfile()
        lateinit var controller: BrowserController
        lateinit var privateTabId: String
        lateinit var scenarioIntent: Intent

        ActivityScenario.launch<MainActivity>(
            publisher.launchIntent(LauncherShortcutTarget.NewPrivateTab),
        ).use { scenario ->
            scenario.onActivity { activity ->
                controller = activity.browserControllerForTesting()
                privateTabId = controller.selectedTabId
                scenarioIntent = activity.intent
            }

            context.startActivity(
                Intent(context, LauncherShortcutActivity::class.java)
                    .setAction(LauncherShortcutRules.ACTION_NEW_TAB)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_TASK_ON_HOME,
                    ),
            )
            waitUntil { controller.tabs.size == 3 }

            scenario.onActivity { activity ->
                assertSame(controller, activity.browserControllerForTesting())
                assertTrue(controller.tabs.any { tab -> tab.id == privateTabId && tab.isIncognito })
                assertFalse(controller.selectedTab.isIncognito)
                activity.intent = scenarioIntent
            }
        }
    }

    @Test
    fun profileDispatcherPreservesPrivateTabOnWarmLaunch() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        seedProfiles()
        lateinit var controller: BrowserController
        lateinit var privateTabId: String
        lateinit var scenarioIntent: Intent

        ActivityScenario.launch<MainActivity>(
            publisher.launchIntent(LauncherShortcutTarget.NewPrivateTab),
        ).use { scenario ->
            scenario.onActivity { activity ->
                controller = activity.browserControllerForTesting()
                privateTabId = controller.selectedTabId
                scenarioIntent = activity.intent
            }

            context.startActivity(
                Intent(context, LauncherShortcutActivity::class.java)
                    .setAction(LauncherShortcutRules.ACTION_OPEN_PROFILE)
                    .putExtra(LauncherShortcutRules.EXTRA_PROFILE_ID, "work")
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_TASK_ON_HOME,
                    ),
            )
            waitUntil { controller.activeProfileId == "work" }

            scenario.onActivity { activity ->
                assertSame(controller, activity.browserControllerForTesting())
                assertTrue(controller.tabs.any { tab -> tab.id == privateTabId && tab.isIncognito })
                assertEquals("work-tab", controller.selectedTabId)
                activity.intent = scenarioIntent
            }
        }
    }

    @Test
    fun profileShortcutSelectsExistingProfileTabWithoutCreatingAnotherTab() {
        seedProfiles()

        ActivityScenario.launch<MainActivity>(
            publisher.launchIntent(LauncherShortcutTarget.Profile("work")),
        ).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals("work", controller.activeProfileId)
                assertEquals("work-tab", controller.selectedTabId)
                assertEquals(2, controller.tabs.size)
            }
        }
    }

    private fun seedProfiles() {
        GestureOnboardingStore(context).markCompleted()
        val candyTab = BrowserTab(
            id = "candy-tab",
            lastAccessedAt = 20L,
            profileId = "candy",
        )
        val workTab = BrowserTab(
            id = "work-tab",
            lastAccessedAt = 10L,
            profileId = "work",
        )
        BrowserSessionStore(context).apply {
            saveProfiles(
                profiles = listOf(
                    BrowserProfile("candy", "🍬", selectedTabId = candyTab.id),
                    BrowserProfile("work", "💼", selectedTabId = workTab.id),
                ),
                activeProfileId = "candy",
            )
            saveTabs(listOf(candyTab, workTab), candyTab.id)
        }
    }

    private fun seedSingleProfile() {
        GestureOnboardingStore(context).markCompleted()
        val tab = BrowserTab(
            id = "candy-tab",
            lastAccessedAt = 10L,
            profileId = "candy",
        )
        BrowserSessionStore(context).apply {
            saveProfiles(
                profiles = listOf(BrowserProfile("candy", "🍬", selectedTabId = tab.id)),
                activeProfileId = "candy",
            )
            saveTabs(listOf(tab), tab.id)
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 5_000L
        var satisfied = false
        while (!satisfied && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { satisfied = condition() }
            if (!satisfied) SystemClock.sleep(20L)
        }
        assertTrue("Condition was not met before timeout", satisfied)
    }
}
