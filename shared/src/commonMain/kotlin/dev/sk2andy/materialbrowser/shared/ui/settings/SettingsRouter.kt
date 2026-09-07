package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.ui.SettingsDestination

@Composable
fun SettingsRouter(
    destination: SettingsDestination,
    modifier: Modifier = Modifier,
    content: @Composable (SettingsDestination) -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AnimatedContent(
            targetState = destination,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (
                    targetState == SettingsDestination.Home ||
                    initialState == SettingsDestination.ToppingCatalog &&
                    targetState == SettingsDestination.Userscripts ||
                    initialState == SettingsDestination.AddressBarActions &&
                    targetState == SettingsDestination.TabsAndGestures ||
                    initialState == SettingsDestination.LinkPeekActions &&
                    targetState == SettingsDestination.TabsAndGestures
                ) {
                    (slideInHorizontally { width -> -width / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> width } + fadeOut())
                } else {
                    (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> -width / 3 } + fadeOut())
                }
            },
            label = "Settings destination",
            content = { currentDestination -> content(currentDestination) },
        )
    }
}
