package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.commands.CommandDispatchOutcome
import dev.sk2andy.materialbrowser.browser.commands.CommandResult

internal enum class AddressCommandFeedbackTone {
    Confirm,
    Reject,
}

internal enum class AddressCommandFeedbackMessage {
    CacheCleared,
    CookiesCleared,
    Reloaded,
    LoadingStopped,
    TabPinned,
    TabUnpinned,
    DuplicateTabsClosed,
    TabMoved,
    ProfileSwitched,
    RegularTabCreated,
    IncognitoTabCreated,
    SettingsOpened,
    Rejected,
}

internal data class AddressCommandFeedback(
    val message: AddressCommandFeedbackMessage,
    val tone: AddressCommandFeedbackTone,
    val count: Int = 0,
    val targetProfileLabel: String? = null,
)

internal object AddressCommandFeedbackRules {
    const val ConfirmDurationMillis = 1_800L
    const val RejectDurationMillis = 1_100L

    const val MaximumDurationMillis = 10_000L

    fun displayDurationMillis(feedback: AddressCommandFeedback): Long = when (feedback.tone) {
        AddressCommandFeedbackTone.Confirm -> ConfirmDurationMillis
        AddressCommandFeedbackTone.Reject -> RejectDurationMillis
    }

    fun accessibleDurationMillis(
        feedback: AddressCommandFeedback,
        recommendedTimeoutMillis: Long,
    ): Long = recommendedTimeoutMillis.coerceIn(
        displayDurationMillis(feedback),
        MaximumDurationMillis,
    )

    fun from(outcome: CommandDispatchOutcome): AddressCommandFeedback? = when (outcome) {
        is CommandDispatchOutcome.Pending -> null
        is CommandDispatchOutcome.Rejected -> AddressCommandFeedback(
            message = AddressCommandFeedbackMessage.Rejected,
            tone = AddressCommandFeedbackTone.Reject,
        )
        is CommandDispatchOutcome.Succeeded -> when (val result = outcome.result) {
            CommandResult.CacheClearedAndReloaded -> confirm(AddressCommandFeedbackMessage.CacheCleared)
            CommandResult.CookiesClearedAndReloaded -> confirm(AddressCommandFeedbackMessage.CookiesCleared)
            CommandResult.Reloaded -> confirm(AddressCommandFeedbackMessage.Reloaded)
            CommandResult.LoadingStopped -> confirm(AddressCommandFeedbackMessage.LoadingStopped)
            CommandResult.TabPinned -> confirm(AddressCommandFeedbackMessage.TabPinned)
            CommandResult.TabUnpinned -> confirm(AddressCommandFeedbackMessage.TabUnpinned)
            is CommandResult.DuplicateTabsClosed -> confirm(
                message = AddressCommandFeedbackMessage.DuplicateTabsClosed,
                count = result.count,
            )
            is CommandResult.TabMoved -> confirm(
                message = AddressCommandFeedbackMessage.TabMoved,
                targetProfileLabel = result.targetProfileLabel,
            )
            is CommandResult.ProfileSwitched -> confirm(
                message = AddressCommandFeedbackMessage.ProfileSwitched,
                targetProfileLabel = result.targetProfileLabel,
            )
            CommandResult.RegularTabCreated -> confirm(AddressCommandFeedbackMessage.RegularTabCreated)
            CommandResult.IncognitoTabCreated -> confirm(AddressCommandFeedbackMessage.IncognitoTabCreated)
            CommandResult.SettingsOpened -> confirm(AddressCommandFeedbackMessage.SettingsOpened)
        }
    }

    private fun confirm(
        message: AddressCommandFeedbackMessage,
        count: Int = 0,
        targetProfileLabel: String? = null,
    ) = AddressCommandFeedback(
        message = message,
        tone = AddressCommandFeedbackTone.Confirm,
        count = count,
        targetProfileLabel = targetProfileLabel,
    )
}
