package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import dev.sk2andy.materialbrowser.browser.BrowserController

@Composable
internal fun BrowserImeOwnershipEffect(
    controller: BrowserController,
    addressEditorVisible: Boolean,
) {
    SideEffect {
        controller.setBrowserChromeOwnsIme(addressEditorVisible)
    }
    DisposableEffect(controller) {
        onDispose { controller.setBrowserChromeOwnsIme(false) }
    }
}
