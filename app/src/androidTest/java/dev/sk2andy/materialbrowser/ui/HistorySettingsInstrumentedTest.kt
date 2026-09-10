package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistorySettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disablingHistoryAlsoDisablesClearOnExitInProtectionSettings() {
        composeRule.setContent {
            var mode by remember { mutableStateOf(HistoryRecordingMode.ClearOnExit) }
            MaterialBrowserTheme {
                ProtectionAndDataSettingsPage(
                    blockerSettings = BlockerSettings(),
                    blockedCount = 0,
                    historyRecordingMode = mode,
                    trustsUserCertificates = false,
                    onBlockerSettingsChanged = {},
                    onHistoryRecordingModeChanged = { mode = it },
                    onPrivacyXRay = {},
                    onPermissionRadar = {},
                    onFilterStudio = {},
                    onClearData = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.ClearHistoryOnExit)
            .performScrollTo()
        composeRule.onNode(
            hasParent(hasTestTag(ProtectionSettingsTestTags.ClearHistoryOnExit)) and isToggleable(),
        ).assertIsOn()
        composeRule.onNodeWithTag(ProtectionSettingsTestTags.SaveHistory)
            .performScrollTo()
            .performClick()
        composeRule.onNode(
            hasParent(hasTestTag(ProtectionSettingsTestTags.SaveHistory)) and isToggleable(),
        ).assertIsOff()
        composeRule.onNode(
            hasParent(hasTestTag(ProtectionSettingsTestTags.ClearHistoryOnExit)) and isToggleable(),
        )
            .assertIsOff()
            .assertIsNotEnabled()
    }
}
