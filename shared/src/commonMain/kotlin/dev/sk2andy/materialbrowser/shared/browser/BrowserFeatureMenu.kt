package dev.sk2andy.materialbrowser.shared.browser

/**
 * Platform-neutral identity and ordering for Candy's complete browser menu.
 *
 * Android renders these ordered sections with the commonMain menu composable. iOS projects the
 * same state and actions into its native Liquid Glass menu so the Apple chrome can morph without
 * duplicating browser behavior. Firefox extensions are the only Android-only addition.
 */
enum class BrowserFeatureMenuSection {
    Toolbar,
    Page,
    Toppings,
    Candy,
    Browser,
}

enum class BrowserFeatureMenuItemKind {
    Command,
    Toggle,
    Navigation,
}

enum class BrowserFeatureMenuAction {
    Back,
    Forward,
    Reload,
    Stop,
    ToggleFavorite,
    TogglePinned,
    ShowTabs,
    NewTab,
    CloseTab,
    ParkAddressBarRight,
    OpenReader,
    TranslatePage,
    FindInPage,
    Share,
    OpenExternal,
    Print,
    ToggleCookieBannerRemoval,
    ToggleForceVerticalScrolling,
    ToggleForcePageZooming,
    ToggleForceSafeArea,
    ToggleAlwaysBlockPopups,
    ToggleDesktopView,
    ToggleDomainMute,
    OpenCandyTrail,
    AddSiteCapsule,
    Summarize,
    SnoozeTab,
    DockAddressBar,
    OpenSnoozedTabs,
    OpenHistory,
    OpenSettings,
    OpenFirefoxExtensions,
    InvokeToppingCommand,
}

enum class BrowserFeatureMenuLabelKey {
    Back,
    Forward,
    Reload,
    StopLoading,
    AddFavorite,
    RemoveFavorite,
    PinTab,
    UnpinTab,
    Tabs,
    NewTab,
    CloseTab,
    ParkAddressBarRight,
    Reader,
    Translate,
    FindInPage,
    Share,
    OpenExternal,
    Print,
    CookieBannerRemoval,
    ForceVerticalScrolling,
    ForcePageZooming,
    ForceSafeArea,
    AlwaysBlockPopups,
    DesktopView,
    MuteDomain,
    UnmuteDomain,
    CandyTrail,
    AddSiteCapsule,
    Summarize,
    SnoozeTab,
    DockAddressBar,
    SnoozedTabs,
    History,
    Settings,
    FirefoxExtensions,
    ToppingsCommand,
}

data class BrowserFeatureMenuItem(
    val stableId: String,
    val action: BrowserFeatureMenuAction,
    val labelKey: BrowserFeatureMenuLabelKey,
    val section: BrowserFeatureMenuSection,
    val kind: BrowserFeatureMenuItemKind,
    val enabled: Boolean,
    val checked: Boolean? = null,
    val dynamicLabel: String? = null,
    val supportingText: String? = null,
    val toppingScriptId: String? = null,
    val toppingCommandId: String? = null,
)

data class BrowserToppingMenuCommand(
    val scriptId: String,
    val commandId: String,
    val caption: String,
    val scriptName: String,
)

data class BrowserFeatureMenuState(
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val hasPage: Boolean = false,
    val canCloseTab: Boolean = true,
    val canToggleFavorite: Boolean = false,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val canOpenReader: Boolean = false,
    val canTranslatePage: Boolean = false,
    val canUseDocumentActions: Boolean = false,
    val canToggleCookieBannerRemoval: Boolean = false,
    val isCookieBannerRemovalEnabled: Boolean = false,
    val canToggleForceVerticalScrolling: Boolean = false,
    val isForceVerticalScrollingEnabled: Boolean = false,
    val canToggleForcePageZooming: Boolean = false,
    val isForcePageZoomingEnabled: Boolean = false,
    val canToggleForceSafeArea: Boolean = false,
    val isForceSafeAreaEnabled: Boolean = false,
    val canToggleAlwaysBlockPopups: Boolean = false,
    val isAlwaysBlockPopupsEnabled: Boolean = false,
    val canToggleDesktopView: Boolean = false,
    val isDesktopView: Boolean = false,
    val canToggleDomainMute: Boolean = false,
    val isDomainMuted: Boolean = false,
    val canAddSiteCapsule: Boolean = false,
    val canSnooze: Boolean = false,
    val canDockAddressBar: Boolean = true,
    val overflowPageActions: List<BrowserFeatureMenuAction> = emptyList(),
    val toppingCommands: List<BrowserToppingMenuCommand> = emptyList(),
)

