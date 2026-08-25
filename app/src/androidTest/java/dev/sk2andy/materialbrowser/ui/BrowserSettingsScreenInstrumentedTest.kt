package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scrollBarSwitchUpdatesSetting() {
        var enabled by mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserSettingsPage(
                    isFullImmersiveModeEnabled = false,
                    isScrollBarEnabled = enabled,
                    isVideoAutoplayBlocked = false,
                    isVideoAutoplayBlockingSupported = true,
                    isDefaultBrowser = false,
                    onFullImmersiveModeEnabledChanged = {},
                    onScrollBarEnabledChanged = { enabled = it },
                    onVideoAutoplayBlockedChanged = {},
                    onOpenDefaultBrowserSettings = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(BrowserSettingsTestTags.ScrollBar).performClick()

        assertTrue(enabled)
    }
}
