package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.HttpsOnlyMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HttpsOnlySettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun geckoDefaultsToAllTabsAndShowsWarningExplanation() {
        setContent()

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.HttpsOnly).performScrollTo()
        composeRule.onNodeWithText(context.getString(R.string.settings_https_only_mode_all))
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.settings_https_only_all_summary))
            .assertExists()
    }

    @Test
    fun modeChoicesUpdateSelectionAndExplainEachScope() {
        val selected = AtomicReference<HttpsOnlyMode>()
        setContent(onModeChanged = selected::set)

        chooseMode(R.string.settings_https_only_mode_private)
        assertEquals(HttpsOnlyMode.PrivateOnly, selected.get())
        composeRule.onNodeWithText(context.getString(R.string.settings_https_only_private_summary))
            .assertExists()

        chooseMode(R.string.settings_https_only_mode_off)
        assertEquals(HttpsOnlyMode.Off, selected.get())
        composeRule.onNodeWithText(context.getString(R.string.settings_https_only_off_summary))
            .assertExists()

        chooseMode(R.string.settings_https_only_mode_all)
        assertEquals(HttpsOnlyMode.AllTabs, selected.get())
    }

    @Test
    fun systemWebViewShowsDisabledSettingAndKeepsSavedGeckoChoiceHidden() {
        setContent(
            browserEngineKind = AndroidBrowserEngineKind.SystemWebView,
            initialMode = HttpsOnlyMode.PrivateOnly,
        )

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.HttpsOnly)
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNode(
            hasTestTag(ProtectionSettingsTestTags.HttpsOnly) and
                hasText(context.getString(R.string.settings_https_only_unavailable)),
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_https_only_system_webview_summary),
        ).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.settings_https_only_mode_private))
            .assertDoesNotExist()
    }

    private fun chooseMode(labelResource: Int) {
        composeRule.onNodeWithTag(ProtectionSettingsTestTags.HttpsOnly)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(labelResource)).performClick()
    }

    private fun setContent(
        browserEngineKind: AndroidBrowserEngineKind = AndroidBrowserEngineKind.GeckoView,
        initialMode: HttpsOnlyMode = HttpsOnlyMode.Default,
        onModeChanged: (HttpsOnlyMode) -> Unit = {},
    ) {
        composeRule.setContent {
            var mode by remember { mutableStateOf(initialMode) }
            MaterialBrowserTheme {
                ProtectionAndDataSettingsPage(
                    blockerSettings = BlockerSettings(),
                    blockedCount = 0,
                    browserEngineKind = browserEngineKind,
                    httpsOnlyMode = mode,
                    trustsUserCertificates = false,
                    onBlockerSettingsChanged = {},
                    onHttpsOnlyModeChanged = { selection ->
                        mode = selection
                        onModeChanged(selection)
                    },
                    onPrivacyXRay = {},
                    onPermissionRadar = {},
                    onFilterStudio = {},
                    onClearData = {},
                    onBack = {},
                )
            }
        }
    }
}
