package dev.sk2andy.materialbrowser.shared.topping

enum class ToppingFrameScope(
    val wireValue: String,
    private val permissionRank: Int,
) {
    Top("top", permissionRank = 0),
    SameOrigin("same-origin", permissionRank = 1),
    AllMatching("all-matching", permissionRank = 2),
    ;

    fun restrictedTo(maximum: ToppingFrameScope): ToppingFrameScope =
        if (permissionRank <= maximum.permissionRank) this else maximum

    fun isWithin(maximum: ToppingFrameScope): Boolean = permissionRank <= maximum.permissionRank

    fun allowedChoices(): List<ToppingFrameScope> = entries
        .filter { candidate -> candidate.permissionRank <= permissionRank }
        .sortedBy(ToppingFrameScope::permissionRank)

    companion object {
        fun fromWireValue(value: String?): ToppingFrameScope? =
            entries.firstOrNull { scope -> scope.wireValue == value }
    }
}
