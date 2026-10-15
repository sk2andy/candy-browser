package dev.sk2andy.materialbrowser.browser.systemwebview

import android.app.Presentation
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineOffscreenSurface
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreviewCapture
import java.util.concurrent.atomic.AtomicBoolean

/** Own-content display gives Chromium a real hardware surface without showing a user window. */
internal class SystemWebViewOffscreenSurface(
    private val webView: WebView,
    private val widthPx: Int,
    private val heightPx: Int,
) : BrowserEngineOffscreenSurface {
    private val handler = Handler(Looper.getMainLooper())
    private val reader = ImageReader.newInstance(widthPx, heightPx, PixelFormat.RGBA_8888, 2)
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var closed = false
    private var pendingCapture: BrowserEnginePreviewCapture? = null

    init {
        try {
            reader.setOnImageAvailableListener(
                { source -> runCatching { source.acquireLatestImage()?.close() } },
                handler,
            )
            val context = webView.context.applicationContext
            val manager = context.getSystemService(DisplayManager::class.java)
            val createdDisplay = checkNotNull(manager.createVirtualDisplay(
                "Candy website widget",
                widthPx,
                heightPx,
                context.resources.displayMetrics.densityDpi,
                reader.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            ))
            display = createdDisplay
            val createdPresentation = Presentation(context, createdDisplay.display, R.style.Theme_MaterialBrowser)
            presentation = createdPresentation
            createdPresentation.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_FULLSCREEN)
                setDimAmount(0f)
                decorView.setPadding(0, 0, 0, 0)
            }
            createdPresentation.setContentView(
                webView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            createdPresentation.show()
            createdPresentation.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } catch (error: RuntimeException) {
            close()
            throw error
        }
    }

    override fun capture(scrollY: Int, onComplete: (Bitmap?) -> Unit): BrowserEnginePreviewCapture? {
        val window = presentation?.window ?: return null
        if (closed) return null
        pendingCapture?.cancel()
        val cancelled = AtomicBoolean(false)
        var attempts = 0
        lateinit var copyFrame: Runnable
        copyFrame = Runnable {
            if (closed || cancelled.get()) return@Runnable
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply {
                density = Bitmap.DENSITY_NONE
            }
            attempts++
            try {
                PixelCopy.request(window, bitmap, { result ->
                    if (closed || cancelled.get()) {
                        bitmap.recycle()
                    } else if (result == PixelCopy.SUCCESS) {
                        onComplete(bitmap)
                    } else {
                        bitmap.recycle()
                        if (result == PixelCopy.ERROR_SOURCE_NO_DATA && attempts < MAX_COPY_ATTEMPTS) {
                            webView.invalidate()
                            handler.postDelayed(copyFrame, FRAME_SETTLE_MILLIS)
                        } else {
                            onComplete(null)
                        }
                    }
                }, handler)
            } catch (_: IllegalArgumentException) {
                bitmap.recycle()
                onComplete(null)
            }
        }
        webView.scrollTo(0, scrollY.coerceAtLeast(0))
        webView.postVisualStateCallback(0L, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (closed || cancelled.get()) return
                webView.scrollTo(0, scrollY.coerceAtLeast(0))
                webView.invalidate()
                handler.postDelayed(copyFrame, FRAME_SETTLE_MILLIS)
            }
        })
        return BrowserEnginePreviewCapture {
            cancelled.set(true)
            handler.removeCallbacks(copyFrame)
        }.also { pendingCapture = it }
    }

    override fun close() {
        if (closed) return
        closed = true
        pendingCapture?.cancel()
        pendingCapture = null
        runCatching { presentation?.dismiss() }
        presentation = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        display?.release()
        display = null
        reader.setOnImageAvailableListener(null, null)
        reader.close()
    }

    private companion object {
        const val FRAME_SETTLE_MILLIS = 100L
        const val MAX_COPY_ATTEMPTS = 3
    }
}
