package dev.sk2andy.materialbrowser.browser

internal data class FindInPageState(
    val tabId: String,
    val query: String = "",
    val activeMatchOrdinal: Int? = null,
    val matchCount: Int = 0,
    val isDoneCounting: Boolean = true,
)

internal data class FindInPageMatchPosition(
    val activeMatchNumber: Int,
    val matchCount: Int,
)

internal object FindInPageRules {
    fun withQuery(state: FindInPageState, query: String): FindInPageState {
        if (query == state.query) return state
        return state.copy(
            query = query,
            activeMatchOrdinal = null,
            matchCount = 0,
            isDoneCounting = query.isEmpty(),
        )
    }

    fun withResult(
        state: FindInPageState,
        activeMatchOrdinal: Int,
        matchCount: Int,
        isDoneCounting: Boolean,
    ): FindInPageState {
        if (state.query.isEmpty()) return withQuery(state, state.query)
        val normalizedMatchCount = matchCount.coerceAtLeast(0)
        return state.copy(
            activeMatchOrdinal = if (normalizedMatchCount == 0) {
                null
            } else {
                activeMatchOrdinal.coerceIn(0, normalizedMatchCount - 1)
            },
            matchCount = normalizedMatchCount,
            isDoneCounting = isDoneCounting,
        )
    }

    fun canNavigate(state: FindInPageState): Boolean =
        state.query.isNotEmpty() && state.matchCount > 0

    fun displayPosition(state: FindInPageState): FindInPageMatchPosition {
        val normalizedMatchCount = state.matchCount.coerceAtLeast(0)
        val activeMatchNumber = if (normalizedMatchCount == 0) {
            0
        } else {
            (state.activeMatchOrdinal ?: 0)
                .coerceIn(0, normalizedMatchCount - 1) + 1
        }
        return FindInPageMatchPosition(
            activeMatchNumber = activeMatchNumber,
            matchCount = normalizedMatchCount,
        )
    }
}
