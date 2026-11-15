package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.sync.MozillaSyncDevice
import dev.sk2andy.materialbrowser.sync.MozillaSyncState
import dev.sk2andy.materialbrowser.sync.MozillaSyncStatus
import dev.sk2andy.materialbrowser.sync.MozillaSyncTab
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDefinition
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState
import dev.sk2andy.materialbrowser.sync.SyncStatus
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MozillaSyncUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unavailableMozillaStillAllowsSelectingCandySync() {
        composeRule.setContent {
            MaterialBrowserTheme {
                SyncSettingsPage(
                    state = SyncRepositoryState(null, SyncStatus.Unconfigured, emptyList(), 0, null, null),
                    iconCatalog = SyncDeviceIconCatalog(listOf(SyncDeviceIconDefinition("phone", "📱", "Phone"))),
                    onConfigure = { true },
                    onEnroll = { _, _, _ -> },
                    onRefresh = {},
                    mozillaState = MozillaSyncState(status = MozillaSyncStatus.Unavailable),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("sync_provider_1").performClick()
        composeRule.onNodeWithTag("mozilla_sync_status").assertExists()
        composeRule.onNodeWithTag("mozilla_sync_connect").assertDoesNotExist()
        composeRule.onNodeWithTag("sync_provider_0").performClick()
        composeRule.onNodeWithTag(SyncSettingsTestTags.Endpoint).assertExists()
    }

    @Test
    fun mozillaLoginUsesChosenLocalProfile() {
        var boundProfile: String? = null
        composeRule.setContent {
            MaterialBrowserTheme {
                MozillaSyncSettings(
                    state = MozillaSyncState(status = MozillaSyncStatus.SignedOut),
                    localProfiles = listOf(BrowserProfile("personal", "🏠"), BrowserProfile("work", "💼")),
                    activeProfileId = "personal",
                    onConnect = { profileId, _ -> boundProfile = profileId },
                    onRefresh = {},
                    onDisconnect = {},
                )
            }
        }
        composeRule.onNodeWithTag("mozilla_sync_profile").performClick()
        composeRule.onNodeWithText("💼").performClick()
        composeRule.onNodeWithTag("mozilla_sync_connect").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals("work", boundProfile) }
    }

    @Test
    fun remoteDeviceOpensCopyAndDoesNotAllowSendingPrivateTab() {
        val remoteTab = MozillaSyncTab("Desktop article", "https://example.org/article", 1_700_000_000_000)
        var opened: MozillaSyncTab? = null
        composeRule.setContent {
            MaterialBrowserTheme {
                MozillaDeviceSheet(
                    device = MozillaSyncDevice("desktop", "Firefox desktop", listOf(remoteTab), canReceiveTabs = true),
                    canSendCurrentTab = false,
                    onOpen = { opened = it },
                    onSend = { error("Private tabs must not be sent") },
                    onRefresh = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("mozilla_device_send_tab").assertIsNotEnabled()
        composeRule.onNodeWithTag("mozilla_device_tab_0").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(remoteTab, opened) }
    }
}
