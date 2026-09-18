package dev.sk2andy.materialbrowser.shared.browser

enum class AddressBarLongPressActionSection {
    Reading,
    Navigation,
    Page,
    Tab,
    Browser,
    Candy,
}

enum class AddressBarLongPressAction(
    val stableId: String,
    val section: AddressBarLongPressActionSection,
) {
    OpenReader("open_reader", AddressBarLongPressActionSection.Reading),
    SaveReaderOffline("save_reader_offline", AddressBarLongPressActionSection.Reading),

    GoBack("go_back", AddressBarLongPressActionSection.Navigation),
    Reload("reload", AddressBarLongPressActionSection.Navigation),
    OpenHistory("open_history", AddressBarLongPressActionSection.Navigation),

    FindInPage("find_in_page", AddressBarLongPressActionSection.Page),
    TranslatePage("translate_page", AddressBarLongPressActionSection.Page),
    ToggleDesktopView("toggle_desktop_view", AddressBarLongPressActionSection.Page),
    CopyUrl("copy_url", AddressBarLongPressActionSection.Page),
    ShareUrl("share_url", AddressBarLongPressActionSection.Page),
    Print("print", AddressBarLongPressActionSection.Page),
    SendToAssistant("send_to_assistant", AddressBarLongPressActionSection.Page),

    ToggleFavorite("toggle_favorite", AddressBarLongPressActionSection.Tab),
    TogglePinned("toggle_pinned", AddressBarLongPressActionSection.Tab),
    DuplicateTab("duplicate_tab", AddressBarLongPressActionSection.Tab),
    SnoozeTab("snooze_tab", AddressBarLongPressActionSection.Tab),
    MoveToProfile("move_to_profile", AddressBarLongPressActionSection.Tab),

    NewTab("new_tab", AddressBarLongPressActionSection.Browser),
    NewPrivateTab("new_private_tab", AddressBarLongPressActionSection.Browser),
    ParkAddressBar("park_address_bar", AddressBarLongPressActionSection.Browser),

    OpenCandyTrail("open_candy_trail", AddressBarLongPressActionSection.Candy),
    CreateSiteCapsule("create_site_capsule", AddressBarLongPressActionSection.Candy),
    ;

    companion object {
        val Default = OpenReader

        fun fromStableId(value: String?): AddressBarLongPressAction =
            entries.firstOrNull { action -> action.stableId == value } ?: Default
    }
}

data class AddressBarLongPressContext(
    val hasPage: Boolean,
    val hasHttpPage: Boolean,
    val isPrivate: Boolean,
    val canGoBack: Boolean,
    val canOpenReader: Boolean,
    val canToggleFavorite: Boolean,
    val canToggleDesktopView: Boolean,
    val canParkAddressBar: Boolean,
    val canCreateSiteCapsule: Boolean,
    val canCreatePrivateTab: Boolean,
    val canSnoozeTab: Boolean,
    val hasMoveTargetProfile: Boolean,
)

object AddressBarLongPressActionRules {
    fun isAvailable(
        action: AddressBarLongPressAction,
        context: AddressBarLongPressContext,
    ): Boolean = when (action) {
        AddressBarLongPressAction.OpenReader -> context.canOpenReader
        AddressBarLongPressAction.SaveReaderOffline ->
            context.canOpenReader && !context.isPrivate
        AddressBarLongPressAction.FindInPage,
        AddressBarLongPressAction.Reload,
        AddressBarLongPressAction.DuplicateTab,
        AddressBarLongPressAction.OpenCandyTrail,
        -> context.hasPage
        AddressBarLongPressAction.TranslatePage,
        AddressBarLongPressAction.CopyUrl,
        AddressBarLongPressAction.ShareUrl,
        AddressBarLongPressAction.Print,
        AddressBarLongPressAction.SendToAssistant,
        -> context.hasHttpPage
        AddressBarLongPressAction.ToggleDesktopView -> context.canToggleDesktopView
        AddressBarLongPressAction.ToggleFavorite -> context.canToggleFavorite
        AddressBarLongPressAction.TogglePinned,
        AddressBarLongPressAction.NewTab,
        AddressBarLongPressAction.OpenHistory,
        -> true
        AddressBarLongPressAction.NewPrivateTab -> context.canCreatePrivateTab
        AddressBarLongPressAction.GoBack -> context.canGoBack
        AddressBarLongPressAction.SnoozeTab -> context.canSnoozeTab
        AddressBarLongPressAction.MoveToProfile -> context.hasMoveTargetProfile
        AddressBarLongPressAction.ParkAddressBar -> context.canParkAddressBar
        AddressBarLongPressAction.CreateSiteCapsule -> context.canCreateSiteCapsule
    }
}
