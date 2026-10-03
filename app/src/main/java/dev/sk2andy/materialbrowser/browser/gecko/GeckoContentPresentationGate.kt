package dev.sk2andy.materialbrowser.browser.gecko

/**
 * Tracks whether Gecko's compositor currently presents valid page content.
 *
 * Gecko may start its compositor before page content exists. Conversely, a paused or detached
 * surface can stop displaying valid content while the compositor keeps running. Both signals are
 * therefore required before Candy replaces a tab-preview handoff with the live engine view.
 */
internal class GeckoContentPresentationGate {
    private var surfaceAvailable = false
    private var compositorStarted = false
    private var contentPainted = false
    private var pendingListener: (() -> Unit)? = null

    var presentationGeneration = 0L
        private set

    val isContentPresented: Boolean
        get() = surfaceAvailable && compositorStarted && contentPainted

    fun awaitContentPresented(listener: () -> Unit) {
        pendingListener = listener
        dispatchIfReady()
    }

    fun onFirstComposite() {
        if (!surfaceAvailable) return
        compositorStarted = true
        dispatchIfReady()
    }

    fun onFirstContentfulPaint() {
        contentPainted = true
        dispatchIfReady()
    }

    fun onPaintStatusReset() {
        contentPainted = false
    }

    fun onSurfaceCreated() {
        if (surfaceAvailable) return
        surfaceAvailable = true
        compositorStarted = false
        presentationGeneration++
    }

    /** A temporary surface loss preserves the page paint and its pending presentation request. */
    fun onSurfaceDestroyed() {
        if (!surfaceAvailable) return
        surfaceAvailable = false
        compositorStarted = false
        presentationGeneration++
    }

    /** A detached surface needs a new composite; the session's page paint remains valid. */
    fun onSurfaceDetached() {
        surfaceAvailable = false
        compositorStarted = false
        pendingListener = null
        presentationGeneration++
    }

    fun close() {
        surfaceAvailable = false
        compositorStarted = false
        contentPainted = false
        pendingListener = null
        presentationGeneration++
    }

    private fun dispatchIfReady() {
        if (!isContentPresented) return
        pendingListener?.also { listener ->
            pendingListener = null
            listener()
        }
    }
}
