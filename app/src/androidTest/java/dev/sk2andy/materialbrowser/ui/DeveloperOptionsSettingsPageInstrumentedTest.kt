package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.BrowserChromeScrollDispatchMode
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.GeckoSafeAreaSettings
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeveloperOptionsSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aboutLongPressUnlocksDeveloperDestinationWithoutOpeningAbout() {
        var unlocked by mutableStateOf(false)
        var destination by mutableStateOf<SettingsDestination?>(null)
        composeRule.setContent {
            MaterialBrowserTheme {
                SettingsHomePage(
                    downloadSummary = "Candy",
                    onDestinationChanged = { destination = it },
                    onDismiss = {},
                    developerOptionsUnlocked = unlocked,
                    onUnlockDeveloperOptions = { unlocked = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.developer_options_title))
            .assertDoesNotExist()
        val aboutNode = composeRule
            .onNodeWithText(context.getString(R.string.settings_section_about_legal))
            .performScrollTo()
        val unlockLabel = context.getString(R.string.developer_options_unlock_action)
        aboutNode.assert(
            SemanticsMatcher("has labeled developer-options long click") { node ->
                runCatching { node.config[SemanticsActions.OnLongClick].label }.getOrNull() ==
                    unlockLabel
            },
        )
        aboutNode
            .performTouchInput { longClick() }

        assertTrue(unlocked)
        assertEquals(null, destination)
        composeRule.onNodeWithText(context.getString(R.string.developer_options_title))
            .assertIsDisplayed()
            .performClick()
        assertEquals(SettingsDestination.DeveloperOptions, destination)
    }

    @Test
    fun safeAreaControlsUpdateAndResetSettings() {
        var settings by mutableStateOf(DeveloperSettings())
        composeRule.setContent {
            MaterialBrowserTheme {
                DeveloperOptionsSettingsPage(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DeveloperOptionsTestTags.LayoutQuietPeriod)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(250f)
            }
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.RequiredFailures)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(4f)
            }
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.ForceSafeAreaFallback)
            .performScrollTo()
            .performClick()

        assertEquals(250, settings.safeAreaLayoutQuietPeriodMillis)
        assertEquals(4, settings.safeAreaRequiredFailureCount)
        assertTrue(settings.forceSafeAreaFallback)
        assertFalse(settings == DeveloperSettings())
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.Reset)
            .performScrollTo()
            .performClick()
        assertEquals(DeveloperSettings(), settings)
    }

    @Test
    fun scrollDispatchModeUpdatesAndSafeAreaResetPreservesIt() {
        var settings by mutableStateOf(DeveloperSettings())
        composeRule.setContent {
            MaterialBrowserTheme {
                DeveloperOptionsSettingsPage(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DeveloperOptionsTestTags.BrowserChromeScrollDispatchMode)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.developer_options_scroll_dispatch_120_hz),
        ).performClick()

        assertEquals(
            BrowserChromeScrollDispatchMode.Fixed120Hz,
            settings.browserChromeScrollDispatchMode,
        )

        composeRule.onNodeWithTag(DeveloperOptionsTestTags.LayoutQuietPeriod)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(250f)
            }
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.Reset)
            .performScrollTo()
            .performClick()

        assertEquals(
            BrowserChromeScrollDispatchMode.Fixed120Hz,
            settings.browserChromeScrollDispatchMode,
        )
        assertEquals(
            DeveloperSettings.DEFAULT_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS,
            settings.safeAreaLayoutQuietPeriodMillis,
        )
    }

    @Test
    fun httpPasswordAutofillRequiresExplicitConfirmation() {
        var enabled by mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                DeveloperOptionsSettingsPage(
                    settings = DeveloperSettings(),
                    isHttpPasswordAutofillEnabled = enabled,
                    isHttpPasswordAutofillSupported = true,
                    onSettingsChanged = {},
                    onHttpPasswordAutofillEnabledChanged = { enabled = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DeveloperOptionsTestTags.HttpPasswordAutofill)
            .assertIsEnabled()
            .performClick()

        assertFalse(enabled)
        composeRule.onNodeWithText(
            context.getString(R.string.developer_options_http_warning_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.developer_options_http_warning_confirm),
        ).performClick()

        assertTrue(enabled)
    }

    @Test
    fun geckoMutationSwitchesUpdateIndependentlyFromNativeFallback() {
        val original = DeveloperSettings(
            browserChromeScrollDispatchMode = BrowserChromeScrollDispatchMode.Fixed30Hz,
            safeAreaLayoutQuietPeriodMillis = 250,
            safeAreaRequiredFailureCount = 4,
            forceSafeAreaFallback = true,
        )
        var settings by mutableStateOf(original)
        composeRule.setContent {
            MaterialBrowserTheme {
                DeveloperOptionsSettingsPage(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }

        listOf(
            DeveloperOptionsTestTags.GeckoRecheckAddedElements,
            DeveloperOptionsTestTags.GeckoRecheckChangedElements,
            DeveloperOptionsTestTags.GeckoRequireInteraction,
            DeveloperOptionsTestTags.GeckoRecheckOnResize,
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        }

        assertEquals(
            original.copy(
                geckoSafeAreaSettings = GeckoSafeAreaSettings(
                    recheckAddedElements = false,
                    recheckChangedElements = false,
                    requireInteractionForUpdates = false,
                    recheckOnResize = false,
                ),
            ),
            settings,
        )
    }

    @Test
    fun geckoBudgetsUpdateAndResetWithoutChangingOtherDeveloperSettings() {
        val original = DeveloperSettings(
            browserChromeScrollDispatchMode = BrowserChromeScrollDispatchMode.Fixed15Hz,
            safeAreaLayoutQuietPeriodMillis = 250,
            safeAreaRequiredFailureCount = 4,
            forceSafeAreaFallback = true,
        )
        var settings by mutableStateOf(original)
        composeRule.setContent {
            MaterialBrowserTheme {
                DeveloperOptionsSettingsPage(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }

        listOf(
            DeveloperOptionsTestTags.GeckoInteractionWindow to 1_600f,
            DeveloperOptionsTestTags.GeckoMutationDebounce to 250f,
            DeveloperOptionsTestTags.GeckoMaxElementsPerBatch to 32f,
            DeveloperOptionsTestTags.GeckoMaxBatchDuration to 6f,
            DeveloperOptionsTestTags.GeckoMaxInitialElements to 1_024f,
        ).forEach { (tag, value) ->
            composeRule.onNodeWithTag(tag)
                .performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                    setProgress(value)
                }
        }

        assertEquals(
            original.copy(
                geckoSafeAreaSettings = GeckoSafeAreaSettings(
                    interactionWindowMillis = 1_600,
                    mutationDebounceMillis = 250,
                    maxElementsPerBatch = 32,
                    maxBatchDurationMillis = 6,
                    maxInitialElements = 1_024,
                ),
            ),
            settings,
        )
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.GeckoReset)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals(original, settings)
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.GeckoReset).assertIsNotEnabled()
    }

    @Test
    fun disablingGeckoCorrectionDisablesOnlyItsChildControlsAndResetReenablesIt() {
        val original = DeveloperSettings(forceSafeAreaFallback = true)
        var settings by mutableStateOf(original)
        composeRule.setContent {
            MaterialBrowserTheme {
                DeveloperOptionsSettingsPage(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DeveloperOptionsTestTags.GeckoSafeAreaEnabled)
            .performScrollTo()
            .performClick()

        assertEquals(
            original.copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)),
            settings,
        )
        listOf(
            DeveloperOptionsTestTags.GeckoRecheckAddedElements,
            DeveloperOptionsTestTags.GeckoRecheckChangedElements,
            DeveloperOptionsTestTags.GeckoRequireInteraction,
            DeveloperOptionsTestTags.GeckoRecheckOnResize,
            DeveloperOptionsTestTags.GeckoInteractionWindow,
            DeveloperOptionsTestTags.GeckoMutationDebounce,
            DeveloperOptionsTestTags.GeckoMaxElementsPerBatch,
            DeveloperOptionsTestTags.GeckoMaxBatchDuration,
            DeveloperOptionsTestTags.GeckoMaxInitialElements,
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled()
        }
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.LayoutQuietPeriod)
            .performScrollTo()
            .assertIsEnabled()
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.ForceSafeAreaFallback)
            .performScrollTo()
            .assertIsEnabled()
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.GeckoSafeAreaEnabled)
            .performScrollTo()
            .assertIsEnabled()
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.GeckoReset)
            .performScrollTo()
            .performClick()

        assertEquals(original, settings)
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.GeckoRecheckAddedElements)
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun nativeFallbackResetPreservesGeckoCorrectionSettings() {
        val geckoSettings = GeckoSafeAreaSettings(enabled = false, maxElementsPerBatch = 32)
        var settings by mutableStateOf(
            DeveloperSettings(
                safeAreaLayoutQuietPeriodMillis = 250,
                forceSafeAreaFallback = true,
                geckoSafeAreaSettings = geckoSettings,
            ),
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                DeveloperOptionsSettingsPage(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DeveloperOptionsTestTags.Reset)
            .performScrollTo()
            .performClick()

        assertEquals(DeveloperSettings(geckoSafeAreaSettings = geckoSettings), settings)
    }

    @Test
    fun unsupportedHttpPasswordAutofillStaysDisabled() {
        composeRule.setContent {
            MaterialBrowserTheme {
                DeveloperOptionsSettingsPage(
                    settings = DeveloperSettings(),
                    isHttpPasswordAutofillEnabled = true,
                    isHttpPasswordAutofillSupported = false,
                    onSettingsChanged = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DeveloperOptionsTestTags.HttpPasswordAutofill)
            .assertIsNotEnabled()
    }

    @Test
    fun diagnosticsAndPresentationActionsEmitCallbacks() {
        var inputDiagnosticsEnabled by mutableStateOf(false)
        var diagnosticsCopied = false
        var onboardingShown = false
        var releaseNotesShown = false
        composeRule.setContent {
            MaterialBrowserTheme {
                DeveloperOptionsSettingsPage(
                    settings = DeveloperSettings(),
                    isInputDiagnosticsEnabled = inputDiagnosticsEnabled,
                    onSettingsChanged = {},
                    onInputDiagnosticsEnabledChanged = {
                        inputDiagnosticsEnabled = it
                    },
                    onCopyDiagnostics = { diagnosticsCopied = true },
                    onShowOnboarding = { onboardingShown = true },
                    onShowReleaseNotes = { releaseNotesShown = true },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DeveloperOptionsTestTags.InputDiagnostics)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.CopyDiagnostics)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.ShowOnboarding)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.ShowReleaseNotes)
            .performScrollTo()
            .performClick()

        assertTrue(inputDiagnosticsEnabled)
        assertTrue(diagnosticsCopied)
        assertTrue(onboardingShown)
        assertTrue(releaseNotesShown)
    }
}
