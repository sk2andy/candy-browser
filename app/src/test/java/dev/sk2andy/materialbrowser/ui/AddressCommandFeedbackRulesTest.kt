package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.commands.BrowserCommandKind
import dev.sk2andy.materialbrowser.browser.commands.CommandDispatchOutcome
import dev.sk2andy.materialbrowser.browser.commands.CommandResult
import org.junit.Assert.assertEquals
import org.junit.Test

class AddressCommandFeedbackRulesTest {
    @Test
    fun `success results map to confirm feedback`() {
        val expected = listOf(
            CommandResult.CacheClearedAndReloaded to AddressCommandFeedbackMessage.CacheCleared,
            CommandResult.CookiesClearedAndReloaded to AddressCommandFeedbackMessage.CookiesCleared,
            CommandResult.Reloaded to AddressCommandFeedbackMessage.Reloaded,
            CommandResult.LoadingStopped to AddressCommandFeedbackMessage.LoadingStopped,
            CommandResult.TabPinned to AddressCommandFeedbackMessage.TabPinned,
            CommandResult.TabUnpinned to AddressCommandFeedbackMessage.TabUnpinned,
            CommandResult.RegularTabCreated to AddressCommandFeedbackMessage.RegularTabCreated,
            CommandResult.IncognitoTabCreated to AddressCommandFeedbackMessage.IncognitoTabCreated,
            CommandResult.SettingsOpened to AddressCommandFeedbackMessage.SettingsOpened,
        )

        expected.forEach { (result, message) ->
            assertEquals(
                AddressCommandFeedback(message, AddressCommandFeedbackTone.Confirm),
                AddressCommandFeedbackRules.from(CommandDispatchOutcome.Succeeded(result)),
            )
        }
    }

    @Test
    fun `parameterized results preserve count and profile label`() {
        assertEquals(
            AddressCommandFeedback(
                message = AddressCommandFeedbackMessage.DuplicateTabsClosed,
                tone = AddressCommandFeedbackTone.Confirm,
                count = 3,
            ),
            AddressCommandFeedbackRules.from(
                CommandDispatchOutcome.Succeeded(CommandResult.DuplicateTabsClosed(3)),
            ),
        )
        assertEquals(
            AddressCommandFeedback(
                message = AddressCommandFeedbackMessage.TabMoved,
                tone = AddressCommandFeedbackTone.Confirm,
                targetProfileLabel = "2 · 💼",
            ),
            AddressCommandFeedbackRules.from(
                CommandDispatchOutcome.Succeeded(CommandResult.TabMoved("2 · 💼")),
            ),
        )
        assertEquals(
            AddressCommandFeedback(
                message = AddressCommandFeedbackMessage.ProfileSwitched,
                tone = AddressCommandFeedbackTone.Confirm,
                targetProfileLabel = "3 · ✈️",
            ),
            AddressCommandFeedbackRules.from(
                CommandDispatchOutcome.Succeeded(CommandResult.ProfileSwitched("3 · ✈️")),
            ),
        )
    }

    @Test
    fun `rejection maps to bounded reject feedback`() {
        val feedback = checkNotNull(
            AddressCommandFeedbackRules.from(
                CommandDispatchOutcome.Rejected(BrowserCommandKind.Reload),
            ),
        )

        assertEquals(
            AddressCommandFeedback(
                message = AddressCommandFeedbackMessage.Rejected,
                tone = AddressCommandFeedbackTone.Reject,
            ),
            feedback,
        )
        assertEquals(1_100L, AddressCommandFeedbackRules.displayDurationMillis(feedback))
        assertEquals(
            4_000L,
            AddressCommandFeedbackRules.accessibleDurationMillis(feedback, 4_000L),
        )
    }

    @Test
    fun `confirm feedback duration is finite`() {
        val feedback = checkNotNull(
            AddressCommandFeedbackRules.from(
                CommandDispatchOutcome.Succeeded(CommandResult.Reloaded),
            ),
        )

        assertEquals(1_800L, AddressCommandFeedbackRules.displayDurationMillis(feedback))
        assertEquals(
            AddressCommandFeedbackRules.MaximumDurationMillis,
            AddressCommandFeedbackRules.accessibleDurationMillis(feedback, Long.MAX_VALUE),
        )
    }

    @Test
    fun `pending outcome has no feedback or haptic tone`() {
        assertEquals(
            null,
            AddressCommandFeedbackRules.from(
                CommandDispatchOutcome.Pending(BrowserCommandKind.ClearCookiesAndReload),
            ),
        )
    }
}
