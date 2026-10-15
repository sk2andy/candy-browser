package dev.sk2andy.materialbrowser.shared.browser

data class BrowserMenuBottomSheetSections(
    val shortcuts: List<BrowserFeatureMenuItem>,
    val pageActions: List<BrowserFeatureMenuItem>,
    val pageControls: List<BrowserFeatureMenuItem>,
    val footer: List<BrowserFeatureMenuItem>,
    val shortcutSlots: List<BrowserFeatureMenuItem?>,
    val footerSlots: List<BrowserFeatureMenuItem?>,
)

object BrowserMenuBottomSheetRules {
    private val ShortcutActions = listOf(
        BrowserFeatureMenuAction.OpenDownloads,
        BrowserFeatureMenuAction.OpenReader,
        BrowserFeatureMenuAction.OpenFavorites,
        BrowserFeatureMenuAction.OpenHistory,
        BrowserFeatureMenuAction.OpenSettings,
    )

    fun sections(
        items: List<BrowserFeatureMenuItem>,
        toolbarEntries: List<BrowserMenuEntry?> = BrowserMenuBottomSheetToolbarRules.DefaultEntries,
    ): BrowserMenuBottomSheetSections {
        val footerSlots = BrowserMenuBottomSheetToolbarRules.normalize(toolbarEntries).map { entry ->
            entry?.let { items.firstOrNull { item -> BrowserMenuLayoutRules.entryForAction(item.action) == entry } }
        }
        val footer = footerSlots.filterNotNull()
        val shortcutSlots = ShortcutActions.map { action ->
            items.firstOrNull { it.action == action && it !in footer }
        }
        val shortcuts = shortcutSlots.filterNotNull()
        val pageControls = items.filter { it.kind == BrowserFeatureMenuItemKind.Toggle && it !in footer }
        return BrowserMenuBottomSheetSections(
            shortcuts = shortcuts,
            pageActions = items.filterNot { it in shortcuts || it in footer || it in pageControls },
            pageControls = pageControls,
            footer = footer,
            shortcutSlots = shortcutSlots,
            footerSlots = footerSlots,
        )
    }
}
