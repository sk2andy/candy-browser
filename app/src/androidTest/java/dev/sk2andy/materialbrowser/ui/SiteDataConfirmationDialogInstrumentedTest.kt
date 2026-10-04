package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.SiteDataTarget
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteDataConfirmationDialogInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unsupportedEngineShowsTargetAndNoDeleteAction() {
        val deletions = AtomicInteger()
        val cancellations = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                SiteDataConfirmationDialog(
                    target = target(),
                    supported = false,
                    onConfirm = deletions::incrementAndGet,
                    onDismiss = cancellations::incrementAndGet,
                )
            }
        }

        composeRule.onNodeWithText("https://news.example:8443").assertExists()
        composeRule.onNodeWithText(string(R.string.command_site_data_unsupported)).assertExists()
        composeRule.onNodeWithTag(SiteDataConfirmationTestTags.Delete).assertDoesNotExist()
        composeRule.onNodeWithTag(SiteDataConfirmationTestTags.Cancel).performClick()
        assertEquals(0, deletions.get())
        assertEquals(1, cancellations.get())
    }

    @Test
    fun staleTargetExplainsPageChangeAndHidesDeleteAction() {
        val deletions = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                SiteDataConfirmationDialog(
                    target = target(),
                    supported = false,
                    targetIsCurrent = false,
                    onConfirm = deletions::incrementAndGet,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.command_site_data_target_changed)).assertExists()
        composeRule.onNodeWithTag(SiteDataConfirmationTestTags.Delete).assertDoesNotExist()
        assertEquals(0, deletions.get())
    }

    @Test
    fun sharedStorageShowsScopeWarningAndRoutesConfirmedDeletion() {
        val deletions = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                SiteDataConfirmationDialog(
                    target = target().copy(sharesStorage = true),
                    supported = true,
                    onConfirm = deletions::incrementAndGet,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.command_site_data_shared_storage)).assertExists()
        composeRule.onNodeWithText(string(R.string.command_site_data_confirm_webview)).assertExists()
        composeRule.onNodeWithTag(SiteDataConfirmationTestTags.Delete).performClick()
        assertEquals(1, deletions.get())
    }

    private fun target() = SiteDataTarget(
        tabId = "tab",
        url = "https://news.example:8443/page",
        origin = "https://news.example:8443",
        host = "news.example",
        profileId = "work",
        isPrivate = false,
        navigationGeneration = 3,
    )

    private fun string(resource: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)
}