data class BrowserFeatureMenuCapabilities(
    val supportsFirefoxExtensions: Boolean = false,
)

object BrowserFeatureMenuRules {
    fun items(
        state: BrowserFeatureMenuState,
        capabilities: BrowserFeatureMenuCapabilities = BrowserFeatureMenuCapabilities(),
    ): List<BrowserFeatureMenuItem> = buildList {
        toolbarItems(state).forEach(::add)
        pageItems(state).forEach(::add)
        toppingItems(state).forEach(::add)
        candyItems(state).forEach(::add)
        browserItems(state, capabilities).forEach(::add)
    }

    private fun toolbarItems(state: BrowserFeatureMenuState) = listOf(
        command(
            BrowserFeatureMenuAction.Back,
            BrowserFeatureMenuLabelKey.Back,
            BrowserFeatureMenuSection.Toolbar,
            state.canGoBack,
        ),
        command(
            BrowserFeatureMenuAction.Forward,
            BrowserFeatureMenuLabelKey.Forward,
            BrowserFeatureMenuSection.Toolbar,
            state.canGoForward,
        ),
        command(
            if (state.isLoading) BrowserFeatureMenuAction.Stop else BrowserFeatureMenuAction.Reload,
            if (state.isLoading) {
                BrowserFeatureMenuLabelKey.StopLoading
            } else {
                BrowserFeatureMenuLabelKey.Reload
            },
            BrowserFeatureMenuSection.Toolbar,
            state.isLoading || state.hasPage,
        ),
        command(
            BrowserFeatureMenuAction.ToggleFavorite,
            if (state.isFavorite) {
                BrowserFeatureMenuLabelKey.RemoveFavorite
            } else {
                BrowserFeatureMenuLabelKey.AddFavorite
            },
            BrowserFeatureMenuSection.Toolbar,
            state.canToggleFavorite,
            checked = state.isFavorite,
        ),
        command(
            BrowserFeatureMenuAction.TogglePinned,
            if (state.isPinned) {
                BrowserFeatureMenuLabelKey.UnpinTab
            } else {
                BrowserFeatureMenuLabelKey.PinTab
            },
            BrowserFeatureMenuSection.Toolbar,
            true,
            checked = state.isPinned,
        ),
    )

