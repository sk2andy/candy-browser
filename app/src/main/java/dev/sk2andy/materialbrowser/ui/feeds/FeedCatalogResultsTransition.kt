package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.ui.LocalCandyBrowserChromeEffects
import dev.sk2andy.materialbrowser.ui.NativeScreenBackHandler
import dev.sk2andy.materialbrowser.ui.predictiveBackSurface
import dev.sk2andy.materialbrowser.ui.rememberNativeScreenBackState

/** The selected tile expands forward; Back reveals the retained discovery page. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedCatalogResultsTransition(
    visible: Boolean,
    origin: FeedCatalogTopicOrigin?,
    backEnabled: Boolean,
    onBack: () -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    val reduceMotion = LocalCandyBrowserChromeEffects.current.reducesMotion()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { bounds = it.boundsInRoot() }) {
        Box(
            Modifier.fillMaxSize()
                .then(if (visible) Modifier.clearAndSetSemantics {} else Modifier)
        ) {
            content(false)
        }
        if (visible) {
            val expansion = remember {
                Animatable(if (origin == null || origin.bounds.isEmpty || reduceMotion) 1f else 0f)
            }
            val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
            LaunchedEffect(Unit) { expansion.animateTo(1f, spatial) }
            val back = rememberNativeScreenBackState()
            NativeScreenBackHandler(back, enabled = backEnabled, onBack = onBack)
            Surface(
                modifier =
                    Modifier.fillMaxSize()
                    .testTag("feed_catalog_results_screen")
                    .predictiveBackSurface(back.progress.value, back.swipeEdgeSign)
                    .graphicsLayer {
                        val progress = expansion.value.coerceIn(0f, 1f)
                        val source = origin
                        if (source != null && !source.bounds.isEmpty && !bounds.isEmpty) {
                            val transform =
                                FeedCatalogMorphRules.transform(source.bounds, bounds, progress)
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = transform.scaleX
                            scaleY = transform.scaleY
                            translationX = transform.translationX
                            translationY = transform.translationY
                            val corners = feedCatalogTopicShape(source.index, 24.dp)
                            // Compensate for the layer scale so the source corners keep their
                            // real on-screen radius while the container expands independently.
                            shape = object : Shape {
                                override fun createOutline(
                                    size: Size,
                                    layoutDirection: LayoutDirection,
                                    density: Density,
                                ): Outline {
                                    val outline =
                                        corners.createOutline(size, layoutDirection, density) as Outline.Rounded
                                    fun CornerRadius.morph() = CornerRadius(
                                        x * (1f - progress) / transform.scaleX,
                                        y * (1f - progress) / transform.scaleY,
                                    )
                                    return Outline.Rounded(
                                        RoundRect(
                                            rect = Rect(0f, 0f, size.width, size.height),
                                            topLeft = outline.roundRect.topLeftCornerRadius.morph(),
                                            topRight = outline.roundRect.topRightCornerRadius.morph(),
                                            bottomRight = outline.roundRect.bottomRightCornerRadius.morph(),
                                            bottomLeft = outline.roundRect.bottomLeftCornerRadius.morph(),
                                        )
                                    )
                                }
                            }
                            clip = true
                        }
                    },
                color = MaterialTheme.colorScheme.surface,
            ) {
                content(true)
            }
        }
    }
}
