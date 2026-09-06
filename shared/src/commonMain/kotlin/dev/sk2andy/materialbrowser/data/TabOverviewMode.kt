package dev.sk2andy.materialbrowser.data

enum class TabOverviewMode(val wireValue: String) {
    Hero("hero"),
    Grid("grid"),
    List("list"),
    ;

    companion object {
        fun fromWireValue(value: String?): TabOverviewMode =
            entries.firstOrNull { it.wireValue == value } ?: Hero
    }
}
