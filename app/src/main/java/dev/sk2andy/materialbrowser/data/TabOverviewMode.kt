package dev.sk2andy.materialbrowser.data

enum class TabOverviewMode(val wireValue: String) {
    Hero("hero"),
    Grid("grid"),
    List("list"),
    ;

    companion object {
        fun fromWireValue(
            value: String?,
            fallback: TabOverviewMode = Hero,
        ): TabOverviewMode = entries.firstOrNull { it.wireValue == value } ?: fallback
    }
}
