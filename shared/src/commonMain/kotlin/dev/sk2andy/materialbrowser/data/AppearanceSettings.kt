package dev.sk2andy.materialbrowser.data

data class AppearanceSettings(
    val appearanceMode: BrowserAppearanceMode = BrowserAppearanceMode.System,
    val forceDarkWebsites: Boolean = false,
    val colorPalette: BrowserColorPalette = BrowserColorPalette.Dynamic,
    val surfaceStyle: BrowserSurfaceStyle = BrowserSurfaceStyle.Clear,
    val shapeStyle: BrowserShapeStyle = BrowserShapeStyle.Rounded,
    val frostedTransparencyPercent: Int = DEFAULT_FROSTED_TRANSPARENCY_PERCENT,
    val frostedAddressBarTransparencyPercent: Int =
        DEFAULT_FROSTED_ADDRESS_BAR_TRANSPARENCY_PERCENT,
    val frostedBlurPercent: Int = DEFAULT_FROSTED_BLUR_PERCENT,
) {
    fun usesDarkColors(systemDark: Boolean): Boolean = when (appearanceMode) {
        BrowserAppearanceMode.System -> systemDark
        BrowserAppearanceMode.Light -> false
        BrowserAppearanceMode.Dark,
        BrowserAppearanceMode.Amoled,
        -> true
    }

    fun normalized(): AppearanceSettings = copy(
        frostedTransparencyPercent = frostedTransparencyPercent.coerceIn(
            MIN_FROSTED_TRANSPARENCY_PERCENT,
            MAX_FROSTED_TRANSPARENCY_PERCENT,
        ),
        frostedAddressBarTransparencyPercent = frostedAddressBarTransparencyPercent.coerceIn(
            MIN_FROSTED_TRANSPARENCY_PERCENT,
            MAX_FROSTED_TRANSPARENCY_PERCENT,
        ),
        frostedBlurPercent = frostedBlurPercent.coerceIn(
            MIN_FROSTED_BLUR_PERCENT,
            MAX_FROSTED_BLUR_PERCENT,
        ),
    )

    companion object {
        const val DEFAULT_FROSTED_TRANSPARENCY_PERCENT = 40
        const val DEFAULT_FROSTED_ADDRESS_BAR_TRANSPARENCY_PERCENT = 40
        const val MIN_FROSTED_TRANSPARENCY_PERCENT = 0
        const val MAX_FROSTED_TRANSPARENCY_PERCENT = 80
        const val DEFAULT_FROSTED_BLUR_PERCENT = 60
        const val MIN_FROSTED_BLUR_PERCENT = 0
        const val MAX_FROSTED_BLUR_PERCENT = 100
    }
}

enum class BrowserAppearanceMode(val stableId: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    Amoled("amoled");

    companion object {
        fun fromStableId(value: String?): BrowserAppearanceMode =
            entries.firstOrNull { it.stableId == value } ?: System
    }
}

enum class BrowserColorPalette(val stableId: String) {
    Dynamic("dynamic"),
    Candy("candy"),
    Neutral("neutral");

    companion object {
        fun fromStableId(value: String?): BrowserColorPalette =
            entries.firstOrNull { it.stableId == value } ?: Dynamic
    }
}

enum class BrowserSurfaceStyle(val stableId: String) {
    Clear("clear"),
    Frosted("frosted");

    companion object {
        fun fromStableId(value: String?): BrowserSurfaceStyle =
            entries.firstOrNull { it.stableId == value } ?: Clear
    }
}

enum class BrowserShapeStyle(val stableId: String) {
    Angular("angular"),
    Rounded("rounded"),
    ExtraRounded("extra_rounded");

    companion object {
        fun fromStableId(value: String?): BrowserShapeStyle =
            entries.firstOrNull { it.stableId == value } ?: Rounded
    }
}
