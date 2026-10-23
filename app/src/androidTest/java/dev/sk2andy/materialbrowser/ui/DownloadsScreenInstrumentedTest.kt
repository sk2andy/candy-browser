package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.DownloadEntry
import dev.sk2andy.materialbrowser.data.DownloadStatus
import dev.sk2andy.materialbrowser.data.DownloadTimeFilter
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsNewestFirstProgressAndFiltersByTextAndTime() {
        val now = System.currentTimeMillis()
        val old = LocalDate.now(ZoneId.systemDefault()).minusDays(40)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val running = entry(
            id = 1,
            name = "Candy-browser.apk",
            status = DownloadStatus.Running,
            bytes = 40,
            total = 100,
            lastModified = now,
        )
        val completed = entry(
            id = 2,
            name = "Manual.pdf",
            status = DownloadStatus.Successful,
            lastModified = now - 1_000,
        )
        val failed = entry(
            id = 3,
            name = "Old-archive.zip",
            status = DownloadStatus.Failed,
            lastModified = old,
        )
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                DownloadsScreen(
                    downloads = listOf(failed, completed, running),
                    onClearFinished = {},
                    onOpenDownload = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DownloadsScreenTestTags.progress(running.id))
            .assertIsDisplayed()
        val runningTop = composeRule.onNodeWithTag(DownloadsScreenTestTags.download(running.id))
            .fetchSemanticsNode().boundsInRoot.top
        val completedTop = composeRule.onNodeWithTag(DownloadsScreenTestTags.download(completed.id))
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(runningTop < completedTop)

        composeRule.onNodeWithTag(DownloadsScreenTestTags.SearchField)
            .performTextInput("manual")
        composeRule.onNodeWithTag(DownloadsScreenTestTags.download(completed.id)).assertIsDisplayed()
        composeRule.onNodeWithTag(DownloadsScreenTestTags.download(running.id)).assertDoesNotExist()

        composeRule.onNodeWithTag(DownloadsScreenTestTags.SearchField).performTextReplacement("")
        composeRule.onNodeWithTag(DownloadsScreenTestTags.timeFilter(DownloadTimeFilter.Today))
            .performClick()
        composeRule.onNodeWithTag(DownloadsScreenTestTags.download(failed.id)).assertDoesNotExist()
    }

    @Test
    fun clearConfirmationDeletesTerminalEntriesButKeepsActiveEntry() {
        val cleared = AtomicReference<List<DownloadEntry>>()
        val running = entry(id = 1, name = "active.bin", status = DownloadStatus.Running)
        val completed = entry(id = 2, name = "done.pdf", status = DownloadStatus.Successful)
        val failed = entry(id = 3, name = "failed.zip", status = DownloadStatus.Failed)
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                DownloadsScreen(
                    downloads = listOf(running, completed, failed),
                    onClearFinished = cleared::set,
                    onOpenDownload = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DownloadsScreenTestTags.Clear).performClick()
        composeRule.onNodeWithTag(DownloadsScreenTestTags.ClearDialog).assertIsDisplayed()
        composeRule.onNodeWithTag(DownloadsScreenTestTags.ClearConfirm).performClick()

        assertEquals(listOf(completed, failed), cleared.get())
    }

    @Test
    fun activeRowsOfferOnlySupportedControlsAndDispatchActions() {
        val cancelled = AtomicReference<DownloadEntry>()
        val toggled = AtomicReference<DownloadEntry>()
        val system = entry(id = 1, name = "system.bin", status = DownloadStatus.Running)
        val gecko = entry(id = -2, name = "gecko.bin", status = DownloadStatus.Paused)
            .copy(supportsPause = true, supportsCancel = true)
        val done = entry(id = 3, name = "done.bin", status = DownloadStatus.Successful)
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                DownloadsScreen(
                    downloads = listOf(system, gecko, done),
                    onClearFinished = {},
                    onOpenDownload = {},
                    onBack = {},
                    onCancelDownload = cancelled::set,
                    onTogglePauseDownload = toggled::set,
                )
            }
        }

        composeRule.onNodeWithTag(DownloadsScreenTestTags.pause(system.id)).assertDoesNotExist()
        composeRule.onNodeWithTag(DownloadsScreenTestTags.cancel(system.id)).performClick()
        assertEquals(system, cancelled.get())
        composeRule.onNodeWithTag(DownloadsScreenTestTags.pause(gecko.id)).performClick()
        assertEquals(gecko, toggled.get())
        composeRule.onNodeWithTag(DownloadsScreenTestTags.cancel(done.id)).assertDoesNotExist()
    }

    @Test
    fun completedDownloadOpensImmediatelyWhileControlsCollapse() {
        val running = entry(id = 1, name = "active.bin", status = DownloadStatus.Running)
            .copy(supportsPause = true)
        val download = mutableStateOf(running)
        val opened = AtomicReference<DownloadEntry>()
        val cancelled = AtomicReference<DownloadEntry>()
        val toggled = AtomicReference<DownloadEntry>()
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                DownloadsScreen(
                    downloads = listOf(download.value),
                    onClearFinished = {},
                    onOpenDownload = opened::set,
                    onBack = {},
                    onCancelDownload = cancelled::set,
                    onTogglePauseDownload = toggled::set,
                )
            }
        }
        composeRule.onNodeWithTag(DownloadsScreenTestTags.pause(running.id)).assertIsEnabled()
        composeRule.onNodeWithTag(DownloadsScreenTestTags.cancel(running.id)).assertIsEnabled()
        val activeHeight = composeRule.onNodeWithTag(DownloadsScreenTestTags.download(running.id))
            .fetchSemanticsNode().boundsInRoot.height
        composeRule.mainClock.autoAdvance = false
        val completed = running.copy(
            status = DownloadStatus.Successful,
            supportsPause = false,
            supportsCancel = false,
        )
        composeRule.runOnIdle { download.value = completed }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(DownloadsScreenTestTags.pause(running.id)).assertDoesNotExist()
        composeRule.onNodeWithTag(DownloadsScreenTestTags.cancel(running.id)).assertDoesNotExist()
        composeRule.onNodeWithTag(DownloadsScreenTestTags.progress(running.id)).assertDoesNotExist()
        composeRule.onNodeWithTag(DownloadsScreenTestTags.download(running.id))
            .assertIsEnabled()
            .performClick()
        assertEquals(completed, opened.get())
        assertEquals(null, cancelled.get())
        assertEquals(null, toggled.get())

        composeRule.mainClock.advanceTimeBy(1_000)
        val completedHeight = composeRule.onNodeWithTag(DownloadsScreenTestTags.download(running.id))
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(completedHeight < activeHeight)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun failedAndCancelledDownloadsRemoveControlsWithoutBecomingOpenable() {
        val running = entry(id = 1, name = "active.bin", status = DownloadStatus.Running)
        val download = mutableStateOf(running)
        val opened = AtomicReference<DownloadEntry>()
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                DownloadsScreen(
                    downloads = listOf(download.value),
                    onClearFinished = {},
                    onOpenDownload = opened::set,
                    onBack = {},
                )
            }
        }
        for (terminalStatus in listOf(DownloadStatus.Failed, DownloadStatus.Cancelled)) {
            composeRule.runOnIdle { download.value = running }
            composeRule.onNodeWithTag(DownloadsScreenTestTags.cancel(running.id)).assertIsEnabled()
            composeRule.runOnIdle { download.value = running.copy(status = terminalStatus) }
            composeRule.onNodeWithTag(DownloadsScreenTestTags.cancel(running.id)).assertDoesNotExist()
            composeRule.onNodeWithTag(DownloadsScreenTestTags.progress(running.id)).assertDoesNotExist()
            composeRule.onNodeWithTag(DownloadsScreenTestTags.download(running.id)).assertIsNotEnabled()
        }
        assertEquals(null, opened.get())
    }

    @Test
    fun failedDownloadOffersSourceOnlyForSafePublicHttpSource() {
        val failed = entry(id = 8, name = "failed.zip", status = DownloadStatus.Failed)
        val download = mutableStateOf(failed)
        val openedSource = AtomicReference<DownloadEntry>()
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                DownloadsScreen(
                    downloads = listOf(download.value),
                    onClearFinished = {},
                    onOpenDownload = {},
                    onOpenSource = openedSource::set,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(DownloadsScreenTestTags.openSource(failed.id)).assertIsDisplayed().performClick()
        assertEquals(failed, openedSource.get())

        for (unsafe in listOf(failed.copy(isPrivate = true), failed.copy(source = "blob:https://example.com/id"))) {
            composeRule.runOnIdle { download.value = unsafe }
            composeRule.onNodeWithTag(DownloadsScreenTestTags.openSource(failed.id)).assertDoesNotExist()
        }
    }

    private fun entry(
        id: Long,
        name: String,
        status: DownloadStatus,
        bytes: Long = 100,
        total: Long = 100,
        lastModified: Long = System.currentTimeMillis(),
    ) = DownloadEntry(
        id = id,
        name = name,
        source = "https://downloads.example/$name",
        status = status,
        bytes = bytes,
        total = total,
        lastModified = lastModified,
        mime = "application/octet-stream",
    )
}
