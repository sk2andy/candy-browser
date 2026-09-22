package dev.sk2andy.materialbrowser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.data.AddressBarColorRules
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAddressBarColorPreset
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.ui.CandyChromeSurfaceRenderer
import dev.sk2andy.materialbrowser.ui.LocalCandyChromeSurfaceRenderer
import dev.sk2andy.materialbrowser.ui.androidCandyChromeSurfaceRenderer
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyDarkColors
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyLightColors
import dev.sk2andy.materialbrowser.shared.ui.theme.NeutralDarkColors
import dev.sk2andy.materialbrowser.shared.ui.theme.NeutralLightColors

private val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }

@Composable
internal fun CandyTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    designLanguage: CandyDesignLanguage = CandyDesignLanguage.MaterialExpressive,
    chromeSurfaceRenderer: CandyChromeSurfaceRenderer =
        androidCandyChromeSurfaceRenderer(designLanguage),
    content: @Composable () -> Unit,
) {
    val effectMotionDurationScale = rememberCoroutineScope()
        .coroutineContext[MotionDurationScale]
    val context = LocalContext.current
    val activity = context.findCandyActivity()
    SideEffect {
        (effectMotionDurationScale as? CandyMotionDurationScale)
            ?.updateAnimationsEnabled(settings.animationsEnabled)
        activity?.let { CandyActivityMotionPolicy.apply(it, settings.animationsEnabled) }
    }
    val systemDark = isSystemInDarkTheme()
    val dark = settings.usesDarkColors(systemDark)
    val baseColors = when (settings.colorPalette) {
        BrowserColorPalette.Dynamic ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        BrowserColorPalette.Candy -> if (dark) CandyDarkColors else CandyLightColors
        BrowserColorPalette.Neutral -> if (dark) NeutralDarkColors else NeutralLightColors
    }
    val appearanceColors = if (settings.appearanceMode == BrowserAppearanceMode.Amoled) {
        baseColors.withAmoledSurfaces()
    } else {
        baseColors
    }
    val colorScheme = appearanceColors.withSurfaceStyle(settings.surfaceStyle)

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = browserShapes(settings.shapeStyle),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onSurface,
            LocalAppearanceSettings provides settings,
            LocalCandyDesignLanguage provides designLanguage,
            LocalCandyMotionScheme provides CandyMotionSchemes.forDesignLanguage(designLanguage),
            LocalCandyChromeSurfaceRenderer provides chromeSurfaceRenderer,
            content = content,
        )
    }
}

@Composable
fun MaterialBrowserTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    CandyTheme(
        settings = settings,
        designLanguage = CandyDesignLanguage.MaterialExpressive,
        content = content,
    )
}

internal fun browserShapes(style: BrowserShapeStyle): Shapes = when (style) {
    BrowserShapeStyle.Angular -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )
    BrowserShapeStyle.Rounded -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
    BrowserShapeStyle.ExtraRounded -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )
}

@Composable
internal fun browserChromeColor(
    color: Color,
    frostedAlpha: Float = 0.82f,
    role: BrowserChromeSurfaceRole = BrowserChromeSurfaceRole.General,
): Color {
    val settings = LocalAppearanceSettings.current
    return if (
        settings.surfaceStyle == BrowserSurfaceStyle.Frosted &&
        settings.appearanceMode != BrowserAppearanceMode.Amoled
    ) {
        val defaultOpacity = 1f -
            AppearanceSettings.DEFAULT_FROSTED_TRANSPARENCY_PERCENT / 100f
        val normalizedSettings = settings.normalized()
        val transparencyPercent = when (role) {
            BrowserChromeSurfaceRole.General -> normalizedSettings.frostedTransparencyPercent
            BrowserChromeSurfaceRole.AddressBar ->
                normalizedSettings.frostedAddressBarTransparencyPercent
        }
        val selectedOpacity = 1f - transparencyPercent / 100f
        color.copy(
            alpha = (frostedAlpha * selectedOpacity / defaultOpacity).coerceIn(0f, 1f),
        )
    } else {
        color
    }
}

internal enum class BrowserChromeSurfaceRole {
    General,
    AddressBar,
}

