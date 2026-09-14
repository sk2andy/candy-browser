package dev.sk2andy.materialbrowser

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import dev.sk2andy.materialbrowser.update.AvailableAppUpdate
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateDialogInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun releaseNotesDownloadAndLaterRemainSeparateActions() {
        val openedNotes = AtomicInteger()
        val downloads = AtomicInteger()
        val dismissals = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                AppUpdateDialog(
                    update = AvailableAppUpdate(
                        versionName = "0.99",
                        downloadUrl = "https://github.com/sk2andy/candy-browser/releases/" +
                            "download/v0.99/CandyBrowser-v0.99-release.apk",
                        fileName = "CandyBrowser-v0.99-release.apk",
                    ),
                    onDismiss = { dismissals.incrementAndGet() },
                    onDownload = { downloads.incrementAndGet() },
                    onOpenReleaseNotes = { openedNotes.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.update_available_message, "0.99"),
        ).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.action_view_release_notes))
            .performClick()

        assertEquals(1, openedNotes.get())
        assertEquals(0, downloads.get())
        assertEquals(0, dismissals.get())

        composeRule.onNodeWithText(context.getString(R.string.action_download_update))
            .performClick()
        assertEquals(1, downloads.get())
        assertEquals(1, openedNotes.get())
        assertEquals(0, dismissals.get())

        composeRule.onNodeWithText(context.getString(R.string.action_later)).performClick()
        assertEquals(1, dismissals.get())
        assertEquals(1, downloads.get())
        assertEquals(1, openedNotes.get())
    }
}
