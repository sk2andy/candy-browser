package dev.sk2andy.materialbrowser.browser.gecko

import androidx.core.view.WindowInsetsCompat
import dev.sk2andy.materialbrowser.browser.WebContentTopInsetTransitionState

internal data class GeckoViewInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    companion object {
        val Zero = GeckoViewInsets(left = 0, top = 0, right = 0, bottom = 0)
    }
}

internal data class GeckoViewInsetLayout(
    val margins: GeckoViewInsets,
    val rendererSafeAreaOverride: GeckoViewInsets?,
    val scrollableTopInsetPx: Int,
    val topInsetTransitionState: WebContentTopInsetTransitionState =
        WebContentTopInsetTransitionState.Document,
)

/** Assigns every safe-area edge to either Candy's native host or Gecko, never both. */
internal object GeckoViewInsetRules {
    fun resolve(
        safeArea: GeckoViewInsets,
        forceNativeSafeArea: Boolean,
        forceNativeTopSafeArea: Boolean,
        isFullscreenContent: Boolean,
        isInsideSafeDrawingHost: Boolean,
        useNativeCssSafeArea: Boolean = true,
        keyboardBottomInsetPx: Int = 0,
        nativeTopHeaderSafeArea: Boolean = false,
    ): GeckoViewInsetLayout {
        val topInsetTransitionState = when {
            isInsideSafeDrawingHost || isFullscreenContent || forceNativeSafeArea ->
                WebContentTopInsetTransitionState.Other
            forceNativeTopSafeArea && nativeTopHeaderSafeArea ->
                WebContentTopInsetTransitionState.WebContentHeader
            forceNativeTopSafeArea -> WebContentTopInsetTransitionState.Other
            else -> WebContentTopInsetTransitionState.Document
        }
        val layout = if (isInsideSafeDrawingHost) {
            GeckoViewInsetLayout(
                margins = GeckoViewInsets.Zero,
                rendererSafeAreaOverride = GeckoViewInsets.Zero,
                scrollableTopInsetPx = 0,
            )
        } else if (isFullscreenContent) {
            GeckoViewInsetLayout(
                margins = GeckoViewInsets.Zero,
                rendererSafeAreaOverride = null,
                scrollableTopInsetPx = 0,
            )
        } else {
            val normalizedSafeArea = safeArea.coerceAtLeastZero()
            if (forceNativeSafeArea) {
                GeckoViewInsetLayout(
                    margins = normalizedSafeArea,
                    rendererSafeAreaOverride = GeckoViewInsets.Zero,
                    scrollableTopInsetPx = 0,
                )
            } else if (forceNativeTopSafeArea) {
                GeckoViewInsetLayout(
                    margins = GeckoViewInsets(
                        left = 0,
                        top = normalizedSafeArea.top,
                        right = 0,
                        bottom = 0,
                    ),
                    rendererSafeAreaOverride = normalizedSafeArea.copy(top = 0),
                    scrollableTopInsetPx = 0,
                )
            } else {
                GeckoViewInsetLayout(
                    margins = GeckoViewInsets.Zero,
                    rendererSafeAreaOverride = if (useNativeCssSafeArea) {
                        normalizedSafeArea
                    } else {
                        normalizedSafeArea.copy(top = 0)
                    },
                    scrollableTopInsetPx = if (useNativeCssSafeArea) 0 else normalizedSafeArea.top,
                )
            }
        }
        val keyboardBottomInset = keyboardBottomInsetPx.coerceAtLeast(0)
        val classifiedLayout = layout.copy(topInsetTransitionState = topInsetTransitionState)
        if (isInsideSafeDrawingHost || keyboardBottomInset == 0) return classifiedLayout
        return classifiedLayout.copy(
            margins = classifiedLayout.margins.copy(
                bottom = maxOf(layout.margins.bottom, keyboardBottomInset),
            ),
            rendererSafeAreaOverride = (
                classifiedLayout.rendererSafeAreaOverride ?: safeArea.coerceAtLeastZero()
                )
                .copy(bottom = 0),
        )
    }
}

internal interface GeckoViewInsetHost {
    fun updateInsets(
        layout: GeckoViewInsetLayout,
        windowInsets: WindowInsetsCompat,
    )
}

private fun GeckoViewInsets.coerceAtLeastZero(): GeckoViewInsets = GeckoViewInsets(
    left = left.coerceAtLeast(0),
    top = top.coerceAtLeast(0),
    right = right.coerceAtLeast(0),
    bottom = bottom.coerceAtLeast(0),
)
