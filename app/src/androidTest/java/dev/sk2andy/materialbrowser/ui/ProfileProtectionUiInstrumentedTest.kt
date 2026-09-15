package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileProtectionUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cooldownDialogAcceptsMinuteInput() {
        val saved = AtomicReference<ProfileProtection?>()
        composeRule.setContent {
            MaterialTheme {
                ProfileProtectionDialog(
                    current = null,
                    onSave = saved::set,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.profile_protection_cooldown),
        ).performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.profile_protection_minutes),
        ).performTextReplacement("12")
        composeRule.onNodeWithText(context.getString(R.string.action_save)).performClick()

        assertEquals(ProfileProtection(ProfileLockTrigger.Cooldown, 12), saved.get())
    }

    @Test
    fun cooldownInputRemainsReachableWithLargeText() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                MaterialTheme {
                    ProfileProtectionDialog(
                        current = null,
                        onSave = {},
                        onDismiss = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.profile_protection_cooldown),
        ).performScrollTo().performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.profile_protection_minutes),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun lockedOverlayHidesProfileBehindUnlockAction() {
        composeRule.setContent {
            MaterialTheme {
                ProfileLockedOverlay(
                    profileEmoji = "💼",
                    unlockAvailable = true,
                    canSwitchProfile = false,
                    onUnlock = {},
                    onSwitchProfile = {},
                )
            }
        }

        composeRule.onNodeWithTag(ProfileProtectionTestTags.LockedOverlay).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.profile_locked_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.profile_unlock_action))
            .assertIsDisplayed()
    }

    @Test
    fun protectedExportWarnsThatZipIsReadable() {
        composeRule.setContent {
            MaterialTheme {
                AppDataExportWarningDialog(
                    hasProtectedProfiles = true,
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.data_archive_export_protected_warning_message),
        ).assertIsDisplayed()
    }
}
