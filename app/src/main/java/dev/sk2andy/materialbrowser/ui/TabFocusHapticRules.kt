package dev.sk2andy.materialbrowser.ui

import kotlin.math.abs

internal object TabFocusHapticRules {
    fun crossedEntryCount(previousPage: Int, currentPage: Int): Int =
        abs(currentPage - previousPage)
}
