package dev.sk2andy.materialbrowser.browser

import kotlin.test.Test
import kotlin.test.assertEquals

class LinkLongPressRulesTest {
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
                LinkLongPressRules.outcome(
                    action = action,
                    hasLinkTarget = true,
                    canOpenInPrivate = true,
                ),
            )
        }
    }

    @Test
    fun `targets without a link always keep content context`() {
        LinkLongPressAction.entries.forEach { action ->
            assertEquals(
                LinkLongPressOutcome.ShowContext,
                LinkLongPressRules.outcome(
                    action = action,
                    hasLinkTarget = false,
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
                    hasLinkTarget = true,
                    canOpenInPrivate = false,
                ),
            )
        }
    }
}
