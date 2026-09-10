package dev.sk2andy.materialbrowser.browser.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkLongPressRulesTest {
    private val link = WebContentTarget(linkUrl = "https://example.com/")

    @Test
    fun `link actions resolve to their matching outcomes`() {
        val expected = mapOf(
            LinkLongPressAction.LinkPeek to LinkLongPressOutcome.ShowContext,
            LinkLongPressAction.CopyLink to LinkLongPressOutcome.CopyLink,
            LinkLongPressAction.Share to LinkLongPressOutcome.Share,
            LinkLongPressAction.DownloadLink to LinkLongPressOutcome.DownloadLink,
            LinkLongPressAction.OpenInNewTabInBackground to
                LinkLongPressOutcome.OpenInNewTabInBackground,
            LinkLongPressAction.OpenInNewTabInForeground to
                LinkLongPressOutcome.OpenInNewTabInForeground,
            LinkLongPressAction.OpenInPrivateTabInBackground to
                LinkLongPressOutcome.OpenInPrivateTabInBackground,
            LinkLongPressAction.OpenInPrivateTabInForeground to
                LinkLongPressOutcome.OpenInPrivateTabInForeground,
        )

        expected.forEach { (action, outcome) ->
            assertEquals(
                outcome,
                LinkLongPressRules.outcome(action, link, canOpenInPrivate = true),
            )
        }
    }

    @Test
    fun `image-only targets always keep content context`() {
        LinkLongPressAction.entries.forEach { action ->
            assertEquals(
                LinkLongPressOutcome.ShowContext,
                LinkLongPressRules.outcome(
                    action = action,
                    target = WebContentTarget(imageUrl = "https://example.com/image.png"),
                    canOpenInPrivate = true,
                ),
            )
        }
    }

    @Test
    fun `unavailable private actions fall back to Link Peek context`() {
        listOf(
            LinkLongPressAction.OpenInPrivateTabInBackground,
            LinkLongPressAction.OpenInPrivateTabInForeground,
        ).forEach { action ->
            assertEquals(
                LinkLongPressOutcome.ShowContext,
                LinkLongPressRules.outcome(
                    action = action,
                    target = link,
                    canOpenInPrivate = false,
                ),
            )
        }
    }
}
