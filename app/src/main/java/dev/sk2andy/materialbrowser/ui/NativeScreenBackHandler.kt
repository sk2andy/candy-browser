package dev.sk2andy.materialbrowser.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class NativeScreenBackState {
    val progress = Animatable(0f)
    var swipeEdgeSign by mutableIntStateOf(1)
    var recovery: Job? = null
}

@Composable
internal fun rememberNativeScreenBackState(): NativeScreenBackState =
    remember { NativeScreenBackState() }

@Composable
internal fun NativeScreenBackHandler(
    state: NativeScreenBackState,
    enabled: Boolean = true,
    retainProgressOnBack: Boolean = false,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        state.recovery?.cancel()
        var receivedProgress = false
        try {
            events.collect { event ->
                receivedProgress = true
                state.swipeEdgeSign = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1
                state.progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            if (receivedProgress) {
                state.progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = PredictiveBackMotion.remainingDurationMillis(
                            state.progress.value,
                        ),
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            currentOnBack()
            if (!retainProgressOnBack) state.progress.snapTo(0f)
        } catch (cancellation: CancellationException) {
            state.recovery = scope.launch {
                state.progress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
                )
            }
            throw cancellation
        }
    }
}
