package dev.sk2andy.materialbrowser.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerSeekSettings
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun inlineMediaPlayerChoiceUpdatesModeAndExplainsDirectPictureInPicture() {
        var mode by mutableStateOf(InlineMediaPlayerMode.ButtonFullscreen)
        composeRule.setContent {
            MaterialBrowserTheme {
                PlayerSettingsPage(
                    isVideoAutoplayBlocked = false,
                    isVideoAutoplayBlockingSupported = true,
                    inlineMediaPlayerMode = mode,
                    isInlineMediaPlayerSupported = true,
                    onVideoAutoplayBlockedChanged = {},
                    onInlineMediaPlayerModeChanged = { mode = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayer)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(
            context.getString(
                R.string.settings_inline_media_player_mode_button_inline_fullscreen,
            ),
        ).performClick()

        assertEquals(InlineMediaPlayerMode.ButtonInlineAndFullscreen, mode)
        val evidencePauseMillis = InstrumentationRegistry.getArguments()
            .getString("evidencePauseMillis")?.toLongOrNull()?.coerceIn(0L, 5_000L) ?: 0L
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayer).performClick()
        composeRule.waitForIdle()
        if (evidencePauseMillis > 0) SystemClock.sleep(evidencePauseMillis)
        composeRule.onNodeWithText(
            context.getString(R.string.settings_inline_media_player_mode_disabled),
        ).performClick()
        assertEquals(InlineMediaPlayerMode.Disabled, mode)
        composeRule.waitForIdle()
        if (evidencePauseMillis > 0) SystemClock.sleep(evidencePauseMillis)
        composeRule.onNodeWithText(
            context.getString(R.string.settings_inline_media_player_subtitle),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun inlineMediaPlayerSeekSlidersUpdateDirectionsSeparatelyAndDisableWithOff() {
        var settings by mutableStateOf(InlineMediaPlayerSeekSettings())
        var mode by mutableStateOf(InlineMediaPlayerMode.Automatic)
        var supported by mutableStateOf(true)
        val changes = mutableListOf<InlineMediaPlayerSeekSettings>()
        composeRule.setContent {
            MaterialBrowserTheme {
                PlayerSettingsPage(
                    isVideoAutoplayBlocked = false,
                    isVideoAutoplayBlockingSupported = true,
                    inlineMediaPlayerMode = mode,
                    inlineMediaPlayerSeekSettings = settings,
                    isInlineMediaPlayerSupported = supported,
                    onVideoAutoplayBlockedChanged = {},
                    onInlineMediaPlayerSeekSettingsChanged = {
                        settings = it
                        changes.add(it)
                    },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayerSeekBackward)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        assertEquals(InlineMediaPlayerSeekSettings(15, 10), settings)
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayerSeekForward)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        assertEquals(listOf(InlineMediaPlayerSeekSettings(15, 10), InlineMediaPlayerSeekSettings(15, 30)), changes)
        composeRule.runOnIdle { mode = InlineMediaPlayerMode.Disabled }
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayerSeekBackward).assertIsNotEnabled()
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayerSeekForward).assertIsNotEnabled()
        assertEquals(InlineMediaPlayerSeekSettings(15, 30), settings)
        composeRule.runOnIdle {
            mode = InlineMediaPlayerMode.Automatic
            supported = false
        }
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayerSeekBackward).assertIsNotEnabled()
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayerSeekForward).assertIsNotEnabled()
    }

    @Test
    fun autoplayRemainsIndependentOfDisabledCandyPlayerAndHonorsSupport() {
        var blocked by mutableStateOf(false)
        var supported by mutableStateOf(true)
        composeRule.setContent {
            MaterialBrowserTheme {
                PlayerSettingsPage(
                    isVideoAutoplayBlocked = blocked,
                    isVideoAutoplayBlockingSupported = supported,
                    inlineMediaPlayerMode = InlineMediaPlayerMode.Disabled,
                    onVideoAutoplayBlockedChanged = { blocked = it },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(PlayerSettingsTestTags.VideoAutoplay).performClick()
        assertTrue(blocked)
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayerSeekBackward)
            .assertIsNotEnabled()
        composeRule.runOnIdle { supported = false }
        composeRule.onNodeWithTag(PlayerSettingsTestTags.VideoAutoplay).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.settings_video_autoplay_unsupported))
            .assertIsDisplayed()
    }
}