    private fun pageItems(state: BrowserFeatureMenuState) = buildList {
        state.overflowPageActions.forEach { action ->
            when (action) {
                BrowserFeatureMenuAction.ShowTabs ->
                    add(command(action, BrowserFeatureMenuLabelKey.Tabs))
                BrowserFeatureMenuAction.NewTab ->
                    add(command(action, BrowserFeatureMenuLabelKey.NewTab))
                BrowserFeatureMenuAction.CloseTab ->
                    add(command(action, BrowserFeatureMenuLabelKey.CloseTab, enabled = state.canCloseTab))
                BrowserFeatureMenuAction.ParkAddressBarRight ->
                    add(
                        command(
                            action,
                            BrowserFeatureMenuLabelKey.ParkAddressBarRight,
                            enabled = state.canDockAddressBar,
                        ),
                    )
                else -> Unit
            }
        }
        add(
            command(
                BrowserFeatureMenuAction.OpenReader,
                BrowserFeatureMenuLabelKey.Reader,
                enabled = state.canOpenReader,
            ),
        )
        add(
            command(
                BrowserFeatureMenuAction.TranslatePage,
                BrowserFeatureMenuLabelKey.Translate,
                enabled = state.canTranslatePage,
            ),
        )
        add(
            command(
                BrowserFeatureMenuAction.FindInPage,
                BrowserFeatureMenuLabelKey.FindInPage,
                enabled = state.canUseDocumentActions,
            ),
        )
        add(command(BrowserFeatureMenuAction.Share, BrowserFeatureMenuLabelKey.Share, enabled = state.hasPage))
        add(
            command(
                BrowserFeatureMenuAction.OpenExternal,
                BrowserFeatureMenuLabelKey.OpenExternal,
                enabled = state.hasPage,
            ),
        )
        add(
            command(
                BrowserFeatureMenuAction.Print,
                BrowserFeatureMenuLabelKey.Print,
                enabled = state.canUseDocumentActions,
            ),
        )
        if (
            state.canToggleForceVerticalScrolling ||
            state.canToggleForcePageZooming ||
            state.canToggleForceSafeArea
        ) {
            add(
                toggle(
                    BrowserFeatureMenuAction.ToggleCookieBannerRemoval,
                    BrowserFeatureMenuLabelKey.CookieBannerRemoval,
                    state.canToggleCookieBannerRemoval,
                    state.isCookieBannerRemovalEnabled,
                ),
            )
        }
        if (state.canToggleForceVerticalScrolling) {
            add(
                toggle(
                    BrowserFeatureMenuAction.ToggleForceVerticalScrolling,
                    BrowserFeatureMenuLabelKey.ForceVerticalScrolling,
                    state.canToggleForceVerticalScrolling,
                    state.isForceVerticalScrollingEnabled,
                ),
            )
        }
        if (state.canToggleForcePageZooming) {
            add(
                toggle(
                    BrowserFeatureMenuAction.ToggleForcePageZooming,
                    BrowserFeatureMenuLabelKey.ForcePageZooming,
                    state.canToggleForcePageZooming,
                    state.isForcePageZoomingEnabled,
                ),
            )
        }
        if (state.canToggleForceSafeArea) {
            add(
                toggle(
                    BrowserFeatureMenuAction.ToggleForceSafeArea,
                    BrowserFeatureMenuLabelKey.ForceSafeArea,
                    state.canToggleForceSafeArea,
                    state.isForceSafeAreaEnabled,
                ),
            )
        }
        add(
            toggle(
                BrowserFeatureMenuAction.ToggleAlwaysBlockPopups,
                BrowserFeatureMenuLabelKey.AlwaysBlockPopups,
                state.canToggleAlwaysBlockPopups,
                state.isAlwaysBlockPopupsEnabled,
            ),
        )
        add(
            toggle(
                BrowserFeatureMenuAction.ToggleDesktopView,
                BrowserFeatureMenuLabelKey.DesktopView,
                state.canToggleDesktopView,
                state.isDesktopView,
            ),
        )
        add(
            toggle(
                BrowserFeatureMenuAction.ToggleDomainMute,
                if (state.isDomainMuted) {
                    BrowserFeatureMenuLabelKey.UnmuteDomain
                } else {
                    BrowserFeatureMenuLabelKey.MuteDomain
                },
                state.canToggleDomainMute,
                state.isDomainMuted,
            ),
        )
    }

    private fun toppingItems(state: BrowserFeatureMenuState) = state.toppingCommands.map { command ->
        BrowserFeatureMenuItem(
            stableId = "topping:${command.scriptId}:${command.commandId}",
            action = BrowserFeatureMenuAction.InvokeToppingCommand,
            labelKey = BrowserFeatureMenuLabelKey.ToppingsCommand,
            section = BrowserFeatureMenuSection.Toppings,
            kind = BrowserFeatureMenuItemKind.Command,
            enabled = true,
            dynamicLabel = command.caption,
            supportingText = command.scriptName,
            toppingScriptId = command.scriptId,
            toppingCommandId = command.commandId,
        )
    }

