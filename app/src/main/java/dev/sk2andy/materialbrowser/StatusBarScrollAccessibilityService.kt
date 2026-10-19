package dev.sk2andy.materialbrowser

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.util.Log
import androidx.core.content.ContextCompat
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.browser.StatusBarScrollRules
import kotlin.math.abs

/** Shows an invisible, centered status-bar hit target only while Candy says its page permits it. */
class StatusBarScrollAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var candyIsForeground = false
    private var currentPageAllowed = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var multiTouchStartY: Float? = null
    private var downwardGestureHandled = false
    private var touchMoved = false
    private var targetPreviewActive = false
    private var targetPreviewFadingOut = false
    private var targetPreviewWidthPercent: Int? = null
    private var renderedTargetPreview = false
    private var previewReceiverRegistered = false
    private var displayListenerRegistered = false
    private val windowManagerDisplayManager by lazy { getSystemService(DisplayManager::class.java) }
    private val previewFadeOut = Runnable {
        targetPreviewFadingOut = true
        overlay?.animate()?.cancel()
        overlay?.animate()?.alpha(0f)?.setDuration(PREVIEW_FADE_DURATION_MS)?.withEndAction {
            targetPreviewActive = false
            targetPreviewFadingOut = false
            targetPreviewWidthPercent = null
            renderedTargetPreview = false
            overlay?.setBackgroundColor(Color.TRANSPARENT)
            refreshOverlay()
        }?.start()
    }
    private val previewReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == StatusBarScrollAccessibilityContract.ACTION_APP_VISIBILITY) {
                candyIsForeground = intent.getBooleanExtra(
                    StatusBarScrollAccessibilityContract.EXTRA_APP_VISIBLE,
                    false,
                )
                refreshOverlay()
                return
            }
            if (intent.action != StatusBarScrollAccessibilityContract.ACTION_PREVIEW_TARGET) return
            targetPreviewWidthPercent = StatusBarScrollRules.targetWidthPercent(
                intent.getIntExtra(
                    StatusBarScrollAccessibilityContract.EXTRA_TARGET_WIDTH_PERCENT,
                    StatusBarScrollRules.DEFAULT_TARGET_WIDTH_PERCENT,
                ),
            )
            targetPreviewActive = true
            targetPreviewFadingOut = false
            stateRefreshHandler.removeCallbacks(previewFadeOut)
            if (intent.getBooleanExtra(StatusBarScrollAccessibilityContract.EXTRA_PREVIEW_FINISHED, false)) {
                stateRefreshHandler.postDelayed(previewFadeOut, PREVIEW_HOLD_DURATION_MS)
            }
            refreshOverlay()
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == android.view.Display.DEFAULT_DISPLAY) refreshOverlay()
        }
    }
    private val stateRefreshHandler = Handler(Looper.getMainLooper())
    private val refreshPageState = Runnable {
        currentPageAllowed = BrowserSessionStore(this).loadStatusBarScrollPageAllowed()
        refreshOverlay()
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        if (!previewReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                previewReceiver,
                IntentFilter().apply {
                    addAction(StatusBarScrollAccessibilityContract.ACTION_PREVIEW_TARGET)
                    addAction(StatusBarScrollAccessibilityContract.ACTION_APP_VISIBILITY)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            previewReceiverRegistered = true
        }
        if (!displayListenerRegistered) {
            windowManagerDisplayManager?.registerDisplayListener(displayListener, stateRefreshHandler)
            displayListenerRegistered = true
        }
        currentPageAllowed = BrowserSessionStore(this).loadStatusBarScrollPageAllowed()
        refreshOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType !in FOREGROUND_EVENT_TYPES) return
        when (event.packageName?.toString()) {
            packageName -> {
                candyIsForeground = true
            }
            SYSTEM_UI_PACKAGE -> Unit // The system panel appears above this target.
            else -> {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    candyIsForeground = false
                }
            }
        }
        // Accessibility can emit a burst of window-change events while system panels animate.
        // Coalesce them so the service's main thread does not accumulate redundant preference
        // reads and WindowManager work behind input dispatch.
        stateRefreshHandler.removeCallbacks(refreshPageState)
        stateRefreshHandler.postDelayed(refreshPageState, PAGE_STATE_REFRESH_DELAY_MS)
    }

    override fun onInterrupt() {
        candyIsForeground = false
        removeOverlay()
    }

    override fun onDestroy() {
        stateRefreshHandler.removeCallbacksAndMessages(null)
        if (previewReceiverRegistered) runCatching { unregisterReceiver(previewReceiver) }
        if (displayListenerRegistered) windowManagerDisplayManager?.unregisterDisplayListener(displayListener)
        previewReceiverRegistered = false
        displayListenerRegistered = false
        removeOverlay()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshOverlay()
    }

    private fun refreshOverlay() {
        val enabled = BrowserSessionStore(this).loadStatusBarScrollEnabled()
        if (!StatusBarScrollRules.shouldShowOverlay(
                enabled = enabled,
                foreground = candyIsForeground,
                pageAllowed = currentPageAllowed,
                previewActive = targetPreviewActive,
            )
        ) {
            removeOverlay()
            return
        }

        val manager = windowManager ?: return
        val targetWidthPercent = targetPreviewWidthPercent
            ?: BrowserSessionStore(this).loadStatusBarScrollTargetWidthPercent()
        val targetWidth = StatusBarScrollRules.targetWidthPx(
            availableScreenWidthPx(),
            targetWidthPercent,
        )
        val statusBarHeight = statusBarHeightPx()
        val existing = overlay
        if (existing != null) {
            val params = existing.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                if (params.width != targetWidth || params.height != statusBarHeight) {
                    params.width = targetWidth
                    params.height = statusBarHeight
                    val updated = runCatching { manager.updateViewLayout(existing, params) }
                        .onFailure { error ->
                            Log.e(TAG, "Could not update status bar target", error)
                            removeOverlay()
                        }
                        .isSuccess
                    if (!updated) return
                }
                updatePreviewAppearance(existing, showPreview = targetPreviewActive && !targetPreviewFadingOut)
                return
            }
            runCatching { manager.removeView(existing) }
            overlay = null
        }

        val target = FrameLayout(this).apply {
            contentDescription = getString(R.string.status_bar_scroll_target_description)
            isClickable = true
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnClickListener {
                sendBroadcast(
                    Intent(StatusBarScrollAccessibilityContract.ACTION_SCROLL_TO_TOP)
                        .setPackage(packageName),
                    StatusBarScrollAccessibilityContract.STATUS_BAR_SCROLL_CONTROL_PERMISSION,
                )
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        multiTouchStartY = null
                        downwardGestureHandled = false
                        touchMoved = false
                        true
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        touchMoved = true
                        if (event.pointerCount >= 2) {
                            multiTouchStartY = (event.getY(0) + event.getY(1)) / 2f
                        }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val touchSlop = ViewConfiguration
                            .get(this@StatusBarScrollAccessibilityService)
                            .scaledTouchSlop
                        val twoFingerDelta = multiTouchStartY?.let { startY ->
                            ((event.getY(0) + event.getY(1)) / 2f) - startY
                        }
                        if (twoFingerDelta != null && abs(twoFingerDelta) > touchSlop) {
                            touchMoved = true
                            if (twoFingerDelta > 0f) {
                                downwardGestureHandled = true
                                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                            }
                        } else {
                            val deltaY = event.rawY - lastTouchY
                            val deltaX = event.rawX - lastTouchX
                            if (abs(deltaY) > touchSlop || abs(deltaX) > touchSlop) {
                                touchMoved = true
                            }
                            if (
                                !downwardGestureHandled &&
                                multiTouchStartY == null &&
                                deltaY > touchSlop &&
                                deltaY > abs(deltaX)
                            ) {
                                downwardGestureHandled = true
                                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!downwardGestureHandled && !touchMoved) view.performClick()
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        touchMoved = true
                        true
                    }

                    else -> true
                }
            }
        }
        val params = WindowManager.LayoutParams(
            targetWidth,
            statusBarHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                setFitInsetsSides(0)
                setFitInsetsTypes(0)
            }
        }
        runCatching { manager.addView(target, params) }
            .onSuccess {
                overlay = target
                updatePreviewAppearance(target, showPreview = targetPreviewActive && !targetPreviewFadingOut)
            }
            .onFailure { error -> Log.e(TAG, "Could not attach status bar target", error) }
    }

    private fun updatePreviewAppearance(target: View, showPreview: Boolean) {
        if (showPreview && !renderedTargetPreview) {
            renderedTargetPreview = true
            target.setBackgroundColor(PREVIEW_COLOR)
            target.alpha = 0f
            target.animate().alpha(1f).setDuration(PREVIEW_FADE_DURATION_MS).start()
        } else if (!showPreview && !targetPreviewFadingOut) {
            renderedTargetPreview = false
            target.animate().cancel()
            target.alpha = 1f
            target.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun availableScreenWidthPx(): Int {
        val display = windowManager?.defaultDisplay
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return windowManager?.maximumWindowMetrics?.bounds?.width()?.coerceAtLeast(1)
                ?: resources.displayMetrics.widthPixels.coerceAtLeast(1)
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)
        return metrics.widthPixels.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels.coerceAtLeast(1)
    }

    private fun removeOverlay() {
        overlay?.let { view ->
            view.animate().cancel()
            runCatching { windowManager?.removeView(view) }
        }
        overlay = null
        renderedTargetPreview = false
    }

    private fun statusBarHeightPx(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId == 0) dp(24) else resources.getDimensionPixelSize(resourceId)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private companion object {
        const val TAG = "StatusBarScroll"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val PAGE_STATE_REFRESH_DELAY_MS = 180L
        const val PREVIEW_HOLD_DURATION_MS = 500L
        const val PREVIEW_FADE_DURATION_MS = 220L
        const val PREVIEW_COLOR = 0x80546EA8.toInt()
        val FOREGROUND_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
        )
    }
}

internal object StatusBarScrollAccessibilityContract {
    const val ACTION_SCROLL_TO_TOP = "dev.sk2andy.materialbrowser.action.SCROLL_TO_TOP_FROM_STATUS_BAR"
    const val ACTION_PREVIEW_TARGET = "dev.sk2andy.materialbrowser.action.PREVIEW_STATUS_BAR_SCROLL_TARGET"
    const val ACTION_APP_VISIBILITY = "dev.sk2andy.materialbrowser.action.STATUS_BAR_SCROLL_APP_VISIBILITY"
    const val EXTRA_TARGET_WIDTH_PERCENT = "target_width_percent"
    const val EXTRA_PREVIEW_FINISHED = "preview_finished"
    const val EXTRA_APP_VISIBLE = "app_visible"
    const val STATUS_BAR_SCROLL_CONTROL_PERMISSION =
        "dev.sk2andy.materialbrowser.permission.STATUS_BAR_SCROLL_CONTROL"
}
