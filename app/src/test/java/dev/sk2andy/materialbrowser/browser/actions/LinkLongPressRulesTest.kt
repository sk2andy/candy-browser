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
            LinkLongPressAction.OpenInNewTab to LinkLongPressOutcome.OpenInNewTab,
            LinkLongPressAction.OpenInPrivateTab to LinkLongPressOutcome.OpenInPrivateTab,
            LinkLongPressAction.Share to LinkLongPressOutcome.Share,
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
    fun `unavailable private action falls back to Link Peek context`() {
        assertEquals(
            LinkLongPressOutcome.ShowContext,
            LinkLongPressRules.outcome(
                action = LinkLongPressAction.OpenInPrivateTab,
                target = link,
                canOpenInPrivate = false,
            ),
        )
    }
}