internal data class BrowserChromeSurfaceTokens(
    val treatment: CandyChromeTreatment,
    val containerColor: Color,
    val contentColor: Color,
    val secondaryContentColor: Color,
    val outlineColor: Color,
    val outlineVariantColor: Color,
    val accentColor: Color,
    val onAccentColor: Color,
    val fieldContainerColor: Color,
    val fieldContentColor: Color,
    val fieldSecondaryContentColor: Color,
    val tonalElevation: Dp,
    val shadowElevation: Dp,
    val blurRadiusPx: Float,
    val backdropBlurEnabled: Boolean,
    val cornerRadius: Dp,
    val largeCornerRadius: Dp,
)

@Composable
internal fun browserChromeSurfaceTokens(
    role: BrowserChromeSurfaceRole = BrowserChromeSurfaceRole.General,
): BrowserChromeSurfaceTokens {
    val settings = LocalAppearanceSettings.current
    val colors = MaterialTheme.colorScheme
    val frostedTransparencyPercent = when (role) {
        BrowserChromeSurfaceRole.General -> settings.frostedTransparencyPercent
        BrowserChromeSurfaceRole.AddressBar -> settings.frostedAddressBarTransparencyPercent
    }
    val specification = BrowserChromeSurfaceRules.resolve(
        designLanguage = LocalCandyDesignLanguage.current,
        surfaceStyle = settings.surfaceStyle,
        appearanceMode = settings.appearanceMode,
        darkColors = colors.surface.luminance() < 0.5f,
        frostedTransparencyPercent = frostedTransparencyPercent,
        frostedBlurPercent = settings.frostedBlurPercent,
    )
    val baseContainerColor = if (
        settings.surfaceStyle == BrowserSurfaceStyle.Frosted &&
        colors.surface.luminance() >= 0.5f
    ) {
        colors.surfaceContainerLowest
    } else {
        colors.surfaceContainerHigh
    }
    val themeContainerColor = lerp(
        baseContainerColor,
        colors.primary,
        specification.primaryTintFraction,
    )
    val resolvedColors = if (role == BrowserChromeSurfaceRole.AddressBar) {
        AddressBarColorTokenRules.resolve(
            preset = settings.addressBarColorPreset,
            customColorArgb = AddressBarColorRules.colorArgb(settings.addressBarCustomColorHex),
            themeContainerColor = themeContainerColor,
            themeFieldContainerColor = colors.surfaceContainerLowest,
            themeContentColor = colors.onSurface,
            themeSecondaryContentColor = colors.onSurfaceVariant,
            themeOutlineColor = colors.outline,
            themeOutlineVariantColor = colors.outlineVariant,
            themeAccentColor = colors.primary,
            themeOnAccentColor = colors.onPrimary,
        )
    } else {
        AddressBarResolvedColors(
            containerColor = themeContainerColor,
            contentColor = colors.onSurface,
            secondaryContentColor = colors.onSurfaceVariant,
            outlineColor = colors.outline,
            outlineVariantColor = colors.outlineVariant,
            accentColor = colors.primary,
            onAccentColor = colors.onPrimary,
            fieldContainerColor = colors.surfaceContainerLowest,
            fieldContentColor = colors.onSurface,
            fieldSecondaryContentColor = colors.onSurfaceVariant,
        )
    }
    val fieldContainerColor = when (LocalCandyDesignLanguage.current) {
        CandyDesignLanguage.LiquidGlass -> Color.Transparent
        CandyDesignLanguage.MaterialExpressive -> browserChromeColor(
            color = resolvedColors.fieldContainerColor,
            frostedAlpha = 0.22f,
            role = BrowserChromeSurfaceRole.AddressBar,
        )
    }
    val hasAddressBarColorOverride = role == BrowserChromeSurfaceRole.AddressBar &&
        settings.addressBarColorPreset != BrowserAddressBarColorPreset.Theme &&
        (
            settings.addressBarColorPreset != BrowserAddressBarColorPreset.Custom ||
                AddressBarColorRules.colorArgb(settings.addressBarCustomColorHex) != null
            )
    val containerColor = resolvedColors.containerColor.copy(
        alpha = if (hasAddressBarColorOverride) {
            maxOf(
                specification.containerAlpha,
                AddressBarColorTokenRules.minimumBackdropSafeAlpha(
                    background = resolvedColors.containerColor,
                    content = resolvedColors.contentColor,
                ),
            )
        } else {
            specification.containerAlpha
        },
    )
    val contrastSafeFieldContainerColor = if (
        hasAddressBarColorOverride && fieldContainerColor.alpha > 0f
    ) {
        fieldContainerColor.copy(
            alpha = maxOf(
                fieldContainerColor.alpha,
                AddressBarColorTokenRules.minimumBackdropSafeAlpha(
                    background = resolvedColors.fieldContainerColor,
                    content = resolvedColors.fieldContentColor,
                ),
            ),
        )
    } else {
        fieldContainerColor
    }
    return BrowserChromeSurfaceTokens(
        treatment = specification.treatment,
        containerColor = containerColor,
        contentColor = resolvedColors.contentColor,
        secondaryContentColor = resolvedColors.secondaryContentColor,
        outlineColor = resolvedColors.outlineColor,
        outlineVariantColor = resolvedColors.outlineVariantColor,
        accentColor = resolvedColors.accentColor,
        onAccentColor = resolvedColors.onAccentColor,
        fieldContainerColor = contrastSafeFieldContainerColor,
        fieldContentColor = resolvedColors.fieldContentColor,
        fieldSecondaryContentColor = resolvedColors.fieldSecondaryContentColor,
        tonalElevation = specification.tonalElevationDp.dp,
        shadowElevation = specification.shadowElevationDp.dp,
        blurRadiusPx = specification.blurRadiusPx,
        backdropBlurEnabled = specification.backdropBlurEnabled,
        cornerRadius = BrowserChromeSurfaceRules.cornerRadius(
            designLanguage = LocalCandyDesignLanguage.current,
            shapeStyle = settings.shapeStyle,
        ).dp,
        largeCornerRadius = BrowserChromeSurfaceRules.largeCornerRadius(
            designLanguage = LocalCandyDesignLanguage.current,
            shapeStyle = settings.shapeStyle,
        ).dp,
    )
}

