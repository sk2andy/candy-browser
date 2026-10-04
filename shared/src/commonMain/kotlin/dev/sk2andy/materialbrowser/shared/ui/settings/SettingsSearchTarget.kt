package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/** Ephemeral localized title selected in settings search. Platforms without search leave it null. */
val LocalSettingsSearchTarget = staticCompositionLocalOf<String?> { null }

@Composable
fun Modifier.settingsSearchTarget(title: String): Modifier {
    if (LocalSettingsSearchTarget.current != title) return this
    val requester = remember { BringIntoViewRequester() }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(placed, title) {
        if (placed) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { placed = true }
        .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
}
