package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ExternalLinkPreviewState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalLinkPreviewBarInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactBarRoutesOpenProfileAndOverflowActions() {
        var opened = false
        var selectedProfileId: String? = null
        var shared = false
        val profiles = listOf(
            BrowserProfile(id = "default", emoji = "🍬"),
            BrowserProfile(id = "work", emoji = "💼"),
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                ExternalLinkPreviewBar(
                    state = ExternalLinkPreviewState(
                        sessionId = 1,
                        generation = 0,
                        currentUrl = "https://example.com/path",
                        targetProfileId = "default",
                    ),
                    profiles = profiles,
                    isDesktopView = false,
                    blurTarget = null,
                    rootBottomInWindowPx = 0,
                    onDismissPreview = {},
                    onOpenInCandy = { opened = true },
                    onSelectProfile = { selectedProfileId = it },
                    onShare = { shared = true },
                    onCopyLink = {},
                    onFindInPage = {},
                    onDesktopViewChange = {},
                )
            }
        }

        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.Open).performClick()
        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.Profile).performClick()
        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.profile("work")).performClick()
        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.Overflow).performClick()
        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.Share).performClick()

        assertTrue(opened)
        assertEquals("work", selectedProfileId)
        assertTrue(shared)
    }
}
