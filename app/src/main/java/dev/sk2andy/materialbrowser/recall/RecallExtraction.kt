package dev.sk2andy.materialbrowser.recall

import org.json.JSONArray
import org.json.JSONObject

internal object RecallExtractionScript {
    val javascript: String = """
        (() => {
          const root = document.body;
          if (!root) return JSON.stringify({error: 'missing-root'});
          const clean = value => (value || '').replace(/[\u0000-\u001f\u007f]+/g, ' ').replace(/\s+/g, ' ').trim();
          const excluded = 'script,style,noscript,template,iframe,object,embed,canvas,svg,form,input,button,nav,aside,footer,video,audio,[hidden],[aria-hidden="true"]';
          const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
          const parts = [];
          let length = 0;
          let visited = 0;
          let node;
          while (visited < 20000 && length < 70000 && (node = walker.nextNode())) {
            visited += 1;
            const parent = node.parentElement;
            if (!parent || parent.closest(excluded) || parent.getClientRects().length === 0) continue;
            const style = getComputedStyle(parent);
            if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') continue;
            const text = clean(node.nodeValue);
            if (!text) continue;
            const remaining = 70000 - length;
            parts.push(text.slice(0, remaining));
            length += Math.min(text.length, remaining) + 1;
          }
          return JSON.stringify({
            title: clean(document.querySelector('meta[property="og:title"]')?.content) || clean(document.title),
            sourceUrl: location.href,
            text: clean(parts.join(' ')).slice(0, 70000)
          });
        })()
    """.trimIndent()
}

internal object RecallExtractionParser {
    fun parse(
        webViewResult: String?,
        profileId: String,
        expectedUrl: String,
        visitedAt: Long,
    ): RecallDocument? {
        val jsonText = decodeJavascriptString(webViewResult) ?: return null
        return runCatching {
            val root = JSONObject(jsonText)
            if (root.has("error")) return@runCatching null
            val document = RecallRules.sanitizeDocument(
                RecallDocument(
                    profileId = profileId,
                    url = root.optString("sourceUrl"),
                    title = root.optString("title"),
                    text = root.optString("text"),
                    visitedAt = visitedAt,
                ),
            )
            document?.takeIf { it.url == RecallRules.canonicalUrl(expectedUrl) }
        }.getOrNull()
    }

    private fun decodeJavascriptString(result: String?): String? {
        val value = result?.takeIf { candidate ->
            candidate != "null" && candidate.length <= MAX_WEBVIEW_RESULT_CHARS
        } ?: return null
        return runCatching { JSONArray("[$value]").getString(0) }.getOrNull()
    }

    private const val MAX_WEBVIEW_RESULT_CHARS = 150_000
}
