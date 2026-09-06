package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyLightColors
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSOperatingSystemVersion
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIGlassEffectStyle
import platform.UIKit.UIVisualEffect
import platform.UIKit.UIVisualEffectView
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIColor

/**
 * UIKit owns Apple glass material; shared Compose keeps chrome anatomy, input and morph geometry.
 */
@OptIn(ExperimentalForeignApi::class)
object IosLiquidGlassChromeEffects : CandyBrowserChromeEffects {
    override val mainMenu: BrowserMainMenuEffects = IosLiquidGlassMainMenuEffects
    override val metrics = CandyBrowserChromeMetrics(
        addressCornerRadius = 28.dp,
        addressMinHeight = 56.dp,
        addressHorizontalPadding = 6.dp,
        addressVerticalPadding = 6.dp,
        actionSpacing = 4.dp,
        actionSize = 44.dp,
        tabSwitcherWidth = 44.dp,
        tabSwitcherHeight = 44.dp,
        tabSwitcherCornerRadius = 13.dp,
        addressFieldCornerRadius = 22.dp,
    )

    @Composable
    override fun browserTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = IosCandyLightColors,
            shapes = IosCandyShapes,
            typography = IosCandyTypography,
            content = content,
        )
    }

    override fun reducesMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()

    @Composable
    override fun addressChromeSurface(
        modifier: Modifier,
        shape: Shape,
        color: Color,
        morphProgress: Float,
        content: @Composable () -> Unit,
    ) {
        IosLiquidGlassSurface(
            modifier = modifier,
            shape = shape,
            morphProgress = morphProgress,
            content = content,
        )
    }

    @Composable
    override fun addressFieldSurface(
        modifier: Modifier,
        shape: Shape,
        color: Color,
        morphProgress: Float,
        content: @Composable () -> Unit,
    ) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(color.copy(alpha = ADDRESS_FIELD_TINT_ALPHA)),
            content = { content() },
        )
    }

    @Composable
    override fun tabSwitcherSurface(
        modifier: Modifier,
        shape: Shape,
        content: @Composable () -> Unit,
    ) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(Color.Black.copy(alpha = IOS_CONTROL_FILL_ALPHA))
                .border(1.dp, Color.White.copy(alpha = IOS_CONTROL_BORDER_ALPHA), shape),
            content = { content() },
        )
    }

    override fun usesHorizontalOverflowIcon(): Boolean = true
}

@OptIn(ExperimentalForeignApi::class)
private object IosLiquidGlassMainMenuEffects : BrowserMainMenuEffects {
    override val style = BrowserMainMenuStyle(
        showHeader = false,
        menuCornerRadius = 28.dp,
        groupCornerRadius = 14.dp,
        groupInnerCornerRadius = 0.dp,
        contentHorizontalPadding = 12.dp,
        contentVerticalPadding = 12.dp,
        toolbarSpacing = 6.dp,
        toolbarMinHeight = 52.dp,
        toolbarIconSize = 20.dp,
        groupItemSpacing = 1.dp,
        rowMinHeight = 48.dp,
        rowHorizontalPadding = 16.dp,
        rowVerticalPadding = 5.dp,
        toggleTrackColor = IOS_SYSTEM_GREEN,
    )

    @Composable
    override fun menuSurface(
        modifier: Modifier,
        shape: Shape,
        content: @Composable () -> Unit,
    ) {
        IosLiquidGlassSurface(
            modifier = modifier,
            shape = shape,
            content = content,
        )
    }

    @Composable
    override fun containerColor(
        color: Color,
        frostedAlpha: Float,
        role: BrowserMainMenuContainerRole,
    ): Color = when (role) {
        BrowserMainMenuContainerRole.Selected ->
            IOS_SYSTEM_BLUE.copy(alpha = IOS_SELECTED_CONTROL_ALPHA)
        BrowserMainMenuContainerRole.Branded ->
            IOS_CANDY_TINT.copy(alpha = IOS_BRANDED_CONTROL_ALPHA)
        BrowserMainMenuContainerRole.Regular ->
            Color.White.copy(alpha = frostedAlpha * MENU_ITEM_TINT_MULTIPLIER)
    }

