package dev.sk2andy.materialbrowser.shared.browser

object BrowserMenuBottomSheetToolbarRules {
    const val SlotCount = 5

    val DefaultEntries = listOf(
        BrowserMenuEntry.Back,
        BrowserMenuEntry.Forward,
        BrowserMenuEntry.Reload,
        BrowserMenuEntry.Favorite,
        BrowserMenuEntry.Pin,
    )

    fun availableEntries(): List<BrowserMenuEntry> = BrowserMenuEntry.entries.filter { entry ->
        entry.supportsTab &&
            entry != BrowserMenuEntry.ToppingCommands &&
            entry != BrowserMenuEntry.FirefoxPageActions
    }

    fun additionalPageActions(entries: List<BrowserMenuEntry?>): List<BrowserFeatureMenuAction> =
        normalize(entries).mapNotNull { entry ->
            when (entry) {
                BrowserMenuEntry.ShowTabs -> BrowserFeatureMenuAction.ShowTabs
                BrowserMenuEntry.NewTab -> BrowserFeatureMenuAction.NewTab
                BrowserMenuEntry.CloseTab -> BrowserFeatureMenuAction.CloseTab
                else -> null
            }
        }

    fun normalize(entries: List<BrowserMenuEntry?>): List<BrowserMenuEntry?> {
        val available = availableEntries()
        val used = mutableSetOf<BrowserMenuEntry>()
        return List(SlotCount) { index ->
            entries.getOrNull(index)?.takeIf { entry -> entry in available && used.add(entry) }
        }
    }

    fun update(
        layout: BrowserMenuLayout,
        slotIndex: Int,
        entry: BrowserMenuEntry?,
    ): BrowserMenuLayout {
        val normalized = BrowserMenuLayoutRules.normalize(layout)
        if (slotIndex !in 0 until SlotCount || entry != null && entry !in availableEntries()) {
            return normalized
        }
        val entries = normalized.bottomSheetToolbarEntries.toMutableList()
        val previous = entries[slotIndex]
        val otherIndex = if (entry == null) -1 else entries.indexOf(entry)
        entries[slotIndex] = entry
        if (otherIndex >= 0 && otherIndex != slotIndex) entries[otherIndex] = previous
        return normalized.copy(bottomSheetToolbarEntries = entries.toList())
    }

    fun fromWireValue(value: String?): List<BrowserMenuEntry?> = if (value == null) {
        DefaultEntries
    } else {
        normalize(
            value.split(',', limit = SlotCount + 1).take(SlotCount).map(BrowserMenuEntry::fromStableId),
        )
    }

    fun assign(
        layout: BrowserMenuLayout,
        slotIndex: Int,
        entry: BrowserMenuEntry?,
    ): BrowserMenuLayout {
        val updated = update(layout, slotIndex, entry)
        if (slotIndex !in 0 until SlotCount || entry == null || entry !in availableEntries()) return updated
        val location = when (BrowserMenuLayoutRules.location(updated, entry)) {
            BrowserMenuLocation.Nowhere -> BrowserMenuLocation.Tab
            BrowserMenuLocation.TabSwitcher -> BrowserMenuLocation.Both
            BrowserMenuLocation.Tab -> BrowserMenuLocation.Tab
            BrowserMenuLocation.Both -> BrowserMenuLocation.Both
        }
        return BrowserMenuLayoutRules.update(updated, entry, location)
    }

    fun toWireValue(entries: List<BrowserMenuEntry?>): String =
        normalize(entries).joinToString(",") { entry -> entry?.stableId.orEmpty() }
}
