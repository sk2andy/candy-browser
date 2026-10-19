package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
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
    Surface(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (backPreviewDestination != null) {
                Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                    content(backPreviewDestination)
                }
            }
            AnimatedContent(
                targetState = destination,
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
                label = "Settings destination",
                content = { currentDestination -> content(currentDestination) },
            )
        }
    }
}
