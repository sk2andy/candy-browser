package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyLightColors
import dev.sk2andy.materialbrowser.data.AppearanceSettings

data class CandyBrowserChromeMetrics(
    val addressCornerRadius: Dp = 34.dp,
    val addressMinHeight: Dp = 64.dp,
    val addressHorizontalPadding: Dp = 8.dp,
    val addressVerticalPadding: Dp = 8.dp,
    val actionSpacing: Dp = 8.dp,
    val actionSize: Dp = 48.dp,
    val tabSwitcherWidth: Dp = 52.dp,
    val tabSwitcherHeight: Dp = 48.dp,
    val tabSwitcherCornerRadius: Dp = 15.dp,
    val addressFieldCornerRadius: Dp = 24.dp,
)

val LocalCandyBrowserChromeEffects = staticCompositionLocalOf<CandyBrowserChromeEffects> {
    DefaultCandyBrowserChromeEffects
}

/** Platform visual treatment for shared Candy browser chrome anatomy. */
interface CandyBrowserChromeEffects {
    val usesGroupedSettings: Boolean
        get() = false

    val mainMenu: BrowserMainMenuEffects
    val tabActionsMenu: TabActionsMenuEffects
        get() = DefaultTabActionsMenuEffects
    val metrics: CandyBrowserChromeMetrics
        get() = CandyBrowserChromeMetrics()

    @Composable
    fun browserTheme(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalCandyBrowserChromeEffects provides this) {
            MaterialTheme(colorScheme = CandyLightColors, content = content)
        }
    }

    @Composable
    fun browserTheme(settings: AppearanceSettings, content: @Composable () -> Unit) {
        browserTheme(content)
    }

    @Composable
    fun icon(
        symbol: String,
        fallback: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
    ) {
        Icon(fallback, contentDescription = contentDescription, modifier = modifier, tint = tint)
    }

    @Composable
    fun settingsToggle(
        checked: Boolean,
        enabled: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }

    @Composable
    fun settingsSlider(
        value: Float,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: (() -> Unit)?,
        enabled: Boolean,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        backgroundColor: Color,
        modifier: Modifier = Modifier,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            modifier = modifier,
            valueRange = valueRange,
            steps = steps,
        )
    }

    @Composable
    fun floatingSurface(
        modifier: Modifier,
        shape: Shape,
        color: Color,
        contentColor: Color,
        tonalElevation: Dp,
        shadowElevation: Dp,
        content: @Composable () -> Unit,
    ) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            content = content,
        )
    }

    fun reducesMotion(): Boolean = false

    @Composable
    fun addressChromeSurface(
        modifier: Modifier,
        shape: Shape,
        color: Color,
        morphProgress: Float,
        content: @Composable () -> Unit,
    )

    @Composable
    fun addressFieldSurface(
        modifier: Modifier,
        shape: Shape,
        color: Color,
        morphProgress: Float,
        content: @Composable () -> Unit,
    )

    @Composable
    fun tabSwitcherSurface(
        modifier: Modifier,
        shape: Shape,
        content: @Composable () -> Unit,
    )

    fun usesHorizontalOverflowIcon(): Boolean = false
}

object DefaultCandyBrowserChromeEffects : CandyBrowserChromeEffects {
    override val mainMenu: BrowserMainMenuEffects = DefaultBrowserMainMenuEffects

    @Composable
    override fun addressChromeSurface(
        modifier: Modifier,
        shape: Shape,
        color: Color,
        morphProgress: Float,
        content: @Composable () -> Unit,
    ) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
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
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            content = content,
        )
    }

    @Composable
    override fun tabSwitcherSurface(
        modifier: Modifier,
        shape: Shape,
        content: @Composable () -> Unit,
    ) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF151515)),
            content = content,
        )
    }
}
