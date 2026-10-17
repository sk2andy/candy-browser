package dev.sk2andy.materialbrowser.shared.ui

/** Platform accessibility decisions used by shared presentation without changing Android defaults. */
internal object CandyPresentationRules {
    fun animationDuration(durationMillis: Int, reduceMotion: Boolean): Int =
        if (reduceMotion) 0 else durationMillis.coerceAtLeast(0)
}
