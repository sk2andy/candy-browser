package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.sk2andy.materialbrowser.browser.cast.CastUiState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CastControlsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disconnectedSessionHidesMiniController() {
        composeRule.setContent {
            MaterialBrowserTheme {
                CastControls(
                    state = CastUiState(),
                    onTogglePlayback = {},
                    onSeek = {},
                    onVolumeChange = {},
                    onDisconnect = {},
                )
            }
        }

        composeRule.onNodeWithTag(CastControlsTestTags.MiniController).assertDoesNotExist()
    }

    @Test
    fun connectedMediaShowsControlsAndDispatchesActions() {
        var playPauseCount = 0
        var disconnectCount = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                CastControls(
                    state = CastUiState(
                        isConnected = true,
                        hasMedia = true,
                        isPlaying = true,
                        title = "Video",
                        deviceName = "Living room",
                        positionMillis = 5_000L,
                        durationMillis = 20_000L,
                    ),
                    onTogglePlayback = { playPauseCount++ },
                    onSeek = {},
                    onVolumeChange = {},
                    onDisconnect = { disconnectCount++ },
                )
            }
        }

        composeRule.onNodeWithTag(CastControlsTestTags.MiniController).assertIsDisplayed()
        composeRule.onNodeWithTag(CastControlsTestTags.PlayPause).performClick()
        composeRule.onNodeWithTag(CastControlsTestTags.Disconnect).performClick()
        composeRule.onNodeWithTag(CastControlsTestTags.MiniController).performClick()
        composeRule.onNodeWithTag(CastControlsTestTags.ExpandedController).assertIsDisplayed()
        composeRule.onNodeWithTag(CastControlsTestTags.Seek).assertIsDisplayed()
        composeRule.onNodeWithTag(CastControlsTestTags.Volume).assertIsDisplayed()
        assertEquals(1, playPauseCount)
        assertEquals(1, disconnectCount)
    }
}
