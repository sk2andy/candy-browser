package dev.sk2andy.materialbrowser.data

enum class AddressBarDockEdge(val wireValue: String) {
    Left("left"),
    Right("right"),
    ;

    companion object {
        fun fromWireValue(value: String?): AddressBarDockEdge =
            entries.firstOrNull { it.wireValue == value } ?: Right
    }
}

data class AddressBarDockPlacement(
    val edge: AddressBarDockEdge,
    val verticalFraction: Float,
) {
    fun normalized(): AddressBarDockPlacement = copy(
        verticalFraction = verticalFraction
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f,
    )

    companion object {
        val Default = AddressBarDockPlacement(
            edge = AddressBarDockEdge.Right,
            verticalFraction = 0f,
        )
    }
}
