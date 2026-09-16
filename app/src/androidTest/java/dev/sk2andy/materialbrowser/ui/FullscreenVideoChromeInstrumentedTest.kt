package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.StartupAddressFocusMode
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionState
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullscreenVideoChromeInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    init {
        clearPreferences()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).apply {
            saveStartupAnimationEnabled(false)
            saveStartupAddressFocusMode(StartupAddressFocusMode.Never)
        }
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        composeRule.activityRule.scenario.close()
        clearPreferences()
    }

    @Test
    fun webContentFullscreenHidesAndRestoresBrowserChromeBeforeMediaMetadata() {
        openAttachedPage()
        composeRule.onNodeWithTag(AddressBarTestTags.TabButton).assertIsDisplayed()

        composeRule.runOnIdle {
            composeRule.activity.browserControllerForTesting().apply {
                reportSelectedBrowserEngineFullscreenStateForTesting(true)
            }
        }

        composeRule.onNodeWithTag(AddressBarTestTags.TabButton).assertDoesNotExist()

        composeRule.runOnIdle {
            composeRule.activity.browserControllerForTesting()
                .reportSelectedBrowserEngineFullscreenStateForTesting(false)
        }

        composeRule.onNodeWithTag(AddressBarTestTags.TabButton).assertIsDisplayed()
    }

    @Test
    fun minimizingFullscreenVideoRestoresBrowserChrome() {
        openAttachedPage()

        composeRule.runOnIdle {
            composeRule.activity.browserControllerForTesting().apply {
                reportSelectedBrowserEngineMediaStateForTesting(
                    GeckoMediaSessionState(
                        isActive = true,
                        isPlaying = true,
                        isFullscreen = true,
                        videoWidth = 1_920,
                        videoHeight = 1_080,
                        videoTrackCount = 1,
                    ),
                )
                reportSelectedBrowserEngineFullscreenStateForTesting(true)
            }
        }

        composeRule.onNodeWithTag(AddressBarTestTags.TabButton).assertDoesNotExist()

        composeRule.runOnIdle {
            composeRule.activity.browserControllerForTesting().minimizeFullscreenVideo()
        }

        composeRule.onNodeWithTag(AddressBarTestTags.TabButton).assertIsDisplayed()
    }

    private fun openAttachedPage() {
        composeRule.runOnIdle {
            assertTrue(
                composeRule.activity.browserControllerForTesting()
                    .openUrl("https://media.example/"),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.activity.browserControllerForTesting()
                .selectedBrowserEngineViewForTesting()
                ?.isAttachedToWindow == true
        }
    }

    private fun clearPreferences() {
        listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            GestureOnboardingStore.PREFERENCES_NAME,
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
