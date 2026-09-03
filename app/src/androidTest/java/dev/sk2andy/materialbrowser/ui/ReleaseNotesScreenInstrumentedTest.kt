package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.ReleaseNotesMarkdownRules
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseNotesScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun releasePageRendersMarkdownAndCompletes() {
        val completed = AtomicBoolean(false)
        val openedLink = AtomicReference<String?>(null)
        val document = requireNotNull(
            ReleaseNotesMarkdownRules.parse(
                markdown = """
                    # Candy Sync is here

                    Move between Candy Browser and your desktop browser.

                    ## Your tabs, everywhere

                    - Open tabs
                    - Close tabs

                    Read [sync docs](https://example.com/sync).

                    > Everything is end-to-end encrypted.
                """.trimIndent(),
                versionName = "0.32",
            ),
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                ReleaseNotesScreen(
                    versionName = "0.32",
                    document = document,
                    onDone = { completed.set(true) },
                    onOpenLink = openedLink::set,
                )
            }
        }

        composeRule.onNodeWithTag(ReleaseNotesTestTags.Screen).assertIsDisplayed()
        composeRule.onNodeWithText("Candy Sync is here").assertIsDisplayed()
        composeRule.onNodeWithText("Version 0.32").assertIsDisplayed()
        composeRule.onNodeWithText("Everything is end-to-end encrypted.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Read sync docs.")
            .performScrollTo()
            .performClick()
        assertEquals("https://example.com/sync", openedLink.get())
        composeRule.onNodeWithTag(ReleaseNotesTestTags.Done).performClick()

        assertTrue(completed.get())
    }
}
