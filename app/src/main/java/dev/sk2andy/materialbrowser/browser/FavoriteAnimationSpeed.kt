package dev.sk2andy.materialbrowser.browser

enum class FavoriteAnimationSpeed(
    val wireValue: String,
    val morphDurationMillis: Int,
    val morphPauseMillis: Int,
) {
    Relaxed(
        wireValue = "relaxed",
        morphDurationMillis = 4_800,
        morphPauseMillis = 1_600,
    ),
    Normal(
        wireValue = "normal",
        morphDurationMillis = 3_600,
        morphPauseMillis = 1_000,
    ),
    Fast(
        wireValue = "fast",
        morphDurationMillis = 2_400,
        morphPauseMillis = 650,
    ),
    ;

    companion object {
        val Default = Normal

        fun fromWireValue(value: String?): FavoriteAnimationSpeed =
            entries.firstOrNull { speed -> speed.wireValue == value } ?: Default
    }
}
