package dev.sk2andy.materialbrowser.browser.gecko

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
    val bottomContentClippingPx: Int,
)

/** Lets Gecko paint edge to edge while preserving an explicit native safe-area escape hatch. */
internal object GeckoViewInsetRules {
    fun resolve(
        safeArea: GeckoViewInsets,
        forceNativeSafeArea: Boolean,
    ): GeckoViewInsetLayout = if (forceNativeSafeArea) {
        GeckoViewInsetLayout(
            margins = safeArea,
            bottomContentClippingPx = 0,
        )
    } else {
        GeckoViewInsetLayout(
            margins = GeckoViewInsets.Zero,
            bottomContentClippingPx = safeArea.bottom.coerceAtLeast(0),
        )
    }
}
