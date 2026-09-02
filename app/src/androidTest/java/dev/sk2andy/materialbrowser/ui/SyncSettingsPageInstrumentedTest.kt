package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDefinition
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState
import dev.sk2andy.materialbrowser.sync.SyncStatus
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun setupRequiresPassphraseConfirmationBeforeConfiguration() {
        val configureCalls = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                SyncSettingsPage(
                    state = unconfiguredState(),
                    iconCatalog = catalog(),
                    onConfigure = {
                        configureCalls.incrementAndGet()
                        true
                    },
                    onEnroll = { _, _, _ -> error("Enrollment must not start") },
                    onRefresh = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SyncSettingsTestTags.Endpoint)
            .performTextInput("https://sync.example")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Username).performTextInput("candy")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Password).performTextInput("server-secret")
        composeRule.onNodeWithTag(SyncSettingsTestTags.DeviceName).performTextInput("Phone")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Passphrase)
            .performScrollTo()
            .performTextInput("first-passphrase")
        composeRule.onNodeWithTag(SyncSettingsTestTags.PassphraseConfirmation)
            .performScrollTo()
            .performTextInput("different-passphrase")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Enroll)
            .performScrollTo()
            .performClick()

        assertEquals(0, configureCalls.get())
    }

    @Test
    fun setupRejectsShortPassphraseBeforeConfiguration() {
        val configureCalls = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                SyncSettingsPage(
                    state = unconfiguredState(),
                    iconCatalog = catalog(),
                    onConfigure = {
                        configureCalls.incrementAndGet()
                        true
                    },
                    onEnroll = { _, _, _ -> error("Enrollment must not start") },
                    onRefresh = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SyncSettingsTestTags.Endpoint)
            .performTextInput("https://sync.example")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Username).performTextInput("candy")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Password).performTextInput("server-secret")
        composeRule.onNodeWithTag(SyncSettingsTestTags.DeviceName).performTextInput("Phone")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Passphrase)
            .performScrollTo()
            .performTextInput("too-short")
        composeRule.onNodeWithTag(SyncSettingsTestTags.PassphraseConfirmation)
            .performScrollTo()
            .performTextInput("too-short")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Enroll)
            .performScrollTo()
            .performClick()

        assertEquals(0, configureCalls.get())
    }

    @Test
    fun exposesIconChoiceAndSeparateSecretInputs() {
        composeRule.setContent {
            MaterialBrowserTheme {
                SyncSettingsPage(
                    state = unconfiguredState(),
                    iconCatalog = catalog(),
                    onConfigure = { true },
                    onEnroll = { _, _, _ -> },
                    onRefresh = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SyncSettingsTestTags.Icon).assertExists()
        composeRule.onNodeWithTag(SyncSettingsTestTags.Password).assertExists()
        composeRule.onNodeWithTag(SyncSettingsTestTags.Passphrase)
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithTag(SyncSettingsTestTags.PassphraseConfirmation)
            .performScrollTo()
            .assertExists()
    }

    private fun unconfiguredState() = SyncRepositoryState(
        settings = null,
        status = SyncStatus.Unconfigured,
        profiles = emptyList(),
        pendingCount = 0,
        lastCursor = null,
        lastSuccessAt = null,
    )

    private fun catalog() = SyncDeviceIconCatalog(
        icons = listOf(
            SyncDeviceIconDefinition("phone", "📱", "Phone"),
            SyncDeviceIconDefinition("computer", "💻", "Computer"),
        ),
    )
}
