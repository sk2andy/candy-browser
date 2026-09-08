package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.view.View
import android.webkit.WebView
import android.view.ViewGroup
import android.widget.FrameLayout
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.util.WeakHashMap

/** Compatibility seam for the pre-Gecko System WebView regression suite. */
internal object SystemWebViewLegacyTestAccess {
    private val linkPeekHosts = WeakHashMap<WebView, View>()

    fun selectEngine(context: Context) {
        BrowserSessionStore(context).saveAndroidBrowserEngineKind(
            AndroidBrowserEngineKind.SystemWebView,
        )
    }

    fun rememberLinkPeekHost(webView: WebView, host: View) {
        linkPeekHosts[webView] = host
    }

    fun removeLinkPeekHost(webView: WebView): View = linkPeekHosts.remove(webView) ?: webView
}

internal fun BrowserController.selectedWebViewForTesting(): WebView =
    requireNotNull(selectedBrowserEngineViewForTesting().findWebView())

internal fun BrowserController.externalLinkPreviewWebViewForTesting(): WebView? =
    externalLinkPreviewEngineViewForTesting().findWebView()

internal fun BrowserController.attachSelectedWebView(host: FrameLayout): WebView =
    requireNotNull(attachSelectedBrowserEngineView(host).findWebView())

internal fun BrowserController.createLinkPeekPreviewWebView(
    url: String,
    onProgressChanged: (Int) -> Unit,
    onCommittedUrlChanged: (String) -> Unit,
): WebView {
    val host = createLinkPeekPreviewView(url, onProgressChanged, onCommittedUrlChanged)
    return requireNotNull(host.findWebView()).also { webView ->
        SystemWebViewLegacyTestAccess.rememberLinkPeekHost(webView, host)
    }
}

internal fun BrowserController.releaseLinkPeekPreviewWebView(webView: WebView) {
    releaseLinkPeekPreviewView(SystemWebViewLegacyTestAccess.removeLinkPeekHost(webView))
}

private fun View?.findWebView(): WebView? = when (this) {
    is WebView -> this
    is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
        getChildAt(index).findWebView()
    }
    else -> null
}
