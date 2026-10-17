package dev.sk2andy.materialbrowser.browser

/** One renderer's scroll history, with UIKit points using the same thresholds as Android dp. */
class BrowserChromeScrollController {
    private var state = BrowserChromeScrollState(previousScrollYPx = 0)

    var compact: Boolean = false
        private set

    fun update(scrollY: Double): Boolean {
        if (!scrollY.isFinite()) return compact
        val update = BrowserChromeScrollRules.update(
            state = state,
            event = BrowserEngineScrollEvent(scrollYPx = scrollY.coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt()),
            collapseThresholdPx = COLLAPSE_THRESHOLD,
            expandThresholdPx = EXPAND_THRESHOLD,
        )
        state = update.state
        update.compact?.let { compact = it }
        return compact
    }

    fun reset(scrollY: Double = 0.0) {
        val baseline = scrollY.takeIf(Double::isFinite)?.coerceIn(0.0, Int.MAX_VALUE.toDouble())?.toInt() ?: 0
        state = BrowserChromeScrollState(previousScrollYPx = baseline)
        compact = false
    }

    private companion object {
        const val COLLAPSE_THRESHOLD = 24f
        const val EXPAND_THRESHOLD = 16f
    }
}
