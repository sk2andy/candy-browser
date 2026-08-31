package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.MaterialTheme
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
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecallSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recallOptInDefaultsOffExplainsLocalTextAndEmitsEnable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enabled = AtomicBoolean()
        composeRule.setContent {
            MaterialTheme {
                ProtectionAndDataSettingsPage(
                    blockerSettings = BlockerSettings(),
                    blockedCount = 0,
                    isRecallEnabled = false,
                    trustsUserCertificates = false,
                    onBlockerSettingsChanged = {},
                    onRecallEnabledChanged = enabled::set,
                    onPrivacyXRay = {},
                    onPermissionRadar = {},
                    onFilterStudio = {},
                    onClearData = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.recall_settings_summary))
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithTag(ProtectionSettingsTestTags.Recall)
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        assertTrue(enabled.get())
    }
}
