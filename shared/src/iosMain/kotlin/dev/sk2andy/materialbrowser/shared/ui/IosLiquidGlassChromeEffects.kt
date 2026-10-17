@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Slider
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyDarkColors
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyLightColors
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import platform.UIKit.UIAccessibilityReduceTransparencyStatusDidChangeNotification
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIGlassEffectStyle
import platform.UIKit.UISwitch
import platform.UIKit.UISlider
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIVisualEffect
import platform.UIKit.UIVisualEffectView
import platform.UIKit.accessibilityElementsHidden
import platform.UIKit.isAccessibilityElement

/**
 * UIKit owns Apple glass material; shared Compose keeps chrome anatomy, input and morph geometry.
 */
@OptIn(ExperimentalForeignApi::class)
object IosLiquidGlassChromeEffects : CandyBrowserChromeEffects {
    override val usesGroupedSettings = true
    override val mainMenu: BrowserMainMenuEffects = IosLiquidGlassMainMenuEffects
    override val tabActionsMenu: TabActionsMenuEffects = IosLiquidGlassTabActionsMenuEffects
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
        browserTheme(AppearanceSettings(), content)
    }

    @Composable
    override fun browserTheme(settings: AppearanceSettings, content: @Composable () -> Unit) {
        val accessibility = rememberIosAccessibilityPreferences()
        val effects = remember(accessibility, settings.animationsEnabled) {
            IosAccessibleChromeEffects(accessibility, settings.animationsEnabled)
        }
        CompositionLocalProvider(LocalCandyBrowserChromeEffects provides effects) {
            MaterialTheme(
                colorScheme = if (settings.usesDarkColors(isSystemInDarkTheme())) {
                    IosCandyDarkColors
                } else {
                    IosCandyLightColors
                },
                shapes = IosCandyShapes,
                typography = IosCandyTypography,
                content = content,
            )
        }
    }

    override fun reducesMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()

    @Composable
    override fun icon(
        symbol: String,
        fallback: ImageVector,
        contentDescription: String?,
        modifier: Modifier,
        tint: Color,
    ) {
        val image = remember(symbol) { iosSymbolBitmap(symbol) }
        if (image == null) {
            Icon(fallback, contentDescription = contentDescription, modifier = modifier, tint = tint)
            return
        }
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            modifier = modifier.size(22.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }

    @Composable
    override fun settingsToggle(
        checked: Boolean,
        enabled: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        // Shared row owns input and accessibility; UIKit supplies the system switch appearance.
        val reduceMotion = LocalCandyBrowserChromeEffects.current.reducesMotion()
        val background = MaterialTheme.colorScheme.surface
        val size = remember {
            UISwitch().intrinsicContentSize.useContents {
                (width.toFloat().dp + 16.dp) to maxOf(height.toFloat().dp + 8.dp, 44.dp)
            }
        }
        Box(
            modifier = Modifier.size(width = size.first, height = maxOf(44.dp, size.second)),
            contentAlignment = Alignment.Center,
        ) {
            UIKitView(
                factory = {
                    IosSwitchView()
                },
                update = { control ->
                    control.backgroundColor = UIColor.colorWithRed(
                        red = background.red.toDouble(),
                        green = background.green.toDouble(),
                        blue = background.blue.toDouble(),
                        alpha = 1.0,
                    )
                    control.opaque = true
                    control.control.enabled = enabled
                    control.control.setOn(checked, animated = !reduceMotion)
                    control.setNeedsLayout()
                },
                modifier = Modifier.size(width = size.first, height = size.second),
                properties = UIKitInteropProperties(
                    interactionMode = null,
                    isNativeAccessibilityEnabled = false,
                ),
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun settingsSlider(
        value: Float,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: (() -> Unit)?,
        enabled: Boolean,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        backgroundColor: Color,
        modifier: Modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            UIKitView(
                factory = {
                    UISlider().apply {
                        isAccessibilityElement = false
                        accessibilityElementsHidden = true
                        userInteractionEnabled = false
                        opaque = true
                    }
                },
                update = { slider ->
                    slider.backgroundColor = UIColor.colorWithRed(
                        red = backgroundColor.red.toDouble(),
                        green = backgroundColor.green.toDouble(),
                        blue = backgroundColor.blue.toDouble(),
                        alpha = 1.0,
                    )
                    slider.minimumValue = valueRange.start
                    slider.maximumValue = valueRange.endInclusive
                    slider.enabled = enabled
                    slider.setValue(value.coerceIn(valueRange), animated = false)
                },
                modifier = Modifier.fillMaxSize(),
                properties = UIKitInteropProperties(
                    interactionMode = null,
                    isNativeAccessibilityEnabled = false,
                ),
            )
            // Keep shared snapping, pointer input, keyboard input and progress semantics.
            // Transparent slots retain gesture geometry; UIKit owns visible track/thumb rendering.
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                enabled = enabled,
                valueRange = valueRange,
                steps = steps,
                modifier = modifier.fillMaxSize(),
                thumb = { Box(Modifier.size(28.dp)) },
                track = { Box(Modifier.fillMaxWidth().height(4.dp)) },
            )
        }
    }

    @Composable
    override fun floatingSurface(
        modifier: Modifier,
        shape: Shape,
        color: Color,
        contentColor: Color,
        tonalElevation: Dp,
        shadowElevation: Dp,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            IosLiquidGlassSurface(
                modifier = modifier,
                shape = shape,
                opaqueColor = color,
                content = content,
            )
        }
    }

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

private data class IosAccessibilityPreferences(
    val reduceMotion: Boolean,
    val reduceTransparency: Boolean,
)

private class IosAccessibleChromeEffects(
    private val accessibility: IosAccessibilityPreferences,
    private val animationsEnabled: Boolean,
) : CandyBrowserChromeEffects by IosLiquidGlassChromeEffects {
    override fun reducesMotion(): Boolean = !animationsEnabled || accessibility.reduceMotion
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun rememberIosAccessibilityPreferences(): IosAccessibilityPreferences {
    fun readPreferences() = IosAccessibilityPreferences(
        reduceMotion = UIAccessibilityIsReduceMotionEnabled(),
        reduceTransparency = UIAccessibilityIsReduceTransparencyEnabled(),
    )
    var preferences by remember { mutableStateOf(readPreferences()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observers = listOf(
            UIAccessibilityReduceMotionStatusDidChangeNotification,
            UIAccessibilityReduceTransparencyStatusDidChangeNotification,
        ).map { notification ->
            center.addObserverForName(
                name = notification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { preferences = readPreferences() }
        }
        onDispose { observers.forEach(center::removeObserver) }
    }
    return preferences
}

@OptIn(ExperimentalForeignApi::class)
private object IosLiquidGlassTabActionsMenuEffects : TabActionsMenuEffects {
    @Composable
    override fun menuSurface(
        modifier: Modifier,
        shape: Shape,
        content: @Composable () -> Unit,
    ) {
        IosLiquidGlassSurface(modifier = modifier, shape = shape, content = content)
    }

    @Composable
    override fun containerColor(color: Color, frostedAlpha: Float): Color =
        if (UIAccessibilityIsReduceTransparencyEnabled()) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            color.copy(alpha = frostedAlpha * MENU_ITEM_TINT_MULTIPLIER)
        }
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
    override fun sectionTitleColor(color: Color): Color = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    override fun selectedToolbarContentColor(): Color = MaterialTheme.colorScheme.onPrimaryContainer

    @Composable
    override fun selectedToggleContentColor(): Color = MaterialTheme.colorScheme.onPrimaryContainer

    override fun preservesVisualEffectDuringMorph(): Boolean = true

    override fun maxHeightFraction(): Float = IOS_MENU_MAX_HEIGHT_FRACTION
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun IosLiquidGlassSurface(
    modifier: Modifier,
    shape: Shape,
    morphProgress: Float = 0f,
    opaqueColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.clip(shape)) {
        if (UIAccessibilityIsReduceTransparencyEnabled()) {
            Box(modifier = Modifier.matchParentSize().background(opaqueColor))
        } else if (morphProgress < 1f) {
            UIKitView(
                factory = {
                    UIVisualEffectView(effect = iosGlassEffect()).apply {
                        backgroundColor = UIColor.clearColor
                        contentView.backgroundColor = UIColor.clearColor
                        opaque = false
                        isAccessibilityElement = false
                        accessibilityElementsHidden = true
                    }
                },
                update = { view ->
                    view.overrideUserInterfaceStyle = if (opaqueColor.luminance() < 0.5f) {
                        UIUserInterfaceStyle.UIUserInterfaceStyleDark
                    } else {
                        UIUserInterfaceStyle.UIUserInterfaceStyleLight
                    }
                },
                modifier = Modifier.matchParentSize(),
                properties = UIKitInteropProperties(
                    interactionMode = null,
                    isNativeAccessibilityEnabled = false,
                ),
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
    secondaryContainer = Color(0xFFE7E6FF),
    onSecondaryContainer = Color(0xFF36348A),
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
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
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

private val IosCandyDarkColors = CandyDarkColors.copy(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF102F50),
    onPrimaryContainer = Color(0xFFB8DAFF),
    secondary = Color(0xFF5E5CE6),
    secondaryContainer = Color(0xFF292850),
    onSecondaryContainer = Color(0xFFD7D6FF),
    tertiary = Color(0xFFBF5AF2),
    tertiaryContainer = Color(0xFF3B2048),
    onTertiaryContainer = Color(0xFFF1CAFF),
    background = Color.Black,
    onBackground = Color(0xFFF2F2F7),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outline = Color(0xFF636366),
    outlineVariant = Color(0xFF38383A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
)
