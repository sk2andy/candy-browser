package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import dev.sk2andy.materialbrowser.data.DownloadManagerMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun builtInDownloaderShowsFolderAndCanResetIt() {
        val changed = AtomicReference<BrowserDownloadSettings>()
        setContent(
            settings = BrowserDownloadSettings(downloadSubdirectory = "Candy/Documents"),
            onChanged = changed::set,
        )

        composeRule.onNodeWithTag(DownloadSettingsTestTags.Directory).assertIsDisplayed()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.DirectoryReset).performClick()

        assertNull(changed.get().downloadSubdirectory)
    }

    @Test
    fun askEveryTimeHidesBuiltInFolderChoice() {
        setContent(
            settings = BrowserDownloadSettings(managerMode = DownloadManagerMode.AskEveryTime),
            onChanged = {},
        )

        composeRule.onNodeWithTag(DownloadSettingsTestTags.Directory).assertDoesNotExist()
    }

    @Test
    fun folderDialogSavesNestedSubdirectory() {
        val changed = AtomicReference<BrowserDownloadSettings>()
        setContent(settings = BrowserDownloadSettings(), onChanged = changed::set)

        composeRule.onNodeWithTag(DownloadSettingsTestTags.Directory).performClick()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.DirectoryDialog).assertIsDisplayed()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.DirectoryInput)
            .performTextInput("Candy/Documents")
        composeRule.onNodeWithTag(DownloadSettingsTestTags.DirectoryConfirm).performClick()

        assertEquals("Candy/Documents", changed.get().downloadSubdirectory)
    }

    private fun setContent(
        settings: BrowserDownloadSettings,
        onChanged: (BrowserDownloadSettings) -> Unit,
    ) {
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                DownloadsSettingsPage(
                    settings = settings,
                    externalManagers = emptyList(),
                    onSettingsChanged = onChanged,
                    onBack = {},
                )
            }
        }
    }
}
