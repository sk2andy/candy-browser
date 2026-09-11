package dev.sk2andy.materialbrowser.browser

internal enum class WebContentTopInsetMode {
    EdgeToEdge,
    ScrollableDocument,
    NativeSafeArea,
}

internal object WebContentTopInsetRules {
    fun resolve(
        drawsEdgeToEdge: Boolean,
        forceSafeArea: Boolean,
        scrollableDocumentEnabled: Boolean,
    ): WebContentTopInsetMode = when {
        drawsEdgeToEdge -> WebContentTopInsetMode.EdgeToEdge
        forceSafeArea -> WebContentTopInsetMode.NativeSafeArea
        scrollableDocumentEnabled -> WebContentTopInsetMode.ScrollableDocument
        else -> WebContentTopInsetMode.EdgeToEdge
    }
}
