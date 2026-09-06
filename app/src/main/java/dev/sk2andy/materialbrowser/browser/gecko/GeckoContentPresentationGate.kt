package dev.sk2andy.materialbrowser.browser.gecko

/**
 * Tracks whether Gecko's compositor currently presents valid page content.
 *
 * Gecko may start its compositor before page content exists. Conversely, a paused or detached
 * surface can stop displaying valid content while the compositor keeps running. Both signals are
 * therefore required before Candy replaces a tab-preview handoff with the live engine view.
 */
internal class GeckoContentPresentationGate {
    private var compositorStarted = false
    private var contentPainted = false
    private var pendingListener: (() -> Unit)? = null

    fun awaitContentPresented(listener: () -> Unit) {
        pendingListener = listener
        dispatchIfReady()
    }

    fun onFirstComposite() {
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

    /** A detached surface needs a new composite; the session's page paint remains valid. */
    fun onSurfaceDetached() {
        compositorStarted = false
        pendingListener = null
    }

    fun close() {
        compositorStarted = false
        contentPainted = false
        pendingListener = null
    }

    private fun dispatchIfReady() {
        if (!compositorStarted || !contentPainted) return
        pendingListener?.also { listener ->
            pendingListener = null
            listener()
        }
    }
}
