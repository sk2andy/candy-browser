package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.shared.ui.LocalCandyBrowserChromeEffects
import dev.sk2andy.materialbrowser.ui.SettingsDestination

@Composable
fun SettingsRouter(
    destination: SettingsDestination,
    modifier: Modifier = Modifier,
    backPreviewDestination: SettingsDestination? = null,
    destinationModifier: Modifier = Modifier,
    content: @Composable (SettingsDestination) -> Unit,
) {
    val reduceMotion = LocalCandyBrowserChromeEffects.current.reducesMotion()
    val pageState = rememberSaveableStateHolder()
    val currentContent by rememberUpdatedState(content)
    val pages = remember(pageState) {
        SettingsDestination.entries.associateWith { page ->
            movableContentOf {
                pageState.SaveableStateProvider(page.name) {
                    currentContent(page)
                }
            }
        }
    }
    val transition = updateTransition(destination, label = "Settings destination")
    // Interrupted AnimatedContent transitions can retain more than two routes. Wait until all
    // outgoing content is gone before moving a saved page into the predictive background.
    val preview = backPreviewDestination?.takeIf {
        it != destination && transition.currentState == transition.targetState && !transition.isRunning
    }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (preview != null) {
                Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                    pages.getValue(preview)()
                }
            }
            transition.AnimatedContent(
                modifier = destinationModifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else if (SettingsNavigationRules.isBackNavigation(initialState, targetState)) {
                        (slideInHorizontally { width -> -width / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> -width / 3 } + fadeOut())
                    }
                },
                content = { currentDestination ->
                    // The same movable instance also owns a predictive preview, so moving it
                    // here on commit never registers a second provider for the same page.
                    pages.getValue(currentDestination)()
                },
            )
        }
    }
}
