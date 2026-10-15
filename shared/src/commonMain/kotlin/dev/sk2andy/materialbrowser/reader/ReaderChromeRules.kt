package dev.sk2andy.materialbrowser.reader

import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules

enum class ReaderChromeMode { Expanded, Compact, Hidden }

data class ReaderChromeScrollUpdate(
    val mode: ReaderChromeMode,
    val accumulatedDistanceDp: Float = 0f,
)

object ReaderChromeRules {
    fun onScroll(
        mode: ReaderChromeMode,
        accumulatedDistanceDp: Float,
        deltaDp: Float,
        atTop: Boolean,
    ): ReaderChromeScrollUpdate {
        if (mode == ReaderChromeMode.Hidden) return ReaderChromeScrollUpdate(mode)
        if (atTop) return ReaderChromeScrollUpdate(ReaderChromeMode.Expanded)
        if (!deltaDp.isFinite() || !accumulatedDistanceDp.isFinite()) {
            return ReaderChromeScrollUpdate(mode)
        }
        val distance = if (deltaDp * accumulatedDistanceDp < 0f) {
            deltaDp
        } else {
            accumulatedDistanceDp + deltaDp
        }
        return when {
            distance >= 24f -> ReaderChromeScrollUpdate(ReaderChromeMode.Compact)
            distance <= -16f -> ReaderChromeScrollUpdate(ReaderChromeMode.Expanded)
            else -> ReaderChromeScrollUpdate(mode, distance)
        }
    }

    fun restore(mode: ReaderChromeMode): ReaderChromeMode =
        if (mode == ReaderChromeMode.Hidden) ReaderChromeMode.Compact else mode

    fun compactAddress(sourceUrl: String): String {
        val normalized = BrowserUrlRules.normalizeHttpUrl(sourceUrl)?.value ?: return sourceUrl
        val authority = normalized.substringAfter("://").substringBefore('/')
            .substringBefore('?').substringBefore('#')
        return if (authority.startsWith('[')) {
            authority.substringBefore(']').plus(']')
        } else {
            authority.substringBefore(':').removePrefix("www.")
        }
    }
}
