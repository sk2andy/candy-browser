package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDefinition
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState
import dev.sk2andy.materialbrowser.sync.SyncStatus
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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

    @Test
    fun setupCanBindAnExistingLocalProfile() {
        val configured = AtomicReference<SyncConnectionSettings>()
        composeRule.setContent {
            MaterialBrowserTheme {
                SyncSettingsPage(
                    state = unconfiguredState(),
                    iconCatalog = catalog(),
                    localProfiles = listOf(
                        BrowserProfile(id = "personal", emoji = "🏠"),
                        BrowserProfile(id = "work", emoji = "💼"),
                    ),
                    activeProfileId = "personal",
                    onConfigure = { settings ->
                        configured.set(settings)
                        true
                    },
                    onEnroll = { _, _, _ -> },
                    onRefresh = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SyncSettingsTestTags.LocalProfile).performClick()
        composeRule.onNodeWithText(
            "💼  ${context.getString(R.string.sync_local_profile_existing)}",
        ).performClick()
        composeRule.onNodeWithTag(SyncSettingsTestTags.Endpoint)
            .performTextInput("https://sync.example")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Username).performTextInput("candy")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Password).performTextInput("server-secret")
        composeRule.onNodeWithTag(SyncSettingsTestTags.DeviceName).performTextInput("Phone")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Passphrase)
            .performScrollTo()
            .performTextInput("0123456789abcdef")
        composeRule.onNodeWithTag(SyncSettingsTestTags.PassphraseConfirmation)
            .performScrollTo()
            .performTextInput("0123456789abcdef")
        composeRule.onNodeWithTag(SyncSettingsTestTags.Enroll)
            .performScrollTo()
            .performClick()

        assertEquals("work", configured.get().localProfileId)
    }

    @Test
    fun secretFieldsExposeIndependentVisibilityControls() {
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

        val showPassword = context.getString(R.string.sync_show_password)
        val hidePassword = context.getString(R.string.sync_hide_password)
        val passwordVisibility = composeRule.onNodeWithTag(
            SyncSettingsTestTags.PasswordVisibility,
        )
        passwordVisibility.assertContentDescriptionEquals(showPassword).performClick()
        passwordVisibility.assertContentDescriptionEquals(hidePassword)

        composeRule.onNodeWithTag(SyncSettingsTestTags.PassphraseVisibility)
            .performScrollTo()
            .assertContentDescriptionEquals(showPassword)
        composeRule.onNodeWithTag(SyncSettingsTestTags.PassphraseConfirmationVisibility)
            .performScrollTo()
            .assertContentDescriptionEquals(showPassword)
    }

    @Test
    fun accentColorPickerSelectsVisualPaletteOptions() {
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

        val initial = composeRule.onNodeWithTag(
            SyncSettingsTestTags.accentColor(312),
        )
        val next = composeRule.onNodeWithTag(
            SyncSettingsTestTags.accentColor(216),
        )
        initial.performScrollTo().assertIsSelected()
        next.performClick().assertIsSelected()
        initial.assertIsNotSelected()
    }

    @Test
    fun passphraseFieldsRemainReachableAfterOpeningKeyboard() {
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

        composeRule.onNodeWithTag(SyncSettingsTestTags.Password).performClick()
        composeRule.onNodeWithTag(SyncSettingsTestTags.Passphrase)
            .performScrollTo()
            .performClick()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SyncSettingsTestTags.PassphraseConfirmation)
            .performScrollTo()
            .performClick()
            .assertIsDisplayed()
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
