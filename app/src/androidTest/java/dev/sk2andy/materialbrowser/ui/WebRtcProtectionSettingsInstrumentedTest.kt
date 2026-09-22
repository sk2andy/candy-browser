package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebRtcProtectionSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun systemWebViewShowsExplicitPoliciesAndFailsClosedForUnsupportedChoice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selectedMode = mutableStateOf(WebRtcProtectionMode.ProtectIpAddresses)
        composeRule.setContent {
            MaterialBrowserTheme {
                ProtectionAndDataSettingsPage(
                    blockerSettings = BlockerSettings(),
                    blockedCount = 0,
                    browserEngineKind = AndroidBrowserEngineKind.SystemWebView,
                    webRtcProtectionMode = selectedMode.value,
                    trustsUserCertificates = false,
                    onBlockerSettingsChanged = {},
                    onWebRtcProtectionModeChanged = { mode -> selectedMode.value = mode },
                    onPrivacyXRay = {},
                    onPermissionRadar = {},
                    onFilterStudio = {},
                    onClearData = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.WebRtcProtection)
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_webrtc_mode_hide_local_network_ip),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_webrtc_mode_block),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_webrtc_mode_disable_non_proxied_udp),
        )
            .performClick()

        assertEquals(WebRtcProtectionMode.DisableNonProxiedUdp, selectedMode.value)
        composeRule.onNodeWithText(
            context.getString(R.string.settings_webrtc_policy_system_summary),
        ).assertExists()
    }
}
