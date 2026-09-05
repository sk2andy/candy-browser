package dev.sk2andy.materialbrowser.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

internal enum class CandyDesignLanguage {
    MaterialExpressive,
    LiquidGlass,
}

internal enum class CandyChromeTreatment {
    Opaque,
    Backdrop,
    PlatformNative,
}

@Immutable
internal data class CandyMotionScheme(
    val addressBarContainerDampingRatio: Float,
    val addressBarContainerStiffness: Float,
    val addressBarDockDampingRatio: Float,
    val addressBarDockStiffness: Float,
    val addressBarBreakawayDampingRatio: Float,
    val addressBarBreakawayStiffness: Float,
    val addressBarFadeThroughExitMillis: Int,
    val addressBarFadeThroughEnterMillis: Int,
    val addressBarQuickFadeOutMillis: Int,
    val addressBarQuickFadeInMillis: Int,
    val addressBarFadeOutEasing: Easing,
    val addressBarFadeInEasing: Easing,
    val addressBarPulseScale: Float,
    val addressBarPulseOutDampingRatio: Float,
    val addressBarPulseOutStiffness: Float,
    val addressBarPulseBackDampingRatio: Float,
    val addressBarPulseBackStiffness: Float,
    val newTabPulseScale: Float,
    val newTabPulseOutDampingRatio: Float,
    val newTabPulseOutStiffness: Float,
    val newTabPulseBackDampingRatio: Float,
    val newTabPulseBackStiffness: Float,
    val addressBarFeedbackColorMillis: Int,
    val addressBarResistanceDampingRatio: Float,
    val addressBarResistanceStiffness: Float,
    val addressBarResistanceFollowMillis: Int,
    val addressBarActionFadeInMillis: Int,
    val addressBarActionFadeOutMillis: Int,
    val addressBarActionExpandMillis: Int,
    val addressBarToggleColorMillis: Int,
)

internal object CandyMotionSchemes {
    val MaterialExpressive = CandyMotionScheme(
        addressBarContainerDampingRatio = 0.88f,
        addressBarContainerStiffness = 600f,
        addressBarDockDampingRatio = 0.88f,
        addressBarDockStiffness = 600f,
        addressBarBreakawayDampingRatio = 0.46f,
        addressBarBreakawayStiffness = 520f,
        addressBarFadeThroughExitMillis = 80,
        addressBarFadeThroughEnterMillis = 120,
        addressBarQuickFadeOutMillis = 50,
        addressBarQuickFadeInMillis = 70,
        addressBarFadeOutEasing = FastOutLinearInEasing,
        addressBarFadeInEasing = LinearOutSlowInEasing,
        addressBarPulseScale = 1.055f,
        addressBarPulseOutDampingRatio = 0.48f,
        addressBarPulseOutStiffness = 650f,
        addressBarPulseBackDampingRatio = 0.42f,
        addressBarPulseBackStiffness = 520f,
        newTabPulseScale = 1.11f,
        newTabPulseOutDampingRatio = 0.6f,
        newTabPulseOutStiffness = 720f,
        newTabPulseBackDampingRatio = 0.72f,
        newTabPulseBackStiffness = 620f,
        addressBarFeedbackColorMillis = 160,
        addressBarResistanceDampingRatio = 0.42f,
        addressBarResistanceStiffness = 480f,
        addressBarResistanceFollowMillis = 40,
        addressBarActionFadeInMillis = 120,
        addressBarActionFadeOutMillis = 80,
        addressBarActionExpandMillis = 180,
        addressBarToggleColorMillis = 160,
    )

    val LiquidGlass = CandyMotionScheme(
        addressBarContainerDampingRatio = 0.86f,
        addressBarContainerStiffness = 420f,
        addressBarDockDampingRatio = 0.82f,
        addressBarDockStiffness = 360f,
        addressBarBreakawayDampingRatio = 0.7f,
        addressBarBreakawayStiffness = 380f,
        addressBarFadeThroughExitMillis = 100,
        addressBarFadeThroughEnterMillis = 140,
        addressBarQuickFadeOutMillis = 70,
        addressBarQuickFadeInMillis = 100,
        addressBarFadeOutEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f),
        addressBarFadeInEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f),
        addressBarPulseScale = 1.035f,
        addressBarPulseOutDampingRatio = 0.82f,
        addressBarPulseOutStiffness = 420f,
        addressBarPulseBackDampingRatio = 0.88f,
        addressBarPulseBackStiffness = 360f,
        newTabPulseScale = 1.06f,
        newTabPulseOutDampingRatio = 0.82f,
        newTabPulseOutStiffness = 440f,
        newTabPulseBackDampingRatio = 0.88f,
        newTabPulseBackStiffness = 380f,
        addressBarFeedbackColorMillis = 180,
        addressBarResistanceDampingRatio = 0.78f,
        addressBarResistanceStiffness = 360f,
        addressBarResistanceFollowMillis = 70,
        addressBarActionFadeInMillis = 140,
        addressBarActionFadeOutMillis = 100,
        addressBarActionExpandMillis = 220,
        addressBarToggleColorMillis = 180,
    )

    fun forDesignLanguage(designLanguage: CandyDesignLanguage): CandyMotionScheme =
        when (designLanguage) {
            CandyDesignLanguage.MaterialExpressive -> MaterialExpressive
            CandyDesignLanguage.LiquidGlass -> LiquidGlass
        }
}

internal val LocalCandyDesignLanguage = staticCompositionLocalOf {
    CandyDesignLanguage.MaterialExpressive
}

internal val LocalCandyMotionScheme = staticCompositionLocalOf {
    CandyMotionSchemes.MaterialExpressive
}
