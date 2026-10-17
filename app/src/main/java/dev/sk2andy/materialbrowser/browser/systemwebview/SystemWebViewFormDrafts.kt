package dev.sk2andy.materialbrowser.browser.systemwebview

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.browser.formdraft.FormDraftCodec
import dev.sk2andy.materialbrowser.browser.formdraft.FormDraftRules
import java.util.UUID
import org.json.JSONObject

internal class SystemWebViewFormDrafts(private val webView: WebView) {
    private var token = UUID.randomUUID().toString()
    private var listener: ((String) -> Unit)? = null
    private var handler: ScriptHandler? = null
    private var installed = false
    private val source by lazy { webView.context.assets.open("form_drafts.js").bufferedReader().use { it.readText() } }

    init {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addWebMessageListener(webView, BRIDGE, setOf("*")) { _, message, origin, mainFrame, _ ->
                if (!mainFrame || listener == null) return@addWebMessageListener
                val raw = message.data?.takeIf { it.length <= FormDraftRules.MAX_WIRE_CHARS + 200 }
                    ?: return@addWebMessageListener
                runCatching {
                    val json = JSONObject(raw)
                    if (json.optString("token") != token) return@runCatching
                    val payload = json.getJSONObject("payload").toString()
                    val snapshot = FormDraftCodec.snapshot(payload) ?: return@runCatching
                    val uri = Uri.parse(snapshot.url)
                    if (snapshot.url != webView.url || uri.scheme != origin.scheme ||
                        uri.host != origin.host || effectivePort(uri) != effectivePort(origin)) return@runCatching
                    listener?.invoke(payload)
                }
            }
            installed = true
        }
    }

    fun setListener(next: ((String) -> Unit)?) {
        if (next == null) {
            disable()
            return
        }
        if (!installed) return
        if (listener == null) token = UUID.randomUUID().toString()
        listener = next
        if (handler == null) handler = WebViewCompat.addDocumentStartJavaScript(webView, script(), setOf("*"))
        webView.evaluateJavascript(script(), null)
    }

    fun restore(payload: String, onComplete: (Boolean) -> Unit) {
        if (!installed || listener == null || payload.length > FormDraftRules.MAX_WIRE_CHARS) {
            onComplete(false)
            return
        }
        webView.evaluateJavascript("globalThis.__candyFormDrafts?.restore($payload) === true") {
            onComplete(it == "true")
        }
    }

    fun close() {
        disable()
        if (installed) WebViewCompat.removeWebMessageListener(webView, BRIDGE)
        installed = false
    }

    private fun disable() {
        val wasEnabled = listener != null
        listener = null
        token = UUID.randomUUID().toString()
        handler?.remove()
        handler = null
        if (installed && wasEnabled) {
            webView.evaluateJavascript(
                "globalThis.__candyFormDrafts?.close(); delete globalThis.__candyFormDrafts; " +
                    "delete globalThis.__candyFormDraftToken",
                null,
            )
        }
    }

    private fun script(): String = """
        (() => {
          if (self !== top || !location.protocol.match(/^https?:$/)) return;
          const token = ${JSONObject.quote(token)};
          if (globalThis.__candyFormDrafts && globalThis.__candyFormDraftToken === token) {
            globalThis.__candyFormDrafts.setEnabled(true);
            return;
          }
          globalThis.__candyFormDrafts?.close();
          $source
          globalThis.__candyFormDrafts = CandyFormDraftFactory.create(window, payload => {
            $BRIDGE.postMessage(JSON.stringify({token, payload}));
          });
          globalThis.__candyFormDraftToken = token;
          globalThis.__candyFormDrafts.setEnabled(true);
        })()
    """.trimIndent()

    private fun effectivePort(uri: Uri): Int = uri.port.takeIf { it >= 0 }
        ?: if (uri.scheme == "https") 443 else 80

    private companion object {
        const val BRIDGE = "CandyFormDraftBridge"
    }
}
