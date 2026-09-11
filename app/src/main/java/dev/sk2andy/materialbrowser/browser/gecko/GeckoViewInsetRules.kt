package dev.sk2andy.materialbrowser.browser.gecko

import androidx.core.view.WindowInsetsCompat

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
)

/** Assigns every safe-area edge to either Candy's native host or Gecko, never both. */
internal object GeckoViewInsetRules {
    fun resolve(
        safeArea: GeckoViewInsets,
        forceNativeSafeArea: Boolean,
        isFullscreenContent: Boolean,
        isInsideSafeDrawingHost: Boolean,
    ): GeckoViewInsetLayout = if (isInsideSafeDrawingHost) {
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
        } else {
            GeckoViewInsetLayout(
                margins = GeckoViewInsets.Zero,
                rendererSafeAreaOverride = normalizedSafeArea,
                scrollableTopInsetPx = normalizedSafeArea.top,
            )
        }
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