internal data class AddressBarResolvedColors(
    val containerColor: Color,
    val contentColor: Color,
    val secondaryContentColor: Color,
    val outlineColor: Color,
    val outlineVariantColor: Color,
    val accentColor: Color,
    val onAccentColor: Color,
    val fieldContainerColor: Color,
    val fieldContentColor: Color,
    val fieldSecondaryContentColor: Color,
)

internal object AddressBarColorTokenRules {
    fun resolve(
        preset: BrowserAddressBarColorPreset,
        customColorArgb: Long?,
        themeContainerColor: Color,
        themeFieldContainerColor: Color,
        themeContentColor: Color,
        themeSecondaryContentColor: Color,
        themeOutlineColor: Color,
        themeOutlineVariantColor: Color,
        themeAccentColor: Color,
        themeOnAccentColor: Color,
    ): AddressBarResolvedColors {
        if (preset == BrowserAddressBarColorPreset.Theme) {
            return AddressBarResolvedColors(
                containerColor = themeContainerColor,
                contentColor = themeContentColor,
                secondaryContentColor = themeSecondaryContentColor,
                outlineColor = themeOutlineColor,
                outlineVariantColor = themeOutlineVariantColor,
                accentColor = themeAccentColor,
                onAccentColor = themeOnAccentColor,
                fieldContainerColor = themeFieldContainerColor,
                fieldContentColor = themeContentColor,
                fieldSecondaryContentColor = themeSecondaryContentColor,
            )
        }
        val containerColor = when (preset) {
            BrowserAddressBarColorPreset.Theme -> error("Theme handled above")
            BrowserAddressBarColorPreset.Dimmed -> lerp(
                themeContainerColor.copy(alpha = 1f),
                Color.Black,
                DIMMED_BLACK_FRACTION,
            )
            BrowserAddressBarColorPreset.Graphite -> GRAPHITE
            BrowserAddressBarColorPreset.Black -> Color.Black
            BrowserAddressBarColorPreset.Custom -> customColorArgb?.let(::Color)
                ?: themeContainerColor
        }
        if (preset == BrowserAddressBarColorPreset.Custom && customColorArgb == null) {
            return resolve(
                preset = BrowserAddressBarColorPreset.Theme,
                customColorArgb = null,
                themeContainerColor = themeContainerColor,
                themeFieldContainerColor = themeFieldContainerColor,
                themeContentColor = themeContentColor,
                themeSecondaryContentColor = themeSecondaryContentColor,
                themeOutlineColor = themeOutlineColor,
                themeOutlineVariantColor = themeOutlineVariantColor,
                themeAccentColor = themeAccentColor,
                themeOnAccentColor = themeOnAccentColor,
            )
        }
        val contentColor = highestContrastMonochrome(containerColor)
        val fieldContainerColor = lerp(
            containerColor,
            contentColor,
            FIELD_SEPARATION_FRACTION,
        )
        val fieldContentColor = highestContrastMonochrome(fieldContainerColor)
        return AddressBarResolvedColors(
            containerColor = containerColor,
            contentColor = contentColor,
            secondaryContentColor = contentColor.copy(alpha = SECONDARY_CONTENT_ALPHA),
            outlineColor = contentColor.copy(alpha = OUTLINE_ALPHA),
            outlineVariantColor = contentColor.copy(alpha = OUTLINE_VARIANT_ALPHA),
            accentColor = contentColor,
            onAccentColor = if (contentColor == Color.White) Color.Black else Color.White,
            fieldContainerColor = fieldContainerColor,
            fieldContentColor = fieldContentColor,
            fieldSecondaryContentColor = fieldContentColor.copy(
                alpha = SECONDARY_CONTENT_ALPHA,
            ),
        )
    }

