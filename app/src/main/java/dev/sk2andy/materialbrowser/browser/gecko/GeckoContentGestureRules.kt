package dev.sk2andy.materialbrowser.browser.gecko

internal data class GeckoContentGestureState(
    val activeTouchDownTime: Long? = null,
    val cancelledTouchDownTime: Long? = null,
)

internal data class GeckoContentGestureTransition(
    val state: GeckoContentGestureState,
    val dispatchCancelDownTime: Long? = null,
)

internal object GeckoContentGestureRules {
    fun onDown(
        state: GeckoContentGestureState,
        downTime: Long,
        handled: Boolean,
    ): GeckoContentGestureState = if (handled) {
        state.copy(
            activeTouchDownTime = downTime,
            cancelledTouchDownTime = null,
        )
    } else {
        state.copy(
            activeTouchDownTime = null,
            cancelledTouchDownTime = null,
        )
    }

    fun onTerminal(
        state: GeckoContentGestureState,
        downTime: Long,
    ): GeckoContentGestureState = if (state.activeTouchDownTime == downTime) {
        state.copy(activeTouchDownTime = null)
    } else {
        state
    }

    fun cancel(state: GeckoContentGestureState): GeckoContentGestureTransition =
        GeckoContentGestureTransition(
            state = state.copy(
                activeTouchDownTime = null,
                cancelledTouchDownTime =
                    state.activeTouchDownTime ?: state.cancelledTouchDownTime,
            ),
            dispatchCancelDownTime = state.activeTouchDownTime,
        )

    fun hasCancelledStream(state: GeckoContentGestureState): Boolean =
        state.cancelledTouchDownTime != null
}
