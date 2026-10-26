package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadManagerApp
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadProtocol
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

        composeRule.onNodeWithTag(DownloadSettingsTestTags.Directory).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.DirectoryReset).performScrollTo().performClick()

        assertNull(changed.get().downloadSubdirectory)
    }

    @Test
    fun askEveryTimeKeepsBuiltInFolderVisibleButDisabled() {
        setContent(
            settings = BrowserDownloadSettings(managerMode = DownloadManagerMode.AskEveryTime),
            onChanged = {},
        )

        composeRule.onNodeWithTag(DownloadSettingsTestTags.Directory)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun folderDialogSavesNestedSubdirectory() {
        val changed = AtomicReference<BrowserDownloadSettings>()
        setContent(settings = BrowserDownloadSettings(), onChanged = changed::set)

        composeRule.onNodeWithTag(DownloadSettingsTestTags.Directory).performScrollTo().performClick()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.DirectoryDialog).assertIsDisplayed()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.DirectoryInput)
            .performTextInput("Candy/Documents")
        composeRule.onNodeWithTag(DownloadSettingsTestTags.DirectoryConfirm).performClick()

        assertEquals("Candy/Documents", changed.get().downloadSubdirectory)
    }

    @Test
    fun managerRadiosRetainFolderAndSessionSettingsAcrossDependencies() {
        val managers = listOf(
            ExternalDownloadManagerApp(
                id = "one-dm", packageName = "test.one.dm", activityName = "Download",
                label = "1DM", protocol = ExternalDownloadProtocol.View, isOneDm = true,
            ),
            ExternalDownloadManagerApp(
                id = "other", packageName = "test.other", activityName = "Download",
                label = "Other downloader", protocol = ExternalDownloadProtocol.View, isOneDm = false,
            ),
        )
        var settings by mutableStateOf(BrowserDownloadSettings(downloadSubdirectory = "Candy/Documents"))
        var writes = 0
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                DownloadsSettingsPage(
                    settings = settings,
                    externalManagers = managers,
                    onSettingsChanged = {
                        settings = it
                        writes++
                    },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(DownloadSettingsTestTags.externalManager("other"))
            .performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.Directory)
            .performScrollTo().assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.DirectoryDialog).assertDoesNotExist()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.DirectoryReset)
            .performScrollTo().assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.OneDmSession)
            .performScrollTo().assertIsNotEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, writes)
            assertEquals("other", settings.externalManagerId)
            assertEquals("Candy/Documents", settings.downloadSubdirectory)
        }
        composeRule.onNodeWithTag(DownloadSettingsTestTags.externalManager("one-dm"))
            .performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.OneDmSession)
            .performScrollTo().assertIsEnabled().performClick().assertIsOn()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.AskEveryTime)
            .performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.OneDmSession)
            .performScrollTo().assertIsEnabled().assertIsOn()
        composeRule.runOnIdle {
            assertEquals(DownloadManagerMode.AskEveryTime, settings.managerMode)
            assertNull(settings.externalManagerId)
        }
        composeRule.onNodeWithTag(DownloadSettingsTestTags.BuiltIn)
            .performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.Directory)
            .performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.OneDmSession)
            .performScrollTo().assertIsNotEnabled().assertIsOn().performClick()
        composeRule.runOnIdle {
            assertEquals(5, writes)
            assertEquals(DownloadManagerMode.BuiltIn, settings.managerMode)
            assertNull(settings.externalManagerId)
            assertEquals("Candy/Documents", settings.downloadSubdirectory)
            assertEquals(true, settings.shareSessionDataWithOneDm)
        }
    }

    @Test
    fun fluxDownSessionSharingStaysIndependentAndRetainedWhenIrrelevant() {
        var managers by mutableStateOf(listOf(
            ExternalDownloadManagerApp(
                id = "one-dm", packageName = "test.one.dm", activityName = "Download",
                label = "1DM", protocol = ExternalDownloadProtocol.View, isOneDm = true,
            ),
            ExternalDownloadManagerApp(
                id = "flux-down", packageName = "com.fluxdown.app", activityName = "MainActivity",
                label = "FluxDown", protocol = ExternalDownloadProtocol.View, isOneDm = false,
            ),
            ExternalDownloadManagerApp(
                id = "other", packageName = "test.other", activityName = "Download",
                label = "Other downloader", protocol = ExternalDownloadProtocol.View, isOneDm = false,
            ),
        ))
        var settings by mutableStateOf(BrowserDownloadSettings())
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                DownloadsSettingsPage(
                    settings = settings,
                    externalManagers = managers,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DownloadSettingsTestTags.FluxDownSession)
            .performScrollTo().assertIsNotEnabled().assertIsOff()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.AskEveryTime)
            .performScrollTo().performClick()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.FluxDownSession)
            .performScrollTo().assertIsEnabled().performClick().assertIsOn()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.OneDmSession)
            .performScrollTo().assertIsEnabled().assertIsOff()

        composeRule.onNodeWithTag(DownloadSettingsTestTags.externalManager("one-dm"))
            .performScrollTo().performClick()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.FluxDownSession)
            .performScrollTo().assertIsNotEnabled().assertIsOn().performClick()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.OneDmSession)
            .performScrollTo().assertIsEnabled().performClick().assertIsOn()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.externalManager("flux-down"))
            .performScrollTo().performClick()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.FluxDownSession)
            .performScrollTo().assertIsEnabled().assertIsOn()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.OneDmSession)
            .performScrollTo().assertIsNotEnabled().assertIsOn()

        composeRule.onNodeWithTag(DownloadSettingsTestTags.externalManager("other"))
            .performScrollTo().performClick()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.FluxDownSession)
            .performScrollTo().assertIsNotEnabled().assertIsOn().performClick()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.BuiltIn)
            .performScrollTo().performClick()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.FluxDownSession)
            .performScrollTo().assertIsNotEnabled().assertIsOn()
        composeRule.onNodeWithTag(DownloadSettingsTestTags.AskEveryTime)
            .performScrollTo().performClick()
        composeRule.runOnIdle { managers = emptyList() }
        composeRule.onNodeWithTag(DownloadSettingsTestTags.FluxDownSession)
            .performScrollTo().assertIsNotEnabled().assertIsOn()
        composeRule.runOnIdle {
            assertEquals(true, settings.shareSessionDataWithOneDm)
            assertEquals(true, settings.shareSessionDataWithFluxDown)
        }
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
