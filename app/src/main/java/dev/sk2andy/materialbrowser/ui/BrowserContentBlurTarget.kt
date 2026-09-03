package dev.sk2andy.materialbrowser.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens
import eightbitlab.com.blurview.BlurTarget

@Composable
internal fun BrowserContentBlurTarget(
    enabled: Boolean,
    onTargetAttached: (BlurTarget) -> Unit,
    onTargetReleased: (BlurTarget) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!browserChromeSurfaceTokens().backdropBlurEnabled) {
        Box(modifier = modifier) { content() }
        return
    }

    val parentComposition = rememberCompositionContext()
    val currentContent = rememberUpdatedState(content)
    val currentOnTargetAttached = rememberUpdatedState(onTargetAttached)
    val currentOnTargetReleased = rememberUpdatedState(onTargetReleased)

    AndroidView(
        factory = { context ->
            BlurTarget(context).apply {
                addView(
                    ComposeView(context).apply {
                        setParentCompositionContext(parentComposition)
                        setViewCompositionStrategy(
                            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
                        )
                        setContent { currentContent.value() }
                    },
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        update = { target ->
            (target.getChildAt(0) as? ComposeView)
                ?.setParentCompositionContext(parentComposition)
            if (enabled) {
                currentOnTargetAttached.value(target)
            } else {
                currentOnTargetReleased.value(target)
            }
        },
        onRelease = { target ->
            currentOnTargetReleased.value(target)
            (target.getChildAt(0) as? ComposeView)?.disposeComposition()
            target.removeAllViews()
        },
        modifier = modifier,
    )
}

@Composable
internal fun BrowserContentBlurTargetWithConstraints(
    enabled: Boolean,
    onTargetAttached: (BlurTarget) -> Unit,
    onTargetReleased: (BlurTarget) -> Unit,
    modifier: Modifier,
    contentModifier: Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    BrowserContentBlurTarget(
        enabled = enabled,
        onTargetAttached = onTargetAttached,
        onTargetReleased = onTargetReleased,
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = contentModifier,
            content = content,
        )
    }
}
