package dev.sk2andy.materialbrowser.browser.gecko

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import org.mozilla.geckoview.GeckoResult

/** Keeps a departing page frame in memory until the replacement surface presents content. */
internal class GeckoResumeCover(
    private val host: FrameLayout,
    private val surface: SurfaceView,
    private val capture: () -> GeckoResult<Bitmap>,
) {
    private val image = ImageView(host.context).apply {
        scaleType = ImageView.ScaleType.MATRIX
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        visibility = View.GONE
    }
    private var frame: Bitmap? = null
    private var captureRequest = 0L
    private var capturePending = false
    private var surfaceGeneration = 0L
    private var navigationGeneration = 0L
    private var contentPresented = false
    private var released = false
    var isContentPresented: () -> Boolean = { false }
    var onSurfaceCreated: (() -> Unit)? = null
    var onSurfaceDestroyed: (() -> Unit)? = null

    private val layoutListener = View.OnLayoutChangeListener {
        _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
            // A departing frame belongs to one renderer viewport, including its IME margins.
            clearFrame()
        }
    }

    private val callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceGeneration++
            contentPresented = false
            showFrame()
            onSurfaceCreated?.invoke()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceGeneration++
            contentPresented = false
            showFrame()
            onSurfaceDestroyed?.invoke()
        }
    }

    init {
        host.addView(
            image,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        surface.holder.addCallback(callback)
        host.addOnLayoutChangeListener(layoutListener)
    }

    fun captureBeforeBackground() {
        if (released || capturePending || !contentPresented || !surface.holder.surface.isValid) return
        val width = host.width
        val height = host.height
        if (width <= 0 || height <= 0) return
        val request = ++captureRequest
        val result = try {
            capture()
        } catch (_: IllegalStateException) {
            return
        }
        capturePending = true
        result.withHandler(Handler(Looper.getMainLooper())).accept(
            { bitmap ->
                if (released || request != captureRequest) {
                    bitmap?.recycle()
                    return@accept
                }
                capturePending = false
                if (
                    bitmap == null || host.width != width || host.height != height ||
                    bitmap.width != width || bitmap.height != height
                ) {
                    bitmap?.recycle()
                    return@accept
                }
                // Gecko captures physical pixels; drawable density must not rescale them.
                bitmap.density = Bitmap.DENSITY_NONE
                frame = bitmap
                if (!contentPresented) showFrame()
            },
            { if (request == captureRequest) capturePending = false },
        )
    }

    fun onFocusRestored() {
        if (isContentPresented()) onContentPresented()
    }

    fun onContentPresented() {
        val generation = surfaceGeneration
        val navigation = navigationGeneration
        host.postOnAnimation {
            if (
                released || generation != surfaceGeneration || navigation != navigationGeneration ||
                !surface.holder.surface.isValid || !isContentPresented()
            ) {
                return@postOnAnimation
            }
            contentPresented = true
            clearFrame()
        }
    }

    fun onPaintStatusReset() {
        contentPresented = false
    }

    fun onNavigationStarted() {
        navigationGeneration++
        contentPresented = false
        clearFrame()
    }

    fun release() {
        released = true
        surface.holder.removeCallback(callback)
        host.removeOnLayoutChangeListener(layoutListener)
        isContentPresented = { false }
        onSurfaceCreated = null
        onSurfaceDestroyed = null
        clearFrame()
    }

    private fun showFrame() {
        val bitmap = frame?.takeUnless(Bitmap::isRecycled) ?: return
        if (bitmap.width != host.width || bitmap.height != host.height) {
            clearFrame()
            return
        }
        image.setImageBitmap(bitmap)
        image.visibility = View.VISIBLE
    }

    private fun clearFrame() {
        captureRequest++
        capturePending = false
        image.visibility = View.GONE
        image.setImageDrawable(null)
        frame = null
    }
}
