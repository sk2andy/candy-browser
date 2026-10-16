package dev.sk2andy.materialbrowser.recall

import org.json.JSONArray
import org.json.JSONObject

internal object RecallExtractionScript {
    val javascript: String = javascript(maxTextChars = 70_000)

    fun javascript(maxTextChars: Int): String {
        require(maxTextChars in 1..70_000)
        return """
        (() => {
          const root = document.body;
          if (!root) return JSON.stringify({error: 'missing-root'});
          const clean = value => (value || '').replace(/[\u0000-\u001f\u007f]+/g, ' ').replace(/\s+/g, ' ').trim();
          const excluded = 'script,style,noscript,template,iframe,object,embed,canvas,svg,form,input,textarea,select,button,[contenteditable],nav,aside,footer,video,audio,[hidden],[aria-hidden="true"]';
          const readable = new WeakMap();
          const hasReadableAncestors = element => {
            const checked = [];
            let current = element;
            let allowed = true;
            let depthLimitReached = false;
            while (current) {
              if (readable.has(current)) {
                allowed = readable.get(current);
                break;
              }
              if (checked.length >= 128) {
                allowed = false;
                depthLimitReached = true;
                break;
              }
              checked.push(current);
              const style = getComputedStyle(current);
              if (current.matches(excluded) || style.display === 'none' ||
                  style.visibility === 'hidden' || style.visibility === 'collapse' ||
                  style.opacity === '0' || style.contentVisibility === 'hidden') {
                allowed = false;
                break;
              }
              current = current.parentElement;
            }
            // A depth rejection applies only to this path, not unrelated sibling paths.
            if (depthLimitReached) readable.set(element, false);
            else checked.forEach(ancestor => readable.set(ancestor, allowed));
            return allowed;
          };
          const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
          const parts = [];
          let length = 0;
          let visited = 0;
          let node;
          while (visited < 20000 && length < $maxTextChars && (node = walker.nextNode())) {
            visited += 1;
            const parent = node.parentElement;
            if (!parent || parent.getClientRects().length === 0 || !hasReadableAncestors(parent)) continue;
            const text = clean(node.nodeValue);
            if (!text) continue;
            const remaining = $maxTextChars - length;
            parts.push(text.slice(0, remaining));
            length += Math.min(text.length, remaining) + 1;
          }
          return JSON.stringify({
            title: (clean(document.querySelector('meta[property="og:title"]')?.content) || clean(document.title)).slice(0, 1024),
            sourceUrl: location.href.slice(0, 8192),
            text: clean(parts.join(' ')).slice(0, $maxTextChars)
          });
        })()
        """.trimIndent()
    }
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
