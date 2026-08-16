package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserCaTrustWarningInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun userCaTrustShowsGlobalDisclosure() {
        setProtectionPage(trustsUserCertificates = true)

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.UserCaWarning)
            .assertExists()
    }

    @Test
    fun systemOnlyTrustOmitsUserCaDisclosure() {
        setProtectionPage(trustsUserCertificates = false)

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.UserCaWarning)
            .assertDoesNotExist()
    }

    private fun setProtectionPage(trustsUserCertificates: Boolean) {
        composeRule.setContent {
            MaterialTheme {
                ProtectionAndDataSettingsPage(
                    blockerSettings = BlockerSettings(),
                    blockedCount = 0,
                    trustsUserCertificates = trustsUserCertificates,
                    onBlockerSettingsChanged = {},
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
