package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertHasClickAction
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
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebRtcProtectionSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun systemWebViewExplainsBlockingAndEmitsStrictMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selectedMode = AtomicReference<WebRtcProtectionMode>()
        composeRule.setContent {
            MaterialBrowserTheme {
                ProtectionAndDataSettingsPage(
                    blockerSettings = BlockerSettings(),
                    blockedCount = 0,
                    browserEngineKind = AndroidBrowserEngineKind.SystemWebView,
                    webRtcProtectionMode = WebRtcProtectionMode.ProtectIpAddresses,
                    trustsUserCertificates = false,
                    onBlockerSettingsChanged = {},
                    onWebRtcProtectionModeChanged = selectedMode::set,
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
        composeRule.onNodeWithText(context.getString(R.string.settings_webrtc_mode_block))
            .performClick()

        assertEquals(WebRtcProtectionMode.Block, selectedMode.get())
        composeRule.onNodeWithText(
            context.getString(R.string.settings_webrtc_protect_system_summary),
        ).assertExists()
    }
}
