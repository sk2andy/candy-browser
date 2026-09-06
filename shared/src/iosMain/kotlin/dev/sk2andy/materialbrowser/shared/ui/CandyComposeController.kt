package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
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
    private val previews = mutableStateMapOf<String, UIImageView>()
    internal var viewport by mutableStateOf(initialViewport)
        private set
    internal var viewportVersion by mutableLongStateOf(0L)
        private set
    var snapshot by mutableStateOf(initialSnapshot)
        private set

    fun updateViewport(value: UIView) {
        if (viewport !== value) {
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
        val preview = previews[tabId] ?: UIImageView().also { imageView ->
            imageView.contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
            imageView.clipsToBounds = true
            previews[tabId] = imageView
        }
        preview.image = value
    }

    fun retainPreviews(tabIds: List<String>) {
        val retainedIds = tabIds.toSet()
        previews.keys.toList().filterNot(retainedIds::contains).forEach(previews::remove)
    }

    internal fun preview(tabId: String): UIImageView? = previews[tabId]
}

@OptIn(ExperimentalForeignApi::class)
class CandyComposeControllerFactory {
    fun create(
        state: IosBrowserViewportState,
        actionSink: BrowserViewportActionSink,
    ): UIViewController = ComposeUIViewController {
        CandyBrowserApp(
            snapshot = state.snapshot,
            actionSink = actionSink,
            browserViewport = {
                key(state.viewportVersion) {
                    UIKitView(
                        factory = { state.viewport },
                        modifier = Modifier.fillMaxSize(),
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
                            factory = { preview },
                            modifier = modifier,
                            interactive = false,
                            accessibilityEnabled = false,
                        )
                    }
                }
            },
        )
    }
}
