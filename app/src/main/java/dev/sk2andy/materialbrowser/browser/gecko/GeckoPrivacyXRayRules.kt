package dev.sk2andy.materialbrowser.browser.gecko

internal object GeckoPrivacyXRayRules {
    fun requestBlockerAction(
        actions: List<GeckoExtensionActionState>,
        tabId: String,
    ): GeckoExtensionActionKey? = actions.firstOrNull { action ->
        action.key.extensionId == U_BLOCK_ID &&
            action.key.kind == GeckoExtensionActionKind.Browser &&
            action.enabled &&
            (action.key.tabId == null || action.key.tabId == tabId)
    }?.key

    private const val U_BLOCK_ID = "uBlock0@raymondhill.net"
}
