package dev.sk2andy.materialbrowser.browser.sitewidget

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreviewCapture
import kotlin.math.ceil

/** The caller owns [bitmap] after a successful capture. */
internal data class SiteWidgetCapture(
    val source: SiteWidgetSource,
    val bitmap: Bitmap,
)

/** Bounds engine capture without applying the tab switcher's thumbnail crop. */
internal object SiteWidgetCaptureRunner {
    fun capture(
        source: SiteWidgetSource,
        session: AndroidBrowserEngineSessionPort,
        view: View,
        handler: Handler,
        isCurrent: () -> Boolean,
        onComplete: (SiteWidgetCapture?) -> Unit,
    ): BrowserEnginePreviewCapture? {
        val widthPx = view.width
        val heightPx = view.height
        if (widthPx <= 0 || heightPx <= 0 || !isCurrent()) {
            onComplete(null)
            return null
        }
        val targetWidthPx = minOf(widthPx, MAX_CAPTURE_WIDTH_PX)
        val targetHeightPx = ceil(heightPx.toDouble() * targetWidthPx / widthPx)
            .toInt()
            .coerceAtLeast(1)
        var completed = false
        var engineCapture: BrowserEnginePreviewCapture? = null
        lateinit var timeout: Runnable

        fun finish(bitmap: Bitmap?) {
            if (completed) {
                bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                return
            }
            completed = true
            handler.removeCallbacks(timeout)
            val accepted = bitmap?.takeUnless(Bitmap::isRecycled)?.takeIf {
                view.width == widthPx && view.height == heightPx && isCurrent()
            }
            if (accepted == null) bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            onComplete(accepted?.let {
                SiteWidgetCapture(
                    if (source.viewportWidthPx > 0) source.copy(
                        viewportHeightPx = (it.height.toLong() * widthPx / it.width).toInt(),
                    ) else source,
                    it,
                )
            })
        }

        fun onMain(action: () -> Unit) {
            if (Looper.myLooper() == handler.looper) action() else handler.post(action)
        }

        timeout = Runnable {
            if (completed) return@Runnable
            finish(null)
            engineCapture?.cancel()
        }
        handler.postDelayed(timeout, CAPTURE_TIMEOUT_MS)
        engineCapture = runCatching {
            session.capturePreview(
                targetWidthPx = targetWidthPx,
                visibleViewHeightPx = heightPx,
                maximumTargetHeightPx = targetHeightPx,
                onComplete = { bitmap -> onMain { finish(bitmap) } },
            )
        }.getOrNull()
        if (engineCapture == null) {
            finish(null)
            return null
        }
        return BrowserEnginePreviewCapture {
            onMain {
                if (!completed) {
                    finish(null)
                    engineCapture.cancel()
                }
            }
        }
    }

    private const val MAX_CAPTURE_WIDTH_PX = 1_280
    private const val CAPTURE_TIMEOUT_MS = 2_500L
}
