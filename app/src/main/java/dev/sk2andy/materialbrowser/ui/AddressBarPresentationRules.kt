package dev.sk2andy.materialbrowser.ui

internal enum class AddressBarPresentation {
    Docked,
    Compact,
    Expanded,
    CommandFeedback,
}

internal object AddressBarPresentationRules {
    fun resolve(
        docked: Boolean,
        compact: Boolean,
        editing: Boolean,
        showingCommandFeedback: Boolean,
    ): AddressBarPresentation = when {
        showingCommandFeedback -> AddressBarPresentation.CommandFeedback
        editing -> AddressBarPresentation.Expanded
        docked -> AddressBarPresentation.Docked
        compact -> AddressBarPresentation.Compact
        else -> AddressBarPresentation.Expanded
    }
}

