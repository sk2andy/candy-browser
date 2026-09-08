package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import eightbitlab.com.blurview.BlurTarget
import kotlin.math.roundToInt

internal class StatusBarStaticOverlayHost(
    context: Context,
    browserContentBlurEnabled: Boolean = false,
) : FrameLayout(context) {
    val contentContainer: FrameLayout = if (browserContentBlurEnabled) {
        BlurTarget(context)
    } else {
        FrameLayout(context)
    }
    val blurTarget: BlurTarget?
        get() = contentContainer as? BlurTarget
    private val overlayView = StatusBarStaticOverlayView(context).apply {
        tag = StatusBarStaticOverlayTestTags.Overlay
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
    }

    init {
        addView(
            contentContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            overlayView,
            LayoutParams(LayoutParams.MATCH_PARENT, 0),
        )
    }

    fun updateOverlay(
        geometry: StatusBarStaticOverlayGeometry,
        tint: Int,
        visible: Boolean,
    ) {
        val showOverlay = visible && geometry.overlayHeightPx > 0
        overlayView.visibility = if (showOverlay) View.VISIBLE else View.GONE
        if (!showOverlay) return
        overlayView.updateFade(geometry, tint)
        val layoutParams = overlayView.layoutParams
        if (layoutParams.height != geometry.overlayHeightPx) {
            layoutParams.height = geometry.overlayHeightPx
            overlayView.layoutParams = layoutParams
        }
    }
}

private class StatusBarStaticOverlayView(context: Context) : View(context) {
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var geometry = StatusBarStaticOverlayGeometry()
    private var tint = android.graphics.Color.TRANSPARENT
    private var gradientHeight = 0

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    fun updateFade(
        geometry: StatusBarStaticOverlayGeometry,
        tint: Int,
    ) {
        if (this.geometry == geometry && this.tint == tint) return
        this.geometry = geometry
        this.tint = tint
        gradientHeight = 0
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        gradientHeight = 0
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        updateGradients()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tintPaint)
    }

    private fun updateGradients() {
        if (gradientHeight == height) return
        val safeBlurHeight = geometry.statusBarHeightPx.coerceAtLeast(1).toFloat()
        val tintStops = floatArrayOf(
            0f,
            0.55f,
            1f,
        )
        tintPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            safeBlurHeight,
            intArrayOf(
                tint.withAlpha(STATUS_BAR_TINT_ALPHA),
                tint.withAlpha(STATUS_BAR_TINT_MIDPOINT_ALPHA),
                android.graphics.Color.TRANSPARENT,
            ),
            tintStops,
            Shader.TileMode.CLAMP,
        )
        gradientHeight = height
    }
}

internal object StatusBarStaticOverlayRules {
    fun geometry(
        statusBarHeightPx: Int,
        density: Float,
    ): StatusBarStaticOverlayGeometry {
        val safeStatusBarHeight = statusBarHeightPx.coerceAtLeast(0)
        if (safeStatusBarHeight == 0 || !density.isFinite() || density <= 0f) {
            return StatusBarStaticOverlayGeometry()
        }
        val fadeHeightPx = (STATUS_BAR_TRANSPARENT_BUFFER_DP * density).roundToInt()
        return StatusBarStaticOverlayGeometry(
            statusBarHeightPx = safeStatusBarHeight,
            overlayHeightPx = safeStatusBarHeight + fadeHeightPx,
        )
    }
}

internal data class StatusBarStaticOverlayGeometry(
    val statusBarHeightPx: Int = 0,
    val overlayHeightPx: Int = 0,
)

internal object StatusBarStaticOverlayTestTags {
    const val Overlay = "status_bar_static_overlay"
}

private fun Int.withAlpha(alpha: Float): Int =
    (this and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

private const val STATUS_BAR_TRANSPARENT_BUFFER_DP = 8f
private const val STATUS_BAR_TINT_ALPHA = 0.22f
private const val STATUS_BAR_TINT_MIDPOINT_ALPHA = 0.10f
