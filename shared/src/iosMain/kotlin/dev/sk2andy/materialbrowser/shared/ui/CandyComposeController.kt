package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIView
import platform.UIKit.UIViewContentMode
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
class IosBrowserViewportState(
    initialViewport: UIView,
    initialSnapshot: BrowserViewportSnapshot,
) {
    private val previews = mutableStateMapOf<String, UIImage>()
    internal var viewport by mutableStateOf(initialViewport)
        private set
    internal var viewportVersion by mutableLongStateOf(0L)
        private set
    var snapshot by mutableStateOf(initialSnapshot)
        private set

    fun updateViewport(value: UIView) {
        // ObjC peers can acquire different Kotlin wrappers across the Swift boundary.
        if (!viewport.isEqual(value)) {
            viewport = value
            viewportVersion += 1
        }
    }

    fun updateSnapshot(value: BrowserViewportSnapshot) {
        snapshot = value
    }

    fun updatePreview(tabId: String, value: UIImage?) {
        if (value == null) {
            previews.remove(tabId)
            return
        }
        previews[tabId] = value
    }

    fun retainPreviews(tabIds: List<String>) {
        val retainedIds = tabIds.toSet()
        previews.keys.toList().filterNot(retainedIds::contains).forEach(previews::remove)
    }

    internal fun preview(tabId: String): UIImage? = previews[tabId]
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
class CandyComposeControllerFactory {
    fun create(
        state: IosBrowserViewportState,
        actionSink: BrowserViewportActionSink,
    ): UIViewController {
        val composeController = ComposeUIViewController {
            CandyBrowserApp(
                snapshot = state.snapshot,
                actionSink = actionSink,
                chromeEffects = IosLiquidGlassChromeEffects,
                showBrowserChrome = false,
                browserViewport = {
                    key(state.viewportVersion) {
                        UIKitView(
                            factory = { state.viewport },
                            modifier = Modifier.fillMaxSize(),
                            properties = UIKitInteropProperties(
                                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                                isNativeAccessibilityEnabled = true,
                            ),
                        )
                    }
                },
                tabPreview = { tab, modifier ->
                    val preview = state.preview(tab.id)
                    if (preview == null) {
                        CandyTabPreviewFallback(tab = tab, modifier = modifier)
                    } else {
                        key(preview) {
                            UIKitView(
                                factory = {
                                    UIImageView(image = preview).apply {
                                        contentMode =
                                            UIViewContentMode.UIViewContentModeScaleAspectFill
                                        clipsToBounds = true
                                    }
                                },
                                update = { imageView -> imageView.image = preview },
                                modifier = modifier,
                                properties = UIKitInteropProperties(
                                    interactionMode = null,
                                    isNativeAccessibilityEnabled = false,
                                ),
                            )
                        }
                    }
                },
            )
        }
        return composeController
    }
}
