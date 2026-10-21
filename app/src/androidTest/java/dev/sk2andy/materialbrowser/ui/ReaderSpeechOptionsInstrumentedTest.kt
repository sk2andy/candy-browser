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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.reader.ReaderSpeechModelState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSpeechOptionsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun firstChoiceDoesNotDownloadBeforeAnExplicitAction() {
        var offline = 0
        var system = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderSpeechChoiceDialog(
                    state = ReaderSpeechModelState(loaded = true),
                    onOfflineVoice = { offline++ },
                    onSystemVoice = { system++ },
                    onDismiss = {},
                )
            }
        }
        composeRule.runOnIdle {
            assertEquals(0, offline)
            assertEquals(0, system)
        }
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.ChooseSystem).performClick()
        composeRule.runOnIdle {
            assertEquals(0, offline)
            assertEquals(1, system)
        }
    }

    @Test
    fun downloadingDisablesDuplicateDownloadsAndFailureOffersRetry() {
        val state = mutableStateOf(ReaderSpeechModelState(loaded = true, busy = true, progress = 0.5f))
        var downloads = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderSpeechChoiceDialog(
                    state = state.value,
                    onOfflineVoice = { downloads++ },
                    onSystemVoice = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.Progress).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.ChooseOffline).assertIsNotEnabled()
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.ChooseSystem).assertIsEnabled()
        composeRule.runOnIdle { state.value = state.value.copy(busy = false, progress = null, error = true) }
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.Error).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.ChooseOffline).performClick()
        composeRule.runOnIdle { assertEquals(1, downloads) }
    }

    @Test
    fun playbackFailureOffersVoiceSwitchInsteadOfAnUnavailableButton() {
        var system = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderSpeechChoiceDialog(
                    state = ReaderSpeechModelState(loaded = true, ready = true, enabled = true),
                    playbackFailed = true,
                    onOfflineVoice = {},
                    onSystemVoice = { system++ },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.PlaybackError).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.ChooseSystem).performClick()
        composeRule.runOnIdle { assertEquals(1, system) }
    }

    @Test
    fun downloadedModelCanBeDisabledWithoutDeletingIt() {
        val state = mutableStateOf(ReaderSpeechModelState(loaded = true, ready = true, enabled = true))
        var deletes = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReaderSpeechSettingsContent(
                        state = state.value,
                        onEnabledChanged = { state.value = state.value.copy(enabled = it) },
                        onDownload = {},
                        onDelete = { deletes++ },
                    )
                }
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.Download).assertIsNotEnabled()
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.Enabled).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(false, state.value.enabled)
            assertEquals(true, state.value.ready)
            assertEquals(0, deletes)
        }
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.Delete).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, deletes) }
    }

    @Test
    fun failedModelCanBeDisabledAndRemovedFromSettings() {
        var enabled: Boolean? = null
        var deletes = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReaderSpeechSettingsContent(
                        state = ReaderSpeechModelState(loaded = true, enabled = true, error = true),
                        onEnabledChanged = { enabled = it },
                        onDownload = {},
                        onDelete = { deletes++ },
                    )
                }
            }
        }
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.Download).assertIsEnabled()
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.Enabled).performScrollTo().performClick()
        composeRule.onNodeWithTag(ReaderSpeechOptionsTestTags.Delete).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(false, enabled)
            assertEquals(1, deletes)
        }
    }
}
