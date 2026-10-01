package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun rememberBrowserImeHandoff(): BrowserImeHandoff {
    val scope = rememberCoroutineScope()
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val handoff = remember(scope, imeInsets, density) { BrowserImeHandoff(scope, imeInsets, density) }
    DisposableEffect(handoff) {
        onDispose { handoff.cancel() }
    }
    return handoff
}

internal class BrowserImeHandoff(
    private val scope: CoroutineScope,
    private val imeInsets: WindowInsets,
    private val density: Density,
) {
    var isPending by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var generation = 0

    fun run(isCurrent: () -> Boolean, onReady: () -> Unit) {
        cancel()
        val requestGeneration = generation
        isPending = true
        job = scope.launch {
            try {
                snapshotFlow { !isCurrent() || imeInsets.getBottom(density) == 0 }
                    .first { it }
                if (!isCurrent()) return@launch
                // Bind the native surface after the full-height frame following IME dismissal.
                withFrameNanos { }
                if (isCurrent()) onReady()
            } finally {
                if (generation == requestGeneration) {
                    isPending = false
                    job = null
                }
            }
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        isPending = false
    }
}
