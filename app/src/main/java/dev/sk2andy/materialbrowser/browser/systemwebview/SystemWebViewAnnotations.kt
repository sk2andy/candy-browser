package dev.sk2andy.materialbrowser.browser.systemwebview

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID
import org.json.JSONObject

internal class SystemWebViewAnnotations(
    private val webView: WebView,
    private val isCurrent: (Long, String) -> Boolean,
) {
    private var listener: ((String) -> Unit)? = null
    private var token = UUID.randomUUID().toString()
    private val installed = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    init {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, BRIDGE, setOf("*")) { _, message, origin, mainFrame, _ ->
                if (!mainFrame || listener == null) return@addWebMessageListener
                val raw = message.data?.takeIf { it.length <= 5_000 } ?: return@addWebMessageListener
                runCatching {
                    val value = JSONObject(raw)
                    val url = value.getString("url")
                    val uri = Uri.parse(url)
                    val id = value.getString("id")
                    if (value.optString("token") != token || id.length !in 1..64 ||
                        !isCurrent(value.getLong("generation"), url) || uri.scheme !in setOf("https", "http") ||
                        uri.scheme != origin.scheme || uri.host != origin.host || effectivePort(uri) != effectivePort(origin)) return@runCatching
                    listener?.invoke(id)
                }
            }
        }
    }

    fun setListener(next: ((String) -> Unit)?) {
        if (installed && listener != null && next == null) {
            webView.evaluateJavascript("globalThis.CandyWebTextOperations?.setAnnotationClickListener(null)", null)
        }
        token = UUID.randomUUID().toString()
        listener = next
    }

    fun script(generation: Long, url: String?): String {
        if (!installed || listener == null || url == null) return "CandyWebTextOperations.setAnnotationClickListener(null);"
        return """
            CandyWebTextOperations.setAnnotationClickListener(id => {
              $BRIDGE.postMessage(JSON.stringify({
                id, token: ${JSONObject.quote(token)}, generation: $generation, url: ${JSONObject.quote(url)}
              }));
            });
        """.trimIndent()
    }

    fun close() {
        setListener(null)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(webView, BRIDGE)
        }
    }

    private fun effectivePort(uri: Uri): Int = uri.port.takeIf { it >= 0 }
        ?: if (uri.scheme == "https") 443 else 80

    private companion object {
        const val BRIDGE = "CandyAnnotationBridge"
    }
}
