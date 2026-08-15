package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import kotlin.math.roundToInt

internal class StatusBarFrostedGlassHost(context: Context) : FrameLayout(context) {
    val blurTarget = BlurTarget(context)
    private var blurAutoUpdateEnabled = true
    private val blurView = StatusBarBlurView(context).apply {
        tag = StatusBarFrostedGlassTestTags.Overlay
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
        setupWith(blurTarget, 1f, true)
            .setOverlayColor(android.graphics.Color.TRANSPARENT)
    }

    init {
        addView(
            blurTarget,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            blurView,
            LayoutParams(LayoutParams.MATCH_PARENT, 0),
        )
    }

    fun updateFrostedGlass(
        geometry: StatusBarFrostedGlassGeometry,
        tint: Int,
        visible: Boolean,
    ) {
        val showBlur = visible && geometry.overlayHeightPx > 0
        blurView.visibility = if (showBlur) View.VISIBLE else View.GONE
        setBlurAutoUpdate(showBlur)
        if (!showBlur) return
        blurView.updateFade(geometry, tint)
        blurView.setBlurRadius(geometry.blurRadiusPx)
        val layoutParams = blurView.layoutParams
        if (layoutParams.height != geometry.overlayHeightPx) {
            layoutParams.height = geometry.overlayHeightPx
            blurView.layoutParams = layoutParams
        }
    }

    fun release() {
        setBlurAutoUpdate(false)
    }

    private fun setBlurAutoUpdate(enabled: Boolean) {
        if (blurAutoUpdateEnabled == enabled) return
        blurView.setBlurAutoUpdate(enabled)
        blurAutoUpdateEnabled = enabled
    }
}

private class StatusBarBlurView(context: Context) : BlurView(context) {
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        blendMode = BlendMode.DST_IN
    }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var geometry = StatusBarFrostedGlassGeometry()
    private var tint = android.graphics.Color.TRANSPARENT
    private var gradientHeight = 0

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    fun updateFade(
        geometry: StatusBarFrostedGlassGeometry,
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

    override fun draw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        updateGradients()
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.draw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        canvas.restoreToCount(layer)

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
        maskPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            safeBlurHeight,
            android.graphics.Color.WHITE,
            android.graphics.Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        tintPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            safeBlurHeight,
            intArrayOf(
                tint.withAlpha(0.66f),
                tint.withAlpha(0.32f),
                android.graphics.Color.TRANSPARENT,
            ),
            tintStops,
            Shader.TileMode.CLAMP,
        )
        gradientHeight = height
    }
}

internal object StatusBarFrostedGlassRules {
    fun geometry(
        statusBarHeightPx: Int,
        density: Float,
    ): StatusBarFrostedGlassGeometry {
        val safeStatusBarHeight = statusBarHeightPx.coerceAtLeast(0)
        if (safeStatusBarHeight == 0 || !density.isFinite() || density <= 0f) {
            return StatusBarFrostedGlassGeometry()
        }
        val fadeHeightPx = (STATUS_BAR_TRANSPARENT_BUFFER_DP * density).roundToInt()
        return StatusBarFrostedGlassGeometry(
            statusBarHeightPx = safeStatusBarHeight,
            blurRadiusPx = STATUS_BAR_BLUR_RADIUS_PX,
            overlayHeightPx = safeStatusBarHeight + fadeHeightPx,
        )
    }
}

internal data class StatusBarFrostedGlassGeometry(
    val statusBarHeightPx: Int = 0,
    val blurRadiusPx: Float = 0f,
    val overlayHeightPx: Int = 0,
)

internal object StatusBarFrostedGlassTestTags {
    const val Overlay = "status_bar_frosted_glass"
}

private fun Int.withAlpha(alpha: Float): Int =
    (this and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

private const val STATUS_BAR_TRANSPARENT_BUFFER_DP = 8f
private const val STATUS_BAR_BLUR_RADIUS_PX = 14f
