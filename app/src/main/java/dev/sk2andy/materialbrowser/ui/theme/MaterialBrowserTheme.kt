package dev.sk2andy.materialbrowser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle

private val CandyLightColors = lightColorScheme(
    primary = Color(0xFF6548C5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFFA9004B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF3F0018),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF31101D),
    surface = Color(0xFFFFF8FB),
    surfaceDim = Color(0xFFE1D8DD),
    surfaceBright = Color(0xFFFFF8FB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF0F6),
    surfaceContainer = Color(0xFFF6EDF2),
    surfaceContainerHigh = Color(0xFFF0E7EC),
    surfaceContainerHighest = Color(0xFFEAE1E6),
)

private val CandyDarkColors = darkColorScheme(
    primary = Color(0xFFCFBDFF),
    onPrimary = Color(0xFF35206F),
    primaryContainer = Color(0xFF4C3795),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFFFFB1C8),
    onSecondary = Color(0xFF65002A),
    secondaryContainer = Color(0xFF88003D),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E3),
    surface = Color(0xFF171216),
    surfaceDim = Color(0xFF171216),
    surfaceBright = Color(0xFF3E373C),
    surfaceContainerLowest = Color(0xFF110D10),
    surfaceContainerLow = Color(0xFF20191D),
    surfaceContainer = Color(0xFF251E23),
    surfaceContainerHigh = Color(0xFF30282D),
    surfaceContainerHighest = Color(0xFF3B3338),
)

private val NeutralLightColors = lightColorScheme(
    primary = Color(0xFF4D5D62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E6EC),
    onPrimaryContainer = Color(0xFF081F24),
    secondary = Color(0xFF586064),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE4E7),
    onSecondaryContainer = Color(0xFF171C1E),
    tertiary = Color(0xFF5D5F6B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1E2EF),
    onTertiaryContainer = Color(0xFF191A24),
    surface = Color(0xFFFAF9F7),
    surfaceDim = Color(0xFFDBDAD8),
    surfaceBright = Color(0xFFFAF9F7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F4F2),
    surfaceContainer = Color(0xFFEFEEEC),
    surfaceContainerHigh = Color(0xFFE9E8E6),
    surfaceContainerHighest = Color(0xFFE3E2E0),
    surfaceVariant = Color(0xFFDFE3E4),
    onSurfaceVariant = Color(0xFF41484B),
    outline = Color(0xFF71787B),
    outlineVariant = Color(0xFFC1C7C9),
    inverseSurface = Color(0xFF303132),
    inverseOnSurface = Color(0xFFF1F0EE),
    inversePrimary = Color(0xFFB4CBD1),
)

private val NeutralDarkColors = darkColorScheme(
    primary = Color(0xFFB4CBD1),
    onPrimary = Color(0xFF1F3439),
    primaryContainer = Color(0xFF354A4F),
    onPrimaryContainer = Color(0xFFD0E6EC),
    secondary = Color(0xFFC0C8CB),
    onSecondary = Color(0xFF2A3134),
    secondaryContainer = Color(0xFF41484B),
    onSecondaryContainer = Color(0xFFDCE4E7),
    tertiary = Color(0xFFC5C6D4),
    onTertiary = Color(0xFF2E303B),
    tertiaryContainer = Color(0xFF454751),
    onTertiaryContainer = Color(0xFFE1E2EF),
    surface = Color(0xFF121415),
    surfaceDim = Color(0xFF121415),
    surfaceBright = Color(0xFF383A3B),
    surfaceContainerLowest = Color(0xFF0D0F10),
    surfaceContainerLow = Color(0xFF1A1C1D),
    surfaceContainer = Color(0xFF1E2021),
    surfaceContainerHigh = Color(0xFF292A2B),
    surfaceContainerHighest = Color(0xFF343536),
    surfaceVariant = Color(0xFF41484B),
    onSurfaceVariant = Color(0xFFC1C7C9),
    outline = Color(0xFF8B9295),
    outlineVariant = Color(0xFF41484B),
    inverseSurface = Color(0xFFE3E2E0),
    inverseOnSurface = Color(0xFF2F3031),
    inversePrimary = Color(0xFF4D5D62),
)

private val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }

@Composable
fun MaterialBrowserTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
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
            content = content,
        )
    }
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
    val containerColor: Color,
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
    return BrowserChromeSurfaceTokens(
        containerColor = lerp(
            baseContainerColor,
            colors.primary,
            specification.primaryTintFraction,
        ).copy(alpha = specification.containerAlpha),
        tonalElevation = specification.tonalElevationDp.dp,
        shadowElevation = specification.shadowElevationDp.dp,
        blurRadiusPx = specification.blurRadiusPx,
        backdropBlurEnabled = specification.backdropBlurEnabled,
        cornerRadius = when (settings.shapeStyle) {
            BrowserShapeStyle.Angular -> 16.dp
            BrowserShapeStyle.Rounded -> 28.dp
            BrowserShapeStyle.ExtraRounded -> 36.dp
        },
        largeCornerRadius = when (settings.shapeStyle) {
            BrowserShapeStyle.Angular -> 12.dp
            BrowserShapeStyle.Rounded -> 20.dp
            BrowserShapeStyle.ExtraRounded -> 28.dp
        },
    )
}

internal data class BrowserChromeSurfaceSpecification(
    val primaryTintFraction: Float,
    val containerAlpha: Float,
    val tonalElevationDp: Int,
    val shadowElevationDp: Int,
    val blurRadiusPx: Float,
    val backdropBlurEnabled: Boolean,
)

internal object BrowserChromeSurfaceRules {
    fun resolve(
        surfaceStyle: BrowserSurfaceStyle,
        appearanceMode: BrowserAppearanceMode,
        darkColors: Boolean,
        frostedTransparencyPercent: Int,
        frostedBlurPercent: Int,
    ): BrowserChromeSurfaceSpecification = when {
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
                primaryTintFraction = if (darkColors) 0.08f else 0.025f,
                containerAlpha = 1f - normalizedTransparency / 100f,
                tonalElevationDp = 2,
                shadowElevationDp = 8,
                blurRadiusPx = MAX_FROSTED_BLUR_RADIUS_PX * normalizedBlur / 100f,
                backdropBlurEnabled = normalizedBlur > 0 && normalizedTransparency > 0,
            )
        }
        else -> BrowserChromeSurfaceSpecification(
            primaryTintFraction = 0f,
            containerAlpha = 1f,
            tonalElevationDp = 12,
            shadowElevationDp = 14,
            blurRadiusPx = 0f,
            backdropBlurEnabled = false,
        )
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
