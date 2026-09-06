package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyLightColors

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

/** Platform visual treatment for shared Candy browser chrome anatomy. */
interface CandyBrowserChromeEffects {
    val mainMenu: BrowserMainMenuEffects
    val metrics: CandyBrowserChromeMetrics
        get() = CandyBrowserChromeMetrics()

    @Composable
    fun browserTheme(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = CandyLightColors, content = content)
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
