package dev.sk2andy.materialbrowser.browser.systemwebview

import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsetLayout
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsets

internal object SystemWebViewSafeAreaRules {
    private const val FULL_WEBVIEW_SAFE_AREA_MILESTONE = 144

    fun supportsCssSafeAreaInsets(versionName: String?): Boolean =
        versionName
            ?.substringBefore('.')
            ?.toIntOrNull()
            ?.let { milestone -> milestone >= FULL_WEBVIEW_SAFE_AREA_MILESTONE }
            ?: false

    fun withNativeCutoutMargins(
        layout: GeckoViewInsetLayout,
        cutout: GeckoViewInsets,
    ): GeckoViewInsetLayout {
        val rendererInsets = layout.rendererSafeAreaOverride ?: return layout
        val margins = layout.margins
        return layout.copy(
            margins = margins.copy(
                left = maxOf(margins.left, minOf(cutout.left, rendererInsets.left)),
                right = maxOf(margins.right, minOf(cutout.right, rendererInsets.right)),
                bottom = maxOf(margins.bottom, minOf(cutout.bottom, rendererInsets.bottom)),
            ),
        )
    }
}
