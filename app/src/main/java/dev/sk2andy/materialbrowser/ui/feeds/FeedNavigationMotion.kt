package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import dev.sk2andy.materialbrowser.shared.ui.LocalCandyBrowserChromeEffects

/** Peer collections fade; the nested catalog moves over its still-composed collection. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedDestinationTransition(
    destination: FeedDestination,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(FeedDestination) -> Unit,
) {
    val savedPages = rememberSaveableStateHolder()
    val reduceMotion = LocalCandyBrowserChromeEffects.current.reducesMotion()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = destination,
        modifier = modifier,
        transitionSpec = {
            if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(effects) togetherWith fadeOut(effects)).using(null)
            }
        },
        label = "feed_destination",
    ) { visibleDestination ->
        savedPages.SaveableStateProvider(visibleDestination.name) {
            Column(Modifier.fillMaxSize()) { content(visibleDestination) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedCatalogTransition(visible: Boolean, content: @Composable () -> Unit) {
    val reduceMotion = LocalCandyBrowserChromeEffects.current.reducesMotion()
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedVisibility(
        visible = visible,
        enter =
            if (reduceMotion) EnterTransition.None
            else slideInHorizontally(spatial) { it / 8 } + fadeIn(effects),
        exit =
            if (reduceMotion) ExitTransition.None
            else slideOutHorizontally(spatial) { it / 8 } + fadeOut(effects),
    ) {
        content()
    }
}
