package dev.sk2andy.materialbrowser.browser.gecko

internal data class GeckoContentGestureState(
    val activeTouchDownTime: Long? = null,
    val cancelledTouchDownTime: Long? = null,
    val observedTouchDownTime: Long? = activeTouchDownTime,
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
            observedTouchDownTime = downTime,
        )
    } else {
        state.copy(
            activeTouchDownTime = null,
            cancelledTouchDownTime = null,
            observedTouchDownTime = downTime,
        )
    }

    fun onTerminal(
        state: GeckoContentGestureState,
        downTime: Long,
    ): GeckoContentGestureState = if (state.observedTouchDownTime == downTime) {
        state.copy(activeTouchDownTime = null, observedTouchDownTime = null)
    } else {
        state
    }

    // Android can rewrite downTime when canceling the current view's touch target.
    fun onCancel(state: GeckoContentGestureState): GeckoContentGestureState = cancel(state).state

    fun cancel(state: GeckoContentGestureState): GeckoContentGestureTransition =
        GeckoContentGestureTransition(
            state = state.copy(
                activeTouchDownTime = null,
                observedTouchDownTime = null,
                cancelledTouchDownTime =
                    state.observedTouchDownTime ?: state.cancelledTouchDownTime,
            ),
            dispatchCancelDownTime = state.activeTouchDownTime,
        )

    fun hasCancelledStream(state: GeckoContentGestureState): Boolean =
        state.cancelledTouchDownTime != null
}
