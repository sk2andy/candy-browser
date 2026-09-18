package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.browser.BrowserBlurTraceState
import dev.sk2andy.materialbrowser.browser.BrowserPerformanceTrace
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceTokens
import dev.sk2andy.materialbrowser.ui.theme.CandyChromeTreatment
import dev.sk2andy.materialbrowser.ui.theme.CandyDesignLanguage
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

internal object BrowserChromeSurfaceTestTags {
    const val BackdropBlur = "browser_chrome_backdrop_blur"
}

private data class AndroidCandyChromeBackdropSource(
    val blurTarget: BlurTarget,
) : CandyChromeBackdropSource

private class BrowserChromeBlurView(context: Context) : BlurView(context) {
    private var appliedBlurRadiusPx = Float.NaN
    private var traceBlurEnabled = true
    // The library listener updates geometry before drawing; this marker observes the same phase,
    // not the duration of its private listener. Controller snapshot/effect work is in draw below.
    private val tracePreDraw = ViewTreeObserver.OnPreDrawListener {
        BrowserPerformanceTrace.event(BrowserPerformanceTrace.Phase.BlurPreDraw)
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) {
            viewTreeObserver.addOnPreDrawListener(tracePreDraw)
        }
        reportState()
    }

    override fun onDetachedFromWindow() {
        if (BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) {
            viewTreeObserver.removeOnPreDrawListener(tracePreDraw)
        }
        BrowserPerformanceTrace.removeBlurParticipant(this)
        super.onDetachedFromWindow()
    }

    override fun draw(canvas: Canvas) {
        BrowserPerformanceTrace.section(BrowserPerformanceTrace.Phase.BlurDraw) {
            val phase = if (canvas.isHardwareAccelerated) {
                BrowserPerformanceTrace.Phase.BlurHardwareDraw
            } else {
                BrowserPerformanceTrace.Phase.BlurSoftwareDraw
            }
            BrowserPerformanceTrace.section(phase) {
                if (traceBlurEnabled) {
                    super.draw(canvas)
                } else {
                    BrowserPerformanceTrace.section(
                        BrowserPerformanceTrace.Phase.BlurReleasedControllerDraw,
                    ) {
                        super.draw(canvas)
                    }
                }
            }
        }
    }

    fun releaseBackdrop() {
        traceBlurEnabled = false
        reportState()
        setBlurAutoUpdate(false)
    }

    private fun reportState() {
        if (!isAttachedToWindow) return
        BrowserPerformanceTrace.updateBlurParticipant(
            identity = this,
            kind = BrowserBlurTraceState.Kind.View,
            enabled = traceBlurEnabled,
        )
    }

    fun updateAppearance(blurRadiusPx: Float, cornerRadiusPx: Float, overlayColor: Int) {
        BrowserPerformanceTrace.section(BrowserPerformanceTrace.Phase.BlurConfigure) {
            (background as? GradientDrawable)?.let { drawable ->
                if (drawable.cornerRadius != cornerRadiusPx) drawable.cornerRadius = cornerRadiusPx
            }
            if (appliedBlurRadiusPx != blurRadiusPx) {
                setBlurRadius(blurRadiusPx)
                appliedBlurRadiusPx = blurRadiusPx
            }
            setOverlayColor(overlayColor)
        }
    }
}

internal fun BlurTarget?.asCandyChromeBackdropSource(): CandyChromeBackdropSource? =
    this?.let(::AndroidCandyChromeBackdropSource)

internal object AndroidCandyChromeSurfaceRenderer : CandyChromeSurfaceRenderer {
    @Composable
    override fun render(
        backdropSource: CandyChromeBackdropSource?,
        tokens: BrowserChromeSurfaceTokens,
        modifier: Modifier,
        shape: Shape,
        blurCornerRadius: Dp,
        containerColor: Color,
        backdropBlurEnabled: Boolean,
        content: @Composable () -> Unit,
    ) {
        val blurTarget = (backdropSource as? AndroidCandyChromeBackdropSource)?.blurTarget
        val requestsBackdropBlur = tokens.treatment == CandyChromeTreatment.Backdrop && backdropBlurEnabled
        TraceChromeBlurConfiguration(requestsBackdropBlur)
        val drawsBackdropBlur = requestsBackdropBlur && blurTarget != null
        val blurCornerRadiusPx = with(LocalDensity.current) { blurCornerRadius.toPx() }
        Surface(
            modifier = modifier,
            shape = shape,
            color = if (drawsBackdropBlur) Color.Transparent else containerColor,
            contentColor = tokens.contentColor,
            tonalElevation = tokens.tonalElevation,
            shadowElevation = tokens.shadowElevation,
        ) {
            Box {
                if (drawsBackdropBlur) {
                    key(blurTarget) {
                        AndroidView(
                            factory = { context ->
                                BrowserChromeBlurView(context).apply {
                                    importantForAccessibility =
                                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                    isClickable = false
                                    isFocusable = false
                                    background = GradientDrawable().apply {
                                        setColor(android.graphics.Color.TRANSPARENT)
                                        cornerRadius = blurCornerRadiusPx
                                    }
                                    outlineProvider = ViewOutlineProvider.BACKGROUND
                                    clipToOutline = true
                                    BrowserPerformanceTrace.section(
                                        BrowserPerformanceTrace.Phase.BlurBind,
                                    ) {
                                        setupWith(blurTarget, 1f, true)
                                    }
                                }
                            },
                            update = { blurView ->
                                blurView.updateAppearance(
                                    blurRadiusPx = tokens.blurRadiusPx,
                                    cornerRadiusPx = blurCornerRadiusPx,
                                    overlayColor = containerColor.toArgb(),
                                )
                            },
                            onRelease = { blurView ->
                                BrowserPerformanceTrace.section(
                                    BrowserPerformanceTrace.Phase.BlurRelease,
                                ) {
                                    blurView.releaseBackdrop()
                                }
                            },
                            modifier = Modifier
                                .matchParentSize()
                                .clip(shape)
                                .testTag(BrowserChromeSurfaceTestTags.BackdropBlur),
                        )
                    }
                }
                Box(
                    modifier = Modifier.zIndex(1f),
                ) {
                    content()
                }
            }
        }
    }
}

/** Composed chrome records intent even when Clear creates no BlurView/BlurTarget. */
@Composable
private fun TraceChromeBlurConfiguration(requestsBackdropBlur: Boolean) {
    if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) return
    val identity = remember { Any() }
    DisposableEffect(identity) {
        onDispose { BrowserPerformanceTrace.removeBlurParticipant(identity) }
    }
    SideEffect {
        BrowserPerformanceTrace.updateBlurParticipant(
            identity = identity,
            kind = BrowserBlurTraceState.Kind.Configuration,
            enabled = requestsBackdropBlur,
        )
    }
}

internal fun androidCandyChromeSurfaceRenderer(
    designLanguage: CandyDesignLanguage,
): CandyChromeSurfaceRenderer = when (designLanguage) {
    CandyDesignLanguage.MaterialExpressive -> AndroidCandyChromeSurfaceRenderer
    CandyDesignLanguage.LiquidGlass -> error(
        "Liquid Glass requires the iOS CandyChromeSurfaceRenderer.",
    )
}
