package dev.sk2andy.materialbrowser.data

data class AppearanceSettings(
    val appearanceMode: BrowserAppearanceMode = BrowserAppearanceMode.System,
    val forceDarkWebsites: Boolean = false,
    val webContentFontSizePercent: Int = DEFAULT_WEB_CONTENT_FONT_SIZE_PERCENT,
    val colorPalette: BrowserColorPalette = BrowserColorPalette.Dynamic,
    val surfaceStyle: BrowserSurfaceStyle = BrowserSurfaceStyle.Clear,
    val shapeStyle: BrowserShapeStyle = BrowserShapeStyle.Rounded,
    val addressBarStyle: BrowserAddressBarStyle = BrowserAddressBarStyle.Classic,
    val addressBarColorPreset: BrowserAddressBarColorPreset = BrowserAddressBarColorPreset.Theme,
    val addressBarCustomColorHex: String = "",
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

    fun normalized(): AppearanceSettings {
        val normalizedAddressBarColor = AddressBarColorRules.normalizeHex(
            addressBarCustomColorHex,
        )
        return copy(
            addressBarColorPreset = if (
                addressBarColorPreset == BrowserAddressBarColorPreset.Custom &&
                normalizedAddressBarColor == null
            ) {
                BrowserAddressBarColorPreset.Theme
            } else {
                addressBarColorPreset
            },
            addressBarCustomColorHex = normalizedAddressBarColor.orEmpty(),
            webContentFontSizePercent = webContentFontSizePercent
                .coerceIn(
                    MIN_WEB_CONTENT_FONT_SIZE_PERCENT,
                    MAX_WEB_CONTENT_FONT_SIZE_PERCENT,
                )
                .let { value ->
                    val offset = value - MIN_WEB_CONTENT_FONT_SIZE_PERCENT
                    MIN_WEB_CONTENT_FONT_SIZE_PERCENT +
                        (offset + WEB_CONTENT_FONT_SIZE_STEP_PERCENT / 2) /
                        WEB_CONTENT_FONT_SIZE_STEP_PERCENT *
                        WEB_CONTENT_FONT_SIZE_STEP_PERCENT
                },
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
    }

    companion object {
        const val DEFAULT_WEB_CONTENT_FONT_SIZE_PERCENT = 100
        const val MIN_WEB_CONTENT_FONT_SIZE_PERCENT = 50
        const val MAX_WEB_CONTENT_FONT_SIZE_PERCENT = 200
        const val WEB_CONTENT_FONT_SIZE_STEP_PERCENT = 5
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

enum class BrowserAddressBarStyle(val stableId: String) {
    Classic("classic"),
    Segmented("segmented");

    companion object {
        fun fromStableId(value: String?): BrowserAddressBarStyle =
            entries.firstOrNull { it.stableId == value } ?: Classic
    }
}

enum class BrowserAddressBarColorPreset(val stableId: String) {
    Theme("theme"),
    Dimmed("dimmed"),
    Graphite("graphite"),
    Black("black"),
    Custom("custom");

    companion object {
        fun fromStableId(value: String?): BrowserAddressBarColorPreset =
            entries.firstOrNull { it.stableId == value } ?: Theme
    }
}

object AddressBarColorRules {
    fun normalizeHex(value: String): String? {
        val digits = value.trim().removePrefix("#")
        if (digits.length != SHORT_HEX_LENGTH && digits.length != LONG_HEX_LENGTH) return null
        if (digits.any { it.digitToIntOrNull(16) == null }) return null
        val expanded = if (digits.length == SHORT_HEX_LENGTH) {
            buildString(LONG_HEX_LENGTH) {
                digits.forEach { digit ->
                    append(digit)
                    append(digit)
                }
            }
        } else {
            digits
        }
        return "#${expanded.uppercase()}"
    }

    fun colorArgb(value: String): Long? = normalizeHex(value)
        ?.removePrefix("#")
        ?.toLongOrNull(16)
        ?.or(OPAQUE_ALPHA)

    private const val SHORT_HEX_LENGTH = 3
    private const val LONG_HEX_LENGTH = 6
    private const val OPAQUE_ALPHA = 0xFF000000L
}
