package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDataTransferSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exportAndImportActionsEmitCallbacks() {
        val exportCalls = AtomicInteger()
        val importCalls = AtomicInteger()
        composeRule.setContent {
            MaterialTheme {
                ProtectionAndDataSettingsPage(
                    blockerSettings = BlockerSettings(),
                    blockedCount = 0,
                    trustsUserCertificates = false,
                    onBlockerSettingsChanged = {},
                    onPrivacyXRay = {},
                    onPermissionRadar = {},
                    onFilterStudio = {},
                    onExportAppData = exportCalls::incrementAndGet,
                    onImportAppData = importCalls::incrementAndGet,
                    onClearData = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ProtectionSettingsTestTags.ExportAppData)
            .performScrollTo()
            .assertExists()
            .performClick()
        composeRule.onNodeWithTag(ProtectionSettingsTestTags.ImportAppData)
            .performScrollTo()
            .assertExists()
            .performClick()

        assertEquals(1, exportCalls.get())
        assertEquals(1, importCalls.get())
    }
}