    @Composable
    override fun sectionTitleColor(color: Color): Color = IOS_SECONDARY_LABEL

    override fun preservesVisualEffectDuringMorph(): Boolean = true

    override fun maxHeightFraction(): Float = IOS_MENU_MAX_HEIGHT_FRACTION
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun IosLiquidGlassSurface(
    modifier: Modifier,
    shape: Shape,
    morphProgress: Float = 0f,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.clip(shape)) {
        if (morphProgress < 1f) {
            UIKitView(
                factory = { UIVisualEffectView(effect = iosGlassEffect()) },
                modifier = Modifier.matchParentSize(),
                interactive = false,
                accessibilityEnabled = false,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = GLASS_HIGHLIGHT_ALPHA))
                    .border(1.dp, Color.White.copy(alpha = GLASS_BORDER_ALPHA), shape),
            )
        }
        content()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun iosGlassEffect(): UIVisualEffect = if (iosLiquidGlassAvailable()) {
    UIGlassEffect.effectWithStyle(
        UIGlassEffectStyle.UIGlassEffectStyleRegular,
    ).apply {
        interactive = true
        tintColor = UIColor.colorWithRed(
            red = 0.88,
            green = 0.91,
            blue = 1.0,
            alpha = GLASS_TINT_ALPHA.toDouble(),
        )
    }
} else {
    legacyGlassEffect()
}

@OptIn(ExperimentalForeignApi::class)
private fun legacyGlassEffect(): UIVisualEffect = UIBlurEffect.effectWithStyle(
    UIBlurEffectStyle.UIBlurEffectStyleSystemUltraThinMaterial,
)

@OptIn(ExperimentalForeignApi::class)
internal fun iosLiquidGlassAvailable(): Boolean =
    NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
        cValue<NSOperatingSystemVersion> {
            majorVersion = IOS_LIQUID_GLASS_VERSION.toLong()
            minorVersion = 0
            patchVersion = 0
        },
    )

private const val IOS_LIQUID_GLASS_VERSION = 26
private const val IOS_MENU_MAX_HEIGHT_FRACTION = 0.78f
private const val ADDRESS_FIELD_TINT_ALPHA = 0.16f
private const val MENU_ITEM_TINT_MULTIPLIER = 0.34f
private const val GLASS_TINT_ALPHA = 0.07f
private const val GLASS_HIGHLIGHT_ALPHA = 0.06f
private const val GLASS_BORDER_ALPHA = 0.46f
private const val IOS_CONTROL_FILL_ALPHA = 0.06f
private const val IOS_CONTROL_BORDER_ALPHA = 0.42f
private const val IOS_SELECTED_CONTROL_ALPHA = 0.16f
private const val IOS_BRANDED_CONTROL_ALPHA = 0.10f

private val IOS_SYSTEM_BLUE = Color(0xFF007AFF)
private val IOS_SYSTEM_GREEN = Color(0xFF34C759)
private val IOS_CANDY_TINT = Color(0xFFAF52DE)
private val IOS_SECONDARY_LABEL = Color(0xFF6E6E73)

private val IosCandyLightColors = CandyLightColors.copy(
    primary = IOS_SYSTEM_BLUE,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFF),
    onPrimaryContainer = Color(0xFF003E7A),
    secondary = Color(0xFF5856D6),
    tertiary = IOS_CANDY_TINT,
    tertiaryContainer = Color(0xFFF4E9FA),
    onTertiaryContainer = Color(0xFF4A1E5C),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = IOS_SECONDARY_LABEL,
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF2F2F7),
    surfaceContainerHighest = Color(0xFFE5E5EA),
)

private val IosCandyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val IosCandyTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 13.sp),
)
