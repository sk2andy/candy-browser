package dev.sk2andy.materialbrowser.shared.browser

enum class BrowserMenuSurface {
    Tab,
    TabSwitcher,
}

enum class BrowserMenuLocation(val stableId: String) {
    Nowhere("nowhere"),
    Tab("tab"),
    TabSwitcher("tab_switcher"),
    Both("both"),
    ;

    companion object {
        fun fromStableId(value: String?): BrowserMenuLocation? =
            entries.firstOrNull { location -> location.stableId == value }
    }
}

enum class BrowserMenuConfigurationSection {
    Toolbar,
    Page,
    Toppings,
    Extensions,
    Candy,
    Browser,
    TabSwitcher,
}

enum class BrowserMenuEntry(
    val stableId: String,
    val section: BrowserMenuConfigurationSection,
    val defaultLocation: BrowserMenuLocation,
    val supportsTab: Boolean,
    val supportsTabSwitcher: Boolean,
) {
    Back("back", BrowserMenuConfigurationSection.Toolbar, BrowserMenuLocation.Tab, true, false),
    Forward("forward", BrowserMenuConfigurationSection.Toolbar, BrowserMenuLocation.Tab, true, false),
    Reload("reload", BrowserMenuConfigurationSection.Toolbar, BrowserMenuLocation.Tab, true, false),
    Favorite("favorite", BrowserMenuConfigurationSection.Toolbar, BrowserMenuLocation.Both, true, true),
    Pin("pin", BrowserMenuConfigurationSection.Toolbar, BrowserMenuLocation.Both, true, true),

    ShowTabs("show_tabs", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Tab, true, false),
    NewTab("new_tab", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Tab, true, false),
    CloseTab("close_tab", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Tab, true, false),
    DuplicateTab("duplicate_tab", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Tab, true, false),
    Reader("reader", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Tab, true, false),
    DiscoverFeeds("discover_feeds", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Tab, true, false),
    OpenFeeds("open_feeds", BrowserMenuConfigurationSection.Browser, BrowserMenuLocation.Tab, true, false),
    OpenLibrary("open_library", BrowserMenuConfigurationSection.Browser, BrowserMenuLocation.Tab, true, false),
    WebAnnotations("web_annotations", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Tab, true, false),
    Translate("translate", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Tab, true, false),
    FindInPage("find_in_page", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Tab, true, false),
    Share("share", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Both, true, true),
    OpenExternal("open_external", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Both, true, true),
    Print("print", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Both, true, true),
    CookieBannerRemoval(
        "cookie_banner_removal",
        BrowserMenuConfigurationSection.Page,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    ForceVerticalScrolling(
        "force_vertical_scrolling",
        BrowserMenuConfigurationSection.Page,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    ForcePageZooming(
        "force_page_zooming",
        BrowserMenuConfigurationSection.Page,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    ForceSafeArea(
        "force_safe_area",
        BrowserMenuConfigurationSection.Page,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    AlwaysBlockPopups(
        "always_block_popups",
        BrowserMenuConfigurationSection.Page,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    DesktopView("desktop_view", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Tab, true, false),
    DomainMute("domain_mute", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Both, true, true),
    StatusBarScroll("status_bar_scroll", BrowserMenuConfigurationSection.Page, BrowserMenuLocation.Tab, true, false),

    ToppingCommands(
        "topping_commands",
        BrowserMenuConfigurationSection.Toppings,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    FirefoxPageActions(
        "firefox_page_actions",
        BrowserMenuConfigurationSection.Extensions,
        BrowserMenuLocation.Both,
        true,
        true,
    ),

    CandyTrail("candy_trail", BrowserMenuConfigurationSection.Candy, BrowserMenuLocation.Both, true, true),
    Constellations("constellations", BrowserMenuConfigurationSection.Candy, BrowserMenuLocation.Tab, true, false),
    AddSiteCapsule(
        "add_site_capsule",
        BrowserMenuConfigurationSection.Candy,
        BrowserMenuLocation.Both,
        true,
        true,
    ),
    CreateSiteWidget(
        "create_site_widget",
        BrowserMenuConfigurationSection.Candy,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    Summarize("summarize", BrowserMenuConfigurationSection.Candy, BrowserMenuLocation.Both, true, true),
    Snooze("snooze", BrowserMenuConfigurationSection.Candy, BrowserMenuLocation.Both, true, true),

    AddressBarDocking(
        "address_bar_docking",
        BrowserMenuConfigurationSection.Browser,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    OpenSnoozedTabs(
        "open_snoozed_tabs",
        BrowserMenuConfigurationSection.Browser,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    OpenFavorites(
        "open_favorites",
        BrowserMenuConfigurationSection.Browser,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    OpenDownloads(
        "open_downloads",
        BrowserMenuConfigurationSection.Browser,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    OpenHistory(
        "open_history",
        BrowserMenuConfigurationSection.Browser,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    OpenFirefoxExtensions(
        "open_firefox_extensions",
        BrowserMenuConfigurationSection.Browser,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),
    OpenSettings(
        "open_settings",
        BrowserMenuConfigurationSection.Browser,
        BrowserMenuLocation.Tab,
        true,
        false,
    ),

    MoveToProfile(
        "move_to_profile",
        BrowserMenuConfigurationSection.TabSwitcher,
        BrowserMenuLocation.TabSwitcher,
        false,
        true,
    ),
    TabStacks(
        "tab_stacks",
        BrowserMenuConfigurationSection.TabSwitcher,
        BrowserMenuLocation.TabSwitcher,
        false,
        true,
    ),
    CloseAllTabs(
        "close_all_tabs",
        BrowserMenuConfigurationSection.TabSwitcher,
        BrowserMenuLocation.TabSwitcher,
        false,
        true,
    ),
    ;

    companion object {
        fun fromStableId(value: String?): BrowserMenuEntry? =
            entries.firstOrNull { entry -> entry.stableId == value }
    }
}

enum class BrowserMenuPresentation(val stableId: String) {
    Floating("floating"),
    BottomSheet("bottom_sheet"),
    ;

    companion object {
        fun fromStableId(value: String?): BrowserMenuPresentation =
            entries.firstOrNull { presentation -> presentation.stableId == value } ?: BottomSheet
    }
}

enum class BrowserMenuBottomSheetContentLayout(val stableId: String) {
    Scroll("scroll"),
    SwipePages("swipe_pages"),
    ;

    companion object {
        fun fromStableId(value: String?): BrowserMenuBottomSheetContentLayout =
            entries.firstOrNull { layout -> layout.stableId == value } ?: Scroll
    }
}

data class BrowserMenuLayout(
    val locations: Map<BrowserMenuEntry, BrowserMenuLocation> = emptyMap(),
    val presentation: BrowserMenuPresentation = BrowserMenuPresentation.BottomSheet,
    val bottomSheetContentLayout: BrowserMenuBottomSheetContentLayout = BrowserMenuBottomSheetContentLayout.Scroll,
    val bottomSheetToolbarEntries: List<BrowserMenuEntry?> = BrowserMenuBottomSheetToolbarRules.DefaultEntries,
) {
    companion object {
        val Default = BrowserMenuLayout()
    }
}

object BrowserMenuLayoutRules {
    private const val PRESENTATION_KEY = "presentation"
    private const val BOTTOM_SHEET_CONTENT_LAYOUT_KEY = "bottom_sheet_content_layout"
    private const val BOTTOM_SHEET_TOOLBAR_ENTRIES_KEY = "bottom_sheet_toolbar_entries"

    fun location(
        layout: BrowserMenuLayout,
        entry: BrowserMenuEntry,
    ): BrowserMenuLocation = layout.locations[entry]
        ?.takeIf { location -> location in allowedLocations(entry) }
        ?: entry.defaultLocation

    fun allowedLocations(entry: BrowserMenuEntry): List<BrowserMenuLocation> = buildList {
        add(BrowserMenuLocation.Nowhere)
        if (entry.supportsTab) add(BrowserMenuLocation.Tab)
        if (entry.supportsTabSwitcher) add(BrowserMenuLocation.TabSwitcher)
        if (entry.supportsTab && entry.supportsTabSwitcher) add(BrowserMenuLocation.Both)
    }

    fun update(
        layout: BrowserMenuLayout,
        entry: BrowserMenuEntry,
        location: BrowserMenuLocation,
    ): BrowserMenuLayout {
        if (location !in allowedLocations(entry)) return normalize(layout)
        return normalize(
            layout.copy(locations = layout.locations + (entry to location)),
        )
    }

    fun normalize(layout: BrowserMenuLayout): BrowserMenuLayout = layout.copy(
        locations = BrowserMenuEntry.entries.associateWith { entry -> location(layout, entry) },
        bottomSheetToolbarEntries = BrowserMenuBottomSheetToolbarRules.normalize(layout.bottomSheetToolbarEntries),
    )

    fun isVisible(
        layout: BrowserMenuLayout,
        entry: BrowserMenuEntry,
        surface: BrowserMenuSurface,
    ): Boolean = when (location(layout, entry)) {
        BrowserMenuLocation.Nowhere -> false
        BrowserMenuLocation.Tab -> surface == BrowserMenuSurface.Tab
        BrowserMenuLocation.TabSwitcher -> surface == BrowserMenuSurface.TabSwitcher
        BrowserMenuLocation.Both -> true
    }

    fun fromWireValues(values: Map<String, String>): BrowserMenuLayout = normalize(
        BrowserMenuLayout(
            locations = values.mapNotNull { (entryId, locationId) ->
                val entry = BrowserMenuEntry.fromStableId(entryId) ?: return@mapNotNull null
                val location = BrowserMenuLocation.fromStableId(locationId) ?: return@mapNotNull null
                (entry to location).takeIf { location in allowedLocations(entry) }
            }.toMap(),
            presentation = BrowserMenuPresentation.fromStableId(values[PRESENTATION_KEY]),
            bottomSheetContentLayout = BrowserMenuBottomSheetContentLayout.fromStableId(
                values[BOTTOM_SHEET_CONTENT_LAYOUT_KEY],
            ),
            bottomSheetToolbarEntries = BrowserMenuBottomSheetToolbarRules.fromWireValue(
                values[BOTTOM_SHEET_TOOLBAR_ENTRIES_KEY],
            ),
        ),
    )

    fun toWireValues(layout: BrowserMenuLayout): Map<String, String> =
        BrowserMenuEntry.entries.associate { entry ->
            entry.stableId to location(layout, entry).stableId
        } + mapOf(
            PRESENTATION_KEY to layout.presentation.stableId,
            BOTTOM_SHEET_CONTENT_LAYOUT_KEY to layout.bottomSheetContentLayout.stableId,
            BOTTOM_SHEET_TOOLBAR_ENTRIES_KEY to BrowserMenuBottomSheetToolbarRules.toWireValue(
                layout.bottomSheetToolbarEntries,
            ),
        )

    fun entryForAction(action: BrowserFeatureMenuAction): BrowserMenuEntry = when (action) {
        BrowserFeatureMenuAction.Back -> BrowserMenuEntry.Back
        BrowserFeatureMenuAction.Forward -> BrowserMenuEntry.Forward
        BrowserFeatureMenuAction.Reload,
        BrowserFeatureMenuAction.Stop,
        -> BrowserMenuEntry.Reload
        BrowserFeatureMenuAction.ToggleFavorite -> BrowserMenuEntry.Favorite
        BrowserFeatureMenuAction.TogglePinned -> BrowserMenuEntry.Pin
        BrowserFeatureMenuAction.ShowTabs -> BrowserMenuEntry.ShowTabs
        BrowserFeatureMenuAction.NewTab -> BrowserMenuEntry.NewTab
        BrowserFeatureMenuAction.DuplicateTab -> BrowserMenuEntry.DuplicateTab
        BrowserFeatureMenuAction.CloseTab -> BrowserMenuEntry.CloseTab
        BrowserFeatureMenuAction.ParkAddressBarRight,
        BrowserFeatureMenuAction.DockAddressBar,
        -> BrowserMenuEntry.AddressBarDocking
        BrowserFeatureMenuAction.OpenFeeds -> BrowserMenuEntry.OpenFeeds
        BrowserFeatureMenuAction.OpenLibrary -> BrowserMenuEntry.OpenLibrary
        BrowserFeatureMenuAction.DiscoverFeeds -> BrowserMenuEntry.DiscoverFeeds
        BrowserFeatureMenuAction.OpenReader -> BrowserMenuEntry.Reader
        BrowserFeatureMenuAction.TranslatePage -> BrowserMenuEntry.Translate
        BrowserFeatureMenuAction.FindInPage -> BrowserMenuEntry.FindInPage
        BrowserFeatureMenuAction.Share -> BrowserMenuEntry.Share
        BrowserFeatureMenuAction.OpenExternal -> BrowserMenuEntry.OpenExternal
        BrowserFeatureMenuAction.Print -> BrowserMenuEntry.Print
        BrowserFeatureMenuAction.ToggleCookieBannerRemoval -> BrowserMenuEntry.CookieBannerRemoval
        BrowserFeatureMenuAction.ToggleForceVerticalScrolling -> BrowserMenuEntry.ForceVerticalScrolling
        BrowserFeatureMenuAction.ToggleForcePageZooming -> BrowserMenuEntry.ForcePageZooming
        BrowserFeatureMenuAction.ToggleForceSafeArea -> BrowserMenuEntry.ForceSafeArea
        BrowserFeatureMenuAction.ToggleAlwaysBlockPopups -> BrowserMenuEntry.AlwaysBlockPopups
        BrowserFeatureMenuAction.ToggleDesktopView -> BrowserMenuEntry.DesktopView
        BrowserFeatureMenuAction.ToggleDomainMute -> BrowserMenuEntry.DomainMute
        BrowserFeatureMenuAction.ToggleStatusBarScroll -> BrowserMenuEntry.StatusBarScroll
        BrowserFeatureMenuAction.OpenCandyTrail -> BrowserMenuEntry.CandyTrail
        BrowserFeatureMenuAction.OpenConstellations -> BrowserMenuEntry.Constellations
        BrowserFeatureMenuAction.OpenWebAnnotations -> BrowserMenuEntry.WebAnnotations
        BrowserFeatureMenuAction.AddSiteCapsule -> BrowserMenuEntry.AddSiteCapsule
        BrowserFeatureMenuAction.CreateSiteWidget -> BrowserMenuEntry.CreateSiteWidget
        BrowserFeatureMenuAction.Summarize -> BrowserMenuEntry.Summarize
        BrowserFeatureMenuAction.SnoozeTab -> BrowserMenuEntry.Snooze
        BrowserFeatureMenuAction.OpenSnoozedTabs -> BrowserMenuEntry.OpenSnoozedTabs
        BrowserFeatureMenuAction.OpenFavorites -> BrowserMenuEntry.OpenFavorites
        BrowserFeatureMenuAction.OpenDownloads -> BrowserMenuEntry.OpenDownloads
        BrowserFeatureMenuAction.OpenHistory -> BrowserMenuEntry.OpenHistory
        BrowserFeatureMenuAction.OpenSettings -> BrowserMenuEntry.OpenSettings
        BrowserFeatureMenuAction.OpenFirefoxExtensions -> BrowserMenuEntry.OpenFirefoxExtensions
        BrowserFeatureMenuAction.InvokeToppingCommand -> BrowserMenuEntry.ToppingCommands
    }

    fun visibleItems(
        items: List<BrowserFeatureMenuItem>,
        layout: BrowserMenuLayout,
        surface: BrowserMenuSurface,
    ): List<BrowserFeatureMenuItem> = items.filter { item ->
        isVisible(layout, entryForAction(item.action), surface)
    }
}
