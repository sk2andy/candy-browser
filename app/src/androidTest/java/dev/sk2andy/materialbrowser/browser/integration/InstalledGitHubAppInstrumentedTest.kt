package dev.sk2andy.materialbrowser.browser.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.DEFAULT_BROWSER_PROFILE
import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID
import dev.sk2andy.materialbrowser.browser.ExternalAppFeedbackStage
import dev.sk2andy.materialbrowser.browser.ExternalAppLinkHandling
import dev.sk2andy.materialbrowser.browser.StartupAddressFocusMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opt in with installedGitHub=true on a device with the official GitHub app already set up. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class InstalledGitHubAppInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var controller: BrowserController
    private var savedPreferences = emptyMap<String, Map<String, *>>()
    private var savedHistory: List<HistoryEntry>? = null

    @Before
    fun setUp() {
        assumeTrue(
            "Requires -e installedGitHub true and the official GitHub app installed",
            InstrumentationRegistry.getArguments().getString("installedGitHub") == "true",
        )
        assertNotNull(context.packageManager.getApplicationInfo(GITHUB_PACKAGE, 0))
        savedPreferences = listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            GestureOnboardingStore.PREFERENCES_NAME,
            ReleaseNotesStore.PREFERENCES_NAME,
        ).associateWith { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap() }
        val store = BrowserSessionStore(context)
        savedHistory = store.loadHistory()
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
        store.saveProfiles(listOf(DEFAULT_BROWSER_PROFILE), DEFAULT_PROFILE_ID)
        store.saveProfilesEnabled(false)
        val tab = BrowserTab(id = "installed-github-app-test", lastAccessedAt = System.currentTimeMillis())
        assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
        store.saveExternalAppLinkHandling(ExternalAppLinkHandling.Automatic)
        store.saveExternalAppSiteExceptions(emptyList())
        store.saveExternalLinkPreviewEnabled(false)
        store.saveHistoryRecordingMode(HistoryRecordingMode.Disabled)
        store.saveStartupAnimationEnabled(false)
        store.saveStartupAddressFocusMode(StartupAddressFocusMode.Never)
        store.saveOpenHomeOnStartupEnabled(false)
        GestureOnboardingStore(context).markCompleted()
        assertTrue(ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong()))
        assertTrue(store.flush())
        scenario = ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        ).also { activeScenario ->
            activeScenario.onActivity { activity -> controller = activity.browserControllerForTesting() }
        }
        assertForeground(context.packageName)
    }

    @After
    fun tearDown() {
        scenario?.close()
        savedHistory?.let { history -> assertTrue(BrowserSessionStore(context).commitHistory(history)) }
        savedPreferences.forEach { (name, original) ->
            val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = preferences.edit()
            (preferences.all.keys - original.keys).forEach(editor::remove)
            original.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            assertTrue(editor.commit())
            assertEquals(original, preferences.all)
        }
    }

    @Test
    fun realLauncherAcceptsGitHubRootAndOpensRepositoryInInstalledApp() {
        val launcher = ExternalAppLauncher(context)
        val prepared = requireNotNull(launcher.prepareWebUrlInChosenApp("https://github.com"))

        assertEquals("https://github.com", prepared.url)
        assertTrue(prepared.hasDefaultHandler || GITHUB_PACKAGE in prepared.matchingPackages)
        assertEquals(ExternalLaunchResult.Launched, launcher.openWebUrlInChosenApp(prepared))
        assertRootHandoffVisible()
        returnToCandy()

        val repository = requireNotNull(launcher.prepareWebUrlInChosenApp(REPOSITORY_URL))
        assertEquals(REPOSITORY_URL, repository.url)
        assertEquals(ExternalLaunchResult.Launched, launcher.openWebUrlInChosenApp(repository))
        assertForeground(GITHUB_PACKAGE)
    }

    @Test
    fun typedGithubAddressSurvivesBackgroundAndReturnsThroughCandySnackbar() {
        instrumentation.runOnMainSync { controller.submitAddressFromAddressBar("github.com") }
        assertRootHandoffVisible()
        var openedId = 0L
        instrumentation.runOnMainSync {
            val opened = requireNotNull(controller.externalAppFeedback)
            assertEquals(ExternalAppFeedbackStage.Opened, opened.stage)
            assertEquals("github.com", opened.site)
            assertFalse(opened.isPrivate)
            openedId = opened.id
        }

        returnToCandy()
        instrumentation.runOnMainSync {
            assertEquals(openedId, requireNotNull(controller.externalAppFeedback).id)
            assertEquals(ExternalAppFeedbackStage.Opened, controller.externalAppFeedback?.stage)
        }
        clickText(R.string.external_app_feedback_open_in_candy)
        await("Always-in-Candy confirmation") {
            controller.externalAppFeedback?.stage == ExternalAppFeedbackStage.ConfirmAlwaysInCandy
        }
        assertTrue(device.wait(
            Until.hasObject(By.text(context.getString(R.string.external_app_feedback_always))),
            TIMEOUT_MILLIS,
        ))
        instrumentation.runOnMainSync {
            assertEquals("github.com", Uri.parse(controller.selectedTab.url).host)
            assertTrue(controller.externalAppSiteExceptions.isEmpty())
        }
        clickText(R.string.action_cancel)
        await("Dismissed confirmation") { controller.externalAppFeedback == null }
    }

    @Test
    fun explicitOpenInAppLaunchesInstalledGitHubAndKeepsReturnFeedback() {
        instrumentation.runOnMainSync { controller.submitAddress(REPOSITORY_URL) }
        await("GitHub repository finished its initial Candy navigation") {
            Uri.parse(controller.selectedTab.url).host == "github.com" && !controller.selectedTab.isLoading
        }
        instrumentation.runOnMainSync { controller.openSelectedPageExternally() }
        assertForeground(GITHUB_PACKAGE)
        var openedId = 0L
        instrumentation.runOnMainSync {
            val opened = requireNotNull(controller.externalAppFeedback)
            assertEquals(ExternalAppFeedbackStage.Opened, opened.stage)
            openedId = opened.id
        }
        returnToCandy()
        instrumentation.runOnMainSync {
            assertEquals(openedId, requireNotNull(controller.externalAppFeedback).id)
            controller.openExternalAppInCandy(openedId)
            assertEquals(ExternalAppFeedbackStage.ConfirmAlwaysInCandy, controller.externalAppFeedback?.stage)
            controller.dismissExternalAppFeedback(requireNotNull(controller.externalAppFeedback).id)
            assertNull(controller.externalAppFeedback)
        }
    }

    private fun assertRootHandoffVisible() {
        // GitHub accepts its root URL, then may return its unsupported homepage to Android's browser chooser.
        assertTrue(
            "GitHub root did not hand off to the app or its browser chooser",
            device.wait(
                Until.hasObject(By.pkg(Pattern.compile("com\\.github\\.android|com\\.android\\.intentresolver")).depth(0)),
                TIMEOUT_MILLIS,
            ),
        )
        instrumentation.waitForIdleSync()
    }

    private fun returnToCandy() {
        if (device.currentPackageName == RESOLVER_PACKAGE) device.pressBack()
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        assertForeground(context.packageName)
    }

    private fun clickText(resourceId: Int) {
        val text = context.getString(resourceId)
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                device.findObject(By.text(text))?.let { node ->
                    node.click()
                    return
                }
            } catch (_: StaleObjectException) {
                // Reacquire the Snackbar node while its resume animation replaces semantics.
            }
            SystemClock.sleep(50L)
        }
        throw AssertionError("Snackbar action was not available: $text")
    }

    private fun assertForeground(packageName: String) {
        assertTrue(
            "$packageName did not become the foreground app",
            device.wait(Until.hasObject(By.pkg(packageName).depth(0)), TIMEOUT_MILLIS),
        )
        instrumentation.waitForIdleSync()
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            var satisfied = false
            instrumentation.runOnMainSync { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(50L)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    private companion object {
        const val GITHUB_PACKAGE = "com.github.android"
        const val RESOLVER_PACKAGE = "com.android.intentresolver"
        const val REPOSITORY_URL = "https://github.com/sk2andy/candy-browser"
        const val TIMEOUT_MILLIS = 30_000L
    }
}
