package dev.sk2andy.materialbrowser.browser

data class TabStack(
    val id: String,
    val profileId: String,
    val name: String,
    val color: TabStackColor,
    val tabIds: List<String>,
    val previewTabId: String? = null,
    val collapsedAnchorTabId: String? = null,
    val isCollapsed: Boolean = false,
)

enum class TabStackColor(val wireValue: String) {
    Grape("grape"),
    Cherry("cherry"),
    Lime("lime"),
    Blueberry("blueberry"),
    ;

    companion object {
        fun fromWireValue(value: String?): TabStackColor =
            entries.firstOrNull { it.wireValue == value } ?: Grape
    }
}
