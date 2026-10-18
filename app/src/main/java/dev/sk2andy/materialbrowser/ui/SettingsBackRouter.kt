package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsRouter

@Composable
internal fun SettingsBackRouter(
    destination: SettingsDestination,
    onDestinationChanged: (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (SettingsDestination) -> Unit,
) {
    val backState = rememberNativeScreenBackState()
    NativeScreenBackHandler(
        state = backState,
        enabled = destination != SettingsDestination.Home,
        onBack = { onDestinationChanged(SettingsBackRules.parentDestination(destination)) },
    )
    SettingsRouter(
        destination = destination,
        modifier = modifier,
        backPreviewDestination = SettingsBackRules.parentDestination(destination).takeIf {
            destination != SettingsDestination.Home && backState.progress.value > 0f
        },
        destinationModifier = Modifier.predictiveBackSurface(
            progress = backState.progress.value,
            swipeEdgeSign = backState.swipeEdgeSign,
        ),
        content = content,
    )
}
