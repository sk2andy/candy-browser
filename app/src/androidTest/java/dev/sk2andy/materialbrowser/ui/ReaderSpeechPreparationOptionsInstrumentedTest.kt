package dev.sk2andy.materialbrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.reader.ReaderSpeechModelState
import dev.sk2andy.materialbrowser.reader.ReaderSpeechPreparationAvailability
import dev.sk2andy.materialbrowser.reader.ReaderSpeechPreparationState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSpeechPreparationOptionsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unsupportedAndUncheckedDevicesDoNotShowNanoOption() {
        val state = mutableStateOf(ReaderSpeechPreparationState())
        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderSpeechPreparationOptions(state.value, {}, {}, {})
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Options).assertDoesNotExist()
        composeRule.runOnIdle {
            state.value = state.value.copy(availability = ReaderSpeechPreparationAvailability.Unavailable)
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Options).assertDoesNotExist()
    }

    @Test
    fun availableModelIsOptInAndCanBeDisabled() {
        val state = mutableStateOf(ReaderSpeechPreparationState(
            adultConfirmed = true,
            availability = ReaderSpeechPreparationAvailability.Available,
        ))
        var downloads = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReaderSpeechPreparationOptions(
                        state = state.value,
                        onEnabledChanged = { state.value = state.value.copy(enabled = it) },
                        onDownload = { downloads++ },
                        onCancelDownload = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Enabled).assertIsOff().performClick()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Enabled).assertIsOn().performClick()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Enabled).assertIsOff()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Download).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, downloads) }
    }

    @Test
    fun downloadableModelRequiresExplicitDownloadBeforeEnabling() {
        var downloads = 0
        var toggles = 0
        var voiceChoices = 0
        val preparation = mutableStateOf(ReaderSpeechPreparationState(
            adultConfirmed = true,
            availability = ReaderSpeechPreparationAvailability.Downloadable,
        ))
        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderSpeechChoiceDialog(
                    state = ReaderSpeechModelState(loaded = true),
                    onOfflineVoice = { voiceChoices++ },
                    onSystemVoice = { voiceChoices++ },
                    onDismiss = {},
                    preparationState = preparation.value,
                    onPreparationEnabledChanged = { toggles++ },
                    onPreparationDownload = { downloads++ },
                )
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Enabled)
            .performScrollTo().assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, downloads) }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Download).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, downloads)
            assertEquals(0, toggles)
            preparation.value = preparation.value.copy(
                availability = ReaderSpeechPreparationAvailability.Available,
                enabled = true,
            )
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Enabled).performScrollTo().assertIsOn()
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.ChooseSystem).assertIsEnabled()
        composeRule.runOnIdle { assertEquals(0, voiceChoices) }
    }

    @Test
    fun downloadShowsProgressAndCanBeCancelled() {
        var cancellations = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReaderSpeechPreparationOptions(
                        state = ReaderSpeechPreparationState(
                            adultConfirmed = true,
                            availability = ReaderSpeechPreparationAvailability.Downloading,
                            busy = true,
                            downloadedBytes = 1_000_000,
                        ),
                        onEnabledChanged = {},
                        onDownload = {},
                        onCancelDownload = { cancellations++ },
                    )
                }
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Download).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Progress).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.CancelDownload).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, cancellations) }
    }

    @Test
    fun sharedSystemDownloadDoesNotOfferToCancelAnotherAppsRequest() {
        var refreshes = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReaderSpeechPreparationOptions(
                        state = ReaderSpeechPreparationState(
                            adultConfirmed = true,
                            availability = ReaderSpeechPreparationAvailability.Downloading,
                            busy = false,
                        ),
                        onEnabledChanged = {},
                        onDownload = {},
                        onCancelDownload = {},
                        onRefresh = { refreshes++ },
                    )
                }
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Progress).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.CancelDownload).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Download).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Refresh).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun failedDownloadOffersRetry() {
        var retries = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReaderSpeechPreparationOptions(
                        state = ReaderSpeechPreparationState(
                            adultConfirmed = true,
                            availability = ReaderSpeechPreparationAvailability.Downloadable,
                            error = true,
                        ),
                        onEnabledChanged = {},
                        onDownload = { retries++ },
                        onCancelDownload = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Error).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Download).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun enabledUnavailableModelCanStillBeDisabled() {
        var enabled: Boolean? = null
        composeRule.setContent {
            MaterialBrowserTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReaderSpeechPreparationOptions(
                        state = ReaderSpeechPreparationState(
                            adultConfirmed = true,
                            availability = ReaderSpeechPreparationAvailability.Unavailable,
                            enabled = true,
                        ),
                        onEnabledChanged = { enabled = it },
                        onDownload = {},
                        onCancelDownload = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Unavailable).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Enabled).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(false, enabled) }
    }

    @Test
    fun preparationStatusDisappearsWhenPreparationCompletes() {
        val preparing = mutableStateOf(true)
        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderSpeechPreparationPlaybackStatus(preparing = preparing.value, fallback = false)
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.PlaybackStatus).assertIsDisplayed()
        composeRule.runOnIdle { preparing.value = false }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.PlaybackStatus).assertDoesNotExist()
    }
    @Test
    fun firstEnableRequiresAdultConfirmationOnlyOnce() {
        val state = mutableStateOf(ReaderSpeechPreparationState(
            availability = ReaderSpeechPreparationAvailability.Available,
        ))
        var confirmations = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReaderSpeechPreparationOptions(
                        state = state.value,
                        onEnabledChanged = { state.value = state.value.copy(enabled = it) },
                        onDownload = {},
                        onCancelDownload = {},
                        onConfirmAdult = {
                            confirmations++
                            state.value = state.value.copy(adultConfirmed = true)
                        },
                    )
                }
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Enabled).performClick()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.AdultDialog).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(false, state.value.enabled) }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.CancelAdult).performClick()
        composeRule.runOnIdle {
            assertEquals(false, state.value.enabled)
            assertEquals(0, confirmations)
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Enabled).performClick()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.ConfirmAdult).performClick()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Enabled).assertIsOn().performClick()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Enabled).assertIsOff().performClick()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Enabled).assertIsOn()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.AdultDialog).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test
    fun firstDownloadWaitsForAdultConfirmationAndCancellingDoesNotDownload() {
        val state = mutableStateOf(ReaderSpeechPreparationState(
            availability = ReaderSpeechPreparationAvailability.Downloadable,
        ))
        var downloads = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReaderSpeechPreparationOptions(
                        state = state.value,
                        onEnabledChanged = {},
                        onDownload = { downloads++ },
                        onCancelDownload = {},
                        onConfirmAdult = { state.value = state.value.copy(adultConfirmed = true) },
                    )
                }
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Download).performScrollTo().performClick()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.CancelAdult).performClick()
        composeRule.runOnIdle {
            assertEquals(false, state.value.adultConfirmed)
            assertEquals(0, downloads)
        }
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.Download).performScrollTo().performClick()
        composeRule.onNodeWithTag(ReaderSpeechPreparationTestTags.ConfirmAdult).performClick()
        composeRule.runOnIdle {
            assertEquals(true, state.value.adultConfirmed)
            assertEquals(1, downloads)
        }
    }

}
