package dev.sk2andy.materialbrowser.browser.gecko

/** Selected-tab protection is independent of whether a renderer is currently visible. */
internal class GeckoSessionPriorityController(
    private val containsFormData: ((Boolean?) -> Unit) -> Unit,
    private val navigationGeneration: () -> Long,
    private val setHighPriority: (Boolean) -> Unit,
    private val scheduleExpiry: (() -> Unit) -> (() -> Unit),
) {
    private var selected = false
    private var closed = false
    private var selectionGeneration = 0L
    private var cancelExpiry: (() -> Unit)? = null

    fun setSelected(selected: Boolean) {
        if (closed || this.selected == selected) return
        this.selected = selected
        val generation = ++selectionGeneration
        cancelExpiry?.invoke()
        cancelExpiry = null
        if (selected) {
            setHighPriority(true)
            return
        }
        val navigation = navigationGeneration()
        // Bound protection even if Gecko's asynchronous form check never completes.
        cancelExpiry = scheduleExpiry {
            if (!closed && selectionGeneration == generation && !this.selected) {
                cancelExpiry = null
                setHighPriority(false)
            }
        }
        containsFormData { containsData ->
            if (closed || selectionGeneration != generation || this.selected) return@containsFormData
            if (navigationGeneration() != navigation || containsData == false) {
                cancelExpiry?.invoke()
                cancelExpiry = null
                setHighPriority(false)
                return@containsFormData
            }
            // Unknown input state receives the same bounded protection as confirmed form data.
            if (cancelExpiry != null) setHighPriority(true)
        }
    }

    fun close() {
        closed = true
        selectionGeneration++
        cancelExpiry?.invoke()
        cancelExpiry = null
    }

    companion object {
        const val FORM_PRIORITY_LIFETIME_MILLIS = 180_000L
    }
}
