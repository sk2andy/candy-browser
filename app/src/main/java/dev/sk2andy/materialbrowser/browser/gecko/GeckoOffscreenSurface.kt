package dev.sk2andy.materialbrowser.browser.gecko

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import org.mozilla.geckoview.GeckoDisplay
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.ScreenLength

/** A compositor target owned by one temporary session, without an Android window. */
internal class GeckoOffscreenSurface private constructor(
    private val session: GeckoSession,
    private val display: GeckoDisplay,
    private val reader: ImageReader,
    private val onClosed: () -> Unit,
) : BrowserEngineOffscreenSurface {
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var pendingCapture: BrowserEnginePreviewCapture? = null

    override fun capture(scrollY: Int, onComplete: (Bitmap?) -> Unit): BrowserEnginePreviewCapture? {
        if (closed) return null
        pendingCapture?.cancel()
        val cancelled = AtomicBoolean(false)
        session.panZoomController.scrollTo(
            ScreenLength.fromPixels(0.0),
            ScreenLength.fromPixels(scrollY.coerceAtLeast(0).toDouble()),
            PanZoomController.SCROLL_BEHAVIOR_AUTO,
        )
        val capture = Runnable {
            if (closed || cancelled.get()) return@Runnable
            val result = runCatching { display.capturePixels() }.getOrNull()
            if (result == null) {
                onComplete(null)
                return@Runnable
            }
            result.withHandler(handler).accept(
                { bitmap ->
                    if (closed || cancelled.get()) bitmap?.recycle() else onComplete(bitmap)
                },
                { if (!closed && !cancelled.get()) onComplete(null) },
            )
        }
        handler.postDelayed(capture, SCROLL_SETTLE_MILLIS)
        return BrowserEnginePreviewCapture {
            cancelled.set(true)
            handler.removeCallbacks(capture)
        }.also { pendingCapture = it }
    }

    override fun close() {
        if (closed) return
        closed = true
        pendingCapture?.cancel()
        pendingCapture = null
        runCatching { display.surfaceDestroyed() }
        runCatching { session.releaseDisplay(display) }
        runCatching { session.setActive(false) }
        reader.setOnImageAvailableListener(null, null)
        reader.close()
        onClosed()
    }

    companion object {
        private const val SCROLL_SETTLE_MILLIS = 250L

        fun create(
            session: GeckoSession,
            widthPx: Int,
            heightPx: Int,
            onSurfaceCreated: () -> Unit,
            onClosed: () -> Unit,
        ): GeckoOffscreenSurface? {
            if (widthPx <= 0 || heightPx <= 0) return null
            val reader = ImageReader.newInstance(widthPx, heightPx, PixelFormat.RGBX_8888, 2)
            var display: GeckoDisplay? = null
            return try {
                // Consume compositor buffers so rendering never stalls behind a full image queue.
                reader.setOnImageAvailableListener(
                    { source -> runCatching { source.acquireLatestImage()?.close() } },
                    Handler(Looper.getMainLooper()),
                )
                display = session.acquireDisplay()
                onSurfaceCreated()
                display.surfaceChanged(
                    GeckoDisplay.SurfaceInfo.Builder(reader.surface).size(widthPx, heightPx).build(),
                )
                session.setActive(true)
                GeckoOffscreenSurface(session, display, reader, onClosed)
            } catch (_: RuntimeException) {
                display?.let {
                    runCatching { it.surfaceDestroyed() }
                    runCatching { session.releaseDisplay(it) }
                }
                reader.close()
                onClosed()
                null
            }
        }
    }
}
