package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.gecko.GeckoRuntimeOwner
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewRuntimeHandle
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoSettingsExtensionFlowInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    init {
        clearPreferences()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        composeRule.activityRule.scenario.close()
        clearPreferences()
    }

    @Test
    fun syncDestinationRendersThroughRealMainMenuWithoutFlowRowLinkageCrash() {
        openSettings()

        composeRule.onNodeWithText(context.getString(R.string.sync_settings_title))
            .performClick()

        composeRule.onNodeWithTag(SyncSettingsTestTags.Endpoint).assertIsDisplayed()
        composeRule.onNodeWithTag(SyncSettingsTestTags.AccentColors)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun settingsExtensionBackReturnsToSettingsHome() {
        openSettings()
        composeRule.onNodeWithText(context.getString(R.string.gecko_extensions_title))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(FirefoxExtensionManagerTestTags.Overlay).assertIsDisplayed()

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(
            device.swipe(
                1,
                device.displayHeight / 2,
                device.displayWidth * 3 / 4,
                device.displayHeight / 2,
                EDGE_BACK_SWIPE_STEPS,
            ),
        )
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(FirefoxExtensionManagerTestTags.Overlay)
                .fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag(FirefoxExtensionManagerTestTags.Overlay).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.settings_title)).assertIsDisplayed()
    }

    @Test
    fun extensionOptionsUseDedicatedChromeAndSystemBackReturnsToOpener() {
        val fixtureOriginHost = ensureBuiltInExtensionFixture()
        var originalTabId = ""
        var originalTabCount = 0
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            controller.submitAddress(ORIGINAL_PAGE_URL)
            originalTabId = controller.selectedTabId
            originalTabCount = controller.tabs.size
        }

        openBrowserMenu()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.FirefoxExtensions)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(
                FirefoxExtensionManagerTestTags.optionsPage(FIXTURE_EXTENSION_ID),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.gecko_extension_install),
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(
            FirefoxExtensionManagerTestTags.optionsPage(FIXTURE_EXTENSION_ID),
        ).assertIsDisplayed().performClick()

        var optionsTabId = ""
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            var optionsReady = false
            composeRule.activityRule.scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                optionsTabId = controller.selectedTabId
                optionsReady = controller.selectedTab.url.startsWith("moz-extension://") &&
                    controller.selectedTab.canGoBack &&
                    controller.selectedGeckoViewForTesting() != null
            }
            optionsReady
        }
        composeRule.onNodeWithTag(FirefoxExtensionChromeTestTags.OptionsTopBar)
            .assertIsDisplayed()
        composeRule.onNodeWithText(FIXTURE_EXTENSION_NAME).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_more_options))
            .assertDoesNotExist()
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            val optionsUri = Uri.parse(controller.selectedTab.url)
            assertNotEquals(originalTabId, controller.selectedTabId)
            assertEquals(originalTabCount + 1, controller.tabs.size)
            assertFalse(controller.selectedTab.isIncognito)
            assertEquals("moz-extension", optionsUri.scheme)
            assertEquals(fixtureOriginHost, optionsUri.host)
            assertEquals("/options.html", optionsUri.path)
            controller.onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setInsets(
                        WindowInsetsCompat.Type.statusBars(),
                        Insets.of(LEFT_INSET_PX, TOP_INSET_PX, RIGHT_INSET_PX, 0),
                    )
                    .setInsets(
                        WindowInsetsCompat.Type.navigationBars(),
                        Insets.of(0, 0, 0, BOTTOM_INSET_PX),
                    )
                    .build(),
            )
            val engineView = (requireNotNull(controller.selectedGeckoViewForTesting()) as ViewGroup)
                .getChildAt(0)
            val margins = engineView.layoutParams as ViewGroup.MarginLayoutParams
            assertEquals(LEFT_INSET_PX, margins.leftMargin)
            assertEquals(0, margins.topMargin)
            assertEquals(RIGHT_INSET_PX, margins.rightMargin)
            assertEquals(BOTTOM_INSET_PX, margins.bottomMargin)
        }

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            var returnedToOpener = false
            composeRule.activityRule.scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                returnedToOpener = controller.selectedTabId == originalTabId &&
                    controller.tabs.none { tab -> tab.id == optionsTabId }
            }
            returnedToOpener
        }
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            assertEquals(originalTabId, controller.selectedTabId)
            assertEquals(originalTabCount, controller.tabs.size)
            assertEquals(ORIGINAL_PAGE_URL, controller.selectedTab.url)
        }
    }

    @Test
    fun externalNavigationFromExtensionOptionsRestoresNormalBrowserChrome() {
        ensureBuiltInExtensionFixture()
        openBrowserMenu()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.FirefoxExtensions)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(
                FirefoxExtensionManagerTestTags.optionsPage(FIXTURE_EXTENSION_ID),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(
            FirefoxExtensionManagerTestTags.optionsPage(FIXTURE_EXTENSION_ID),
        ).assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(FirefoxExtensionChromeTestTags.OptionsTopBar)
                .fetchSemanticsNodes().isNotEmpty()
        }

        var optionsTabId = ""
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            optionsTabId = controller.selectedTabId
            controller.submitAddress(EXTERNAL_PAGE_URL)
        }
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            var externalPageSelected = false
            composeRule.activityRule.scenario.onActivity { activity ->
                externalPageSelected =
                    activity.browserControllerForTesting().selectedTab.url == EXTERNAL_PAGE_URL
            }
            externalPageSelected
        }
        composeRule.onNodeWithTag(FirefoxExtensionChromeTestTags.OptionsTopBar)
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_more_options))
            .assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            activity.onBackPressedDispatcher.onBackPressed()
            assertEquals(optionsTabId, controller.selectedTabId)
            controller.closeTab(optionsTabId)
        }
    }

    @Test
    fun privateMainMenuDoesNotExposeFirefoxExtensions() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            controller.createTab(isIncognito = true)
            assertTrue(controller.selectedTab.isIncognito)
        }
        composeRule.waitForIdle()

        openBrowserMenu()

        composeRule.onNodeWithTag(BrowserMainMenuTestTags.FirefoxExtensions)
            .assertDoesNotExist()
    }

    private fun openSettings() {
        openBrowserMenu()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Settings)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_title)).assertIsDisplayed()
    }

    private fun openBrowserMenu() {
        val closeAddressDescription = context.getString(R.string.cd_close_address_input)
        if (
            composeRule.onAllNodesWithContentDescription(closeAddressDescription)
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            composeRule.onNodeWithContentDescription(closeAddressDescription).performClick()
        }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.cd_more_options),
        ).performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
    }

    private fun ensureBuiltInExtensionFixture(): String {
        val installed = CountDownLatch(1)
        val error = arrayOfNulls<Throwable>(1)
        var baseUrl: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val runtime = GeckoRuntimeOwner.getOrCreate(context) as GeckoViewRuntimeHandle
            runtime.ensureBuiltInExtensionFixture()
                .withHandler(Handler(Looper.getMainLooper()))
                .accept(
                    { extension ->
                        if (extension?.id == FIXTURE_EXTENSION_ID) {
                            baseUrl = extension.metaData.baseUrl
                            installed.countDown()
                        }
                    },
                    { failure ->
                        error[0] = failure
                        installed.countDown()
                    },
                )
        }
        assertTrue(
            "Firefox extension fixture failed: ${error[0]}",
            installed.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) && error[0] == null,
        )
        return requireNotNull(Uri.parse(requireNotNull(baseUrl)).host)
    }

    private fun clearPreferences() {
        listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            GestureOnboardingStore.PREFERENCES_NAME,
            ReleaseNotesStore.PREFERENCES_NAME,
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private companion object {
        const val FIXTURE_EXTENSION_ID = "candy-firefox-fixture@sk2andy.dev"
        const val FIXTURE_EXTENSION_NAME = "Candy Firefox Conformance Fixture"
        const val ORIGINAL_PAGE_URL = "https://example.invalid/original-page"
        const val EXTERNAL_PAGE_URL = "https://example.invalid/from-extension-options"
        const val LEFT_INSET_PX = 8
        const val TOP_INSET_PX = 96
        const val RIGHT_INSET_PX = 12
        const val BOTTOM_INSET_PX = 48
        const val EDGE_BACK_SWIPE_STEPS = 30
        const val TIMEOUT_MILLIS = 30_000L
    }
}