    private fun candyItems(state: BrowserFeatureMenuState) = listOf(
        command(
            BrowserFeatureMenuAction.OpenCandyTrail,
            BrowserFeatureMenuLabelKey.CandyTrail,
            BrowserFeatureMenuSection.Candy,
            state.hasPage,
        ),
        command(
            BrowserFeatureMenuAction.AddSiteCapsule,
            BrowserFeatureMenuLabelKey.AddSiteCapsule,
            BrowserFeatureMenuSection.Candy,
            state.canAddSiteCapsule,
        ),
        command(
            BrowserFeatureMenuAction.Summarize,
            BrowserFeatureMenuLabelKey.Summarize,
            BrowserFeatureMenuSection.Candy,
            state.hasPage,
        ),
        command(
            BrowserFeatureMenuAction.SnoozeTab,
            BrowserFeatureMenuLabelKey.SnoozeTab,
            BrowserFeatureMenuSection.Candy,
            state.canSnooze,
        ),
    )

    private fun browserItems(
        state: BrowserFeatureMenuState,
        capabilities: BrowserFeatureMenuCapabilities,
    ) = buildList {
        if (
            state.canDockAddressBar &&
            BrowserFeatureMenuAction.ParkAddressBarRight !in state.overflowPageActions
        ) {
            add(
                command(
                    BrowserFeatureMenuAction.DockAddressBar,
                    BrowserFeatureMenuLabelKey.DockAddressBar,
                    BrowserFeatureMenuSection.Browser,
                ),
            )
        }
        add(
            navigation(
                BrowserFeatureMenuAction.OpenSnoozedTabs,
                BrowserFeatureMenuLabelKey.SnoozedTabs,
            ),
        )
        add(navigation(BrowserFeatureMenuAction.OpenHistory, BrowserFeatureMenuLabelKey.History))
        if (capabilities.supportsFirefoxExtensions) {
            add(
                navigation(
                    BrowserFeatureMenuAction.OpenFirefoxExtensions,
                    BrowserFeatureMenuLabelKey.FirefoxExtensions,
                ),
            )
        }
        add(navigation(BrowserFeatureMenuAction.OpenSettings, BrowserFeatureMenuLabelKey.Settings))
    }

    private fun command(
        action: BrowserFeatureMenuAction,
        labelKey: BrowserFeatureMenuLabelKey,
        section: BrowserFeatureMenuSection = BrowserFeatureMenuSection.Page,
        enabled: Boolean = true,
        checked: Boolean? = null,
    ) = BrowserFeatureMenuItem(
        stableId = action.name,
        action = action,
        labelKey = labelKey,
        section = section,
        kind = BrowserFeatureMenuItemKind.Command,
        enabled = enabled,
        checked = checked,
    )

    private fun toggle(
        action: BrowserFeatureMenuAction,
        labelKey: BrowserFeatureMenuLabelKey,
        enabled: Boolean,
        checked: Boolean,
    ) = BrowserFeatureMenuItem(
        stableId = action.name,
        action = action,
        labelKey = labelKey,
        section = BrowserFeatureMenuSection.Page,
        kind = BrowserFeatureMenuItemKind.Toggle,
        enabled = enabled,
        checked = checked,
    )

    private fun navigation(
        action: BrowserFeatureMenuAction,
        labelKey: BrowserFeatureMenuLabelKey,
    ) = BrowserFeatureMenuItem(
        stableId = action.name,
        action = action,
        labelKey = labelKey,
        section = BrowserFeatureMenuSection.Browser,
        kind = BrowserFeatureMenuItemKind.Navigation,
        enabled = true,
    )
}
