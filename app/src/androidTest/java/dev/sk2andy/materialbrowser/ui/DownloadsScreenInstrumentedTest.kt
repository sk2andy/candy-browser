package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsDisplayed
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
