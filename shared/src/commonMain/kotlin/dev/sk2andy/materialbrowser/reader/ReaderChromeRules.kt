package dev.sk2andy.materialbrowser.reader

enum class ReaderChromeMode { Expanded, Compact, Hidden }

object ReaderChromeRules {
    fun toggle(mode: ReaderChromeMode): ReaderChromeMode = when (mode) {
        ReaderChromeMode.Expanded -> ReaderChromeMode.Compact
        ReaderChromeMode.Compact, ReaderChromeMode.Hidden -> ReaderChromeMode.Expanded
    }

    fun restore(mode: ReaderChromeMode): ReaderChromeMode =
        if (mode == ReaderChromeMode.Hidden) ReaderChromeMode.Compact else mode
}
