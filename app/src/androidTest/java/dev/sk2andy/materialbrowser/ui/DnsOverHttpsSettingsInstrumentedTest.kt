package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsProvider
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsSettings
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DnsOverHttpsSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun geckoProviderChoiceEmitsSelectedStrictResolver() {
        val selected = AtomicReference<DnsOverHttpsSettings>()
        setContent(onSettingsChanged = selected::set)

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.DnsOverHttps)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_dns_over_https_provider_cloudflare),
        ).performClick()

        assertEquals(
            DnsOverHttpsSettings(DnsOverHttpsProvider.Cloudflare),
            selected.get(),
        )
    }

    @Test
    fun customProviderRequiresValidHttpsEndpointBeforeSaving() {
        val selected = AtomicReference<DnsOverHttpsSettings>()
        setContent(onSettingsChanged = selected::set)

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.DnsOverHttps)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_dns_over_https_provider_custom),
        ).performClick()

        composeRule.onNodeWithText(context.getString(R.string.action_save))
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(ProtectionSettingsTestTags.CustomDnsEndpoint)
            .performTextReplacement("http://dns.example/dns-query")
        composeRule.onNodeWithText(context.getString(R.string.settings_dns_over_https_custom_invalid))
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.action_save))
            .assertIsNotEnabled()
        assertNull(selected.get())

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.CustomDnsEndpoint)
            .performTextReplacement(" HTTPS://DNS.NextDNS.IO/abc123 ")
        composeRule.onNodeWithText(context.getString(R.string.action_save))
            .assertIsEnabled()
            .performClick()

        assertEquals(
            DnsOverHttpsSettings(
                provider = DnsOverHttpsProvider.Custom,
                customEndpoint = "https://dns.nextdns.io/abc123",
            ),
            selected.get(),
        )
    }

    @Test
    fun systemWebViewShowsDisabledChoiceAndEngineExplanation() {
        setContent(
            browserEngineKind = AndroidBrowserEngineKind.SystemWebView,
            dnsOverHttpsSettings = DnsOverHttpsSettings(DnsOverHttpsProvider.Cloudflare),
        )

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.DnsOverHttps)
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_dns_over_https_system_webview_summary),
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_dns_over_https_unavailable),
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_dns_over_https_provider_cloudflare),
        ).assertDoesNotExist()
    }

    @Test
    fun geckoSystemChoiceExplainsAndroidDnsInsteadOfStrictDoh() {
        setContent()

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.DnsOverHttps)
            .performScrollTo()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_dns_over_https_system_summary),
        ).assertExists()
    }

    private fun setContent(
        browserEngineKind: AndroidBrowserEngineKind = AndroidBrowserEngineKind.GeckoView,
        dnsOverHttpsSettings: DnsOverHttpsSettings = DnsOverHttpsSettings(),
        onSettingsChanged: (DnsOverHttpsSettings) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialBrowserTheme {
                ProtectionAndDataSettingsPage(
                    blockerSettings = BlockerSettings(),
                    blockedCount = 0,
                    browserEngineKind = browserEngineKind,
                    dnsOverHttpsSettings = dnsOverHttpsSettings,
                    trustsUserCertificates = false,
                    onBlockerSettingsChanged = {},
                    onDnsOverHttpsSettingsChanged = onSettingsChanged,
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
