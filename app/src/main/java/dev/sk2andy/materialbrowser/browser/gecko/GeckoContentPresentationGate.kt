package dev.sk2andy.materialbrowser.browser.gecko

/**
 * Tracks whether Gecko's compositor currently presents valid page content.
 *
 * Gecko may start its compositor before page content exists. Conversely, a paused or detached
 * surface can stop displaying valid content while the compositor keeps running. Both signals are
 * therefore required before Candy replaces a tab-preview handoff with the live engine view. A
 * retained document can reuse its earlier content paint once the replacement surface composites.
 */
internal class GeckoContentPresentationGate {
    private var surfaceAvailable = false
    private var compositorStarted = false
    private var contentPainted = false
    // A paint reset can precede surface loss without replacing the current document.
    private var documentPainted = false
    private var retainedDocumentPainted = false
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
        // Gecko can reset paint after background memory pressure without repeating the document's
        // contentful-paint callback. A new composite can present the same already-painted document.
        if (retainedDocumentPainted) {
            contentPainted = true
            retainedDocumentPainted = false
        }
        dispatchIfReady()
    }

    fun onFirstContentfulPaint() {
        contentPainted = true
        documentPainted = true
        retainedDocumentPainted = false
        dispatchIfReady()
    }

    fun onNavigationStarted() {
        contentPainted = false
        documentPainted = false
        retainedDocumentPainted = false
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
        retainedDocumentPainted = retainedDocumentPainted || documentPainted
        surfaceAvailable = false
        compositorStarted = false
        presentationGeneration++
    }

    /** A detached surface needs a new composite; the session's page paint remains valid. */
    fun onSurfaceDetached() {
        retainedDocumentPainted = retainedDocumentPainted || documentPainted
        surfaceAvailable = false
        compositorStarted = false
        pendingListener = null
        presentationGeneration++
    }

    fun close() {
        surfaceAvailable = false
        compositorStarted = false
        contentPainted = false
        documentPainted = false
        retainedDocumentPainted = false
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