    fun minimumBackdropSafeAlpha(
        background: Color,
        content: Color,
    ): Float {
        fun isSafe(alpha: Float): Boolean {
            val overlay = background.copy(alpha = alpha)
            val overBlack = overlay.compositeOver(Color.Black)
            val overWhite = overlay.compositeOver(Color.White)
            return contrastRatio(overBlack, content) >= MINIMUM_CONTENT_CONTRAST &&
                contrastRatio(overWhite, content) >= MINIMUM_CONTENT_CONTRAST
        }

        if (!isSafe(1f)) return 1f

        var unsafeAlpha = 0f
        var safeAlpha = 1f
        repeat(CONTRAST_SEARCH_STEPS) {
            val candidate = (unsafeAlpha + safeAlpha) / 2f
            if (isSafe(candidate)) {
                safeAlpha = candidate
            } else {
                unsafeAlpha = candidate
            }
        }
        return safeAlpha
    }

    private fun highestContrastMonochrome(background: Color): Color {
        val luminance = background.luminance()
        val blackContrast = (luminance + 0.05f) / 0.05f
        val whiteContrast = 1.05f / (luminance + 0.05f)
        return if (whiteContrast >= blackContrast) Color.White else Color.Black
    }

    private fun contrastRatio(
        background: Color,
        content: Color,
    ): Float {
        val lighter = maxOf(background.luminance(), content.luminance())
        val darker = minOf(background.luminance(), content.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private val GRAPHITE = Color(0xFF303236)
    private const val DIMMED_BLACK_FRACTION = 0.18f
    private const val FIELD_SEPARATION_FRACTION = 0.08f
    private const val SECONDARY_CONTENT_ALPHA = 0.78f
    private const val OUTLINE_ALPHA = 0.62f
    private const val OUTLINE_VARIANT_ALPHA = 0.38f
    private const val MINIMUM_CONTENT_CONTRAST = 4.5f
    private const val CONTRAST_SEARCH_STEPS = 12
}

internal data class BrowserChromeSurfaceSpecification(
    val treatment: CandyChromeTreatment,
    val primaryTintFraction: Float,
    val containerAlpha: Float,
    val tonalElevationDp: Int,
    val shadowElevationDp: Int,
    val blurRadiusPx: Float,
    val backdropBlurEnabled: Boolean,
)

internal object BrowserChromeSurfaceRules {
    fun resolve(
        designLanguage: CandyDesignLanguage = CandyDesignLanguage.MaterialExpressive,
        surfaceStyle: BrowserSurfaceStyle,
        appearanceMode: BrowserAppearanceMode,
        darkColors: Boolean,
        frostedTransparencyPercent: Int,
        frostedBlurPercent: Int,
    ): BrowserChromeSurfaceSpecification = when {
        designLanguage == CandyDesignLanguage.LiquidGlass &&
            appearanceMode != BrowserAppearanceMode.Amoled -> {
            val normalizedTransparency = frostedTransparencyPercent.coerceIn(
                AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT,
                AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT,
            )
            val normalizedBlur = frostedBlurPercent.coerceIn(
                AppearanceSettings.MIN_FROSTED_BLUR_PERCENT,
                AppearanceSettings.MAX_FROSTED_BLUR_PERCENT,
            )
            BrowserChromeSurfaceSpecification(
                treatment = CandyChromeTreatment.PlatformNative,
                primaryTintFraction = if (darkColors) 0.06f else 0.02f,
                containerAlpha = (1f - normalizedTransparency / 100f).coerceAtLeast(0.2f),
                tonalElevationDp = 0,
                shadowElevationDp = 4,
                blurRadiusPx = MAX_FROSTED_BLUR_RADIUS_PX * normalizedBlur / 100f,
                backdropBlurEnabled = true,
            )
        }
        surfaceStyle == BrowserSurfaceStyle.Frosted &&
            appearanceMode != BrowserAppearanceMode.Amoled -> {
            val normalizedTransparency = frostedTransparencyPercent.coerceIn(
                AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT,
                AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT,
            )
            val normalizedBlur = frostedBlurPercent.coerceIn(
                AppearanceSettings.MIN_FROSTED_BLUR_PERCENT,
                AppearanceSettings.MAX_FROSTED_BLUR_PERCENT,
            )
            BrowserChromeSurfaceSpecification(
                treatment = CandyChromeTreatment.Backdrop,
                primaryTintFraction = if (darkColors) 0.08f else 0.025f,
                containerAlpha = 1f - normalizedTransparency / 100f,
                tonalElevationDp = 2,
                shadowElevationDp = 8,
                blurRadiusPx = MAX_FROSTED_BLUR_RADIUS_PX * normalizedBlur / 100f,
                backdropBlurEnabled = normalizedBlur > 0 && normalizedTransparency > 0,
            )
        }
        else -> BrowserChromeSurfaceSpecification(
            treatment = CandyChromeTreatment.Opaque,
            primaryTintFraction = 0f,
            containerAlpha = 1f,
            tonalElevationDp = 12,
            shadowElevationDp = 14,
            blurRadiusPx = 0f,
            backdropBlurEnabled = false,
        )
    }

    fun cornerRadius(
        designLanguage: CandyDesignLanguage,
        shapeStyle: BrowserShapeStyle,
    ): Int = when (designLanguage) {
        CandyDesignLanguage.LiquidGlass -> 28
        CandyDesignLanguage.MaterialExpressive -> when (shapeStyle) {
            BrowserShapeStyle.Angular -> 16
            BrowserShapeStyle.Rounded -> 28
            BrowserShapeStyle.ExtraRounded -> 36
        }
    }

    fun largeCornerRadius(
        designLanguage: CandyDesignLanguage,
        shapeStyle: BrowserShapeStyle,
    ): Int = when (designLanguage) {
        CandyDesignLanguage.LiquidGlass -> 22
        CandyDesignLanguage.MaterialExpressive -> when (shapeStyle) {
            BrowserShapeStyle.Angular -> 12
            BrowserShapeStyle.Rounded -> 20
            BrowserShapeStyle.ExtraRounded -> 28
        }
    }

    private const val MAX_FROSTED_BLUR_RADIUS_PX = 36f
}

private fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF171717),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1B1B1B),
)

private fun ColorScheme.withSurfaceStyle(style: BrowserSurfaceStyle): ColorScheme = when (style) {
    BrowserSurfaceStyle.Clear -> this
    BrowserSurfaceStyle.Frosted -> copy(
        surfaceContainerLowest = lerp(surfaceContainerLowest, primary, 0.03f),
        surfaceContainerLow = lerp(surfaceContainerLow, primary, 0.045f),
        surfaceContainer = lerp(surfaceContainer, primary, 0.06f),
        surfaceContainerHigh = lerp(surfaceContainerHigh, primary, 0.08f),
        surfaceContainerHighest = lerp(surfaceContainerHighest, primary, 0.1f),
        outlineVariant = outlineVariant.copy(alpha = 0.72f),
    )
}
