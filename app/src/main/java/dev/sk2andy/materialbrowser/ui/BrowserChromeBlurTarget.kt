package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.Canvas
import dev.sk2andy.materialbrowser.browser.BrowserBlurTraceState
import dev.sk2andy.materialbrowser.browser.BrowserPerformanceTrace
import eightbitlab.com.blurview.BlurTarget

/** Measures source recording separately from the BlurView controller's draw work. */
internal class BrowserChromeBlurTarget(context: Context) : BlurTarget(context) {
    // Publication/ownership state, not the user's global Blur setting.
    var captureEnabled: Boolean = false
        set(value) {
            field = value
            if (isAttachedToWindow) reportState()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reportState()
    }

    override fun onDetachedFromWindow() {
        BrowserPerformanceTrace.removeBlurParticipant(this)
        super.onDetachedFromWindow()
    }

    override fun dispatchDraw(canvas: Canvas) {
        // On API 31+ only the hardware branch creates the target's RenderNode snapshot.
        // Software dispatchDraw by itself is ordinary child drawing, not capture or blur.
        val drawPhase = BrowserPerformanceTrace.sourceDrawPhase(canvas.isHardwareAccelerated)
        if (drawPhase == BrowserPerformanceTrace.Phase.BlurSourceChildDraw) {
            BrowserPerformanceTrace.section(drawPhase) {
                super.dispatchDraw(canvas)
            }
            return
        }
        BrowserPerformanceTrace.section(drawPhase) {
            BrowserPerformanceTrace.section(BrowserPerformanceTrace.Phase.BlurHardwareCapture) {
                if (captureEnabled) {
                    super.dispatchDraw(canvas)
                } else {
                    BrowserPerformanceTrace.section(
                        BrowserPerformanceTrace.Phase.BlurCaptureUnpublishedSource,
                    ) {
                        super.dispatchDraw(canvas)
                    }
                }
            }
        }
    }

    private fun reportState() {
        BrowserPerformanceTrace.updateBlurParticipant(
            identity = this,
            kind = BrowserBlurTraceState.Kind.Source,
            enabled = captureEnabled,
        )
    }
}
