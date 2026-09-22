package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.browser.PrivacySignalSettings
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacySignalSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signalsDefaultOnAndEmitIndependentOptOuts() {
        val selected = AtomicReference<PrivacySignalSettings>()
        composeRule.setContent {
            MaterialBrowserTheme {
                ProtectionAndDataSettingsPage(
                    blockerSettings = BlockerSettings(),
                    blockedCount = 0,
                    trustsUserCertificates = false,
                    onBlockerSettingsChanged = {},
                    onPrivacySignalSettingsChanged = selected::set,
                    onPrivacyXRay = {},
                    onPermissionRadar = {},
                    onFilterStudio = {},
                    onClearData = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.DoNotTrack)
            .performScrollTo()
            .performClick()
        assertEquals(
            PrivacySignalSettings(
                doNotTrackEnabled = false,
                globalPrivacyControlEnabled = true,
            ),
            selected.get(),
        )

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.GlobalPrivacyControl)
            .performScrollTo()
            .performClick()
        assertEquals(
            PrivacySignalSettings(
                doNotTrackEnabled = true,
                globalPrivacyControlEnabled = false,
            ),
            selected.get(),
        )
    }
}
