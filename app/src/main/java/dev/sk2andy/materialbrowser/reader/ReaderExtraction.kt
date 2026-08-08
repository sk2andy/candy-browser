package dev.sk2andy.materialbrowser.reader

import org.json.JSONArray
import org.json.JSONObject

object ReaderExtractionScript {
    val javascript: String = """
        (() => {
          const source = document.querySelector('article') || document.querySelector('main') || document.body;
          if (!source) return JSON.stringify({error: 'missing-root'});
          const root = source.cloneNode(true);
          root.querySelectorAll('script,style,noscript,template,iframe,object,embed,canvas,svg,form,input,button,nav,aside,footer,video,audio').forEach(node => node.remove());
          const clean = value => (value || '').replace(/[\u0000-\u001f\u007f]+/g, ' ').replace(/\s+/g, ' ').trim();
          const blocks = [];
          let totalChars = 0;
          let totalLinks = 0;
          root.querySelectorAll('h1,h2,h3,h4,h5,h6,p,blockquote,li').forEach(node => {
            if (blocks.length >= 600 || totalChars >= 500000) return;
            const text = clean(node.innerText || node.textContent).slice(0, Math.min(12000, 500000 - totalChars));
            if (!text || text.length < 2) return;
            const tag = node.tagName.toLowerCase();
            const kind = tag.startsWith('h') ? 'heading' : tag === 'blockquote' ? 'quote' : tag === 'li' ? 'listitem' : 'paragraph';
            const links = Array.from(node.querySelectorAll('a[href]')).slice(0, Math.min(40, 500 - totalLinks)).map(anchor => ({
              label: clean(anchor.innerText || anchor.textContent),
              url: anchor.href
            }));
            blocks.push({kind, level: kind === 'heading' ? Number(tag.substring(1)) : 0, text, links});
            totalChars += text.length;
            totalLinks += links.length;
          });
          return JSON.stringify({
            title: clean(document.querySelector('meta[property="og:title"]')?.content) || clean(document.title),
            siteName: clean(document.querySelector('meta[property="og:site_name"]')?.content) || clean(location.hostname),
            sourceUrl: location.href,
            blocks
          });
        })()
    """.trimIndent()
}

object ReaderExtractionParser {
    fun parse(webViewResult: String?): ReaderExtractionResult {
        val jsonText = decodeJavascriptString(webViewResult)
            ?: return ReaderExtractionResult.Failure(ReaderExtractionFailure.InvalidResponse)
        return runCatching {
            val root = JSONObject(jsonText)
            if (root.has("error")) {
                return@runCatching ReaderExtractionResult.Failure(
                    ReaderExtractionFailure.EmptyArticle,
                )
            }
            val rawBlocks = root.optJSONArray("blocks") ?: JSONArray()
            val blocks = buildList {
                for (index in 0 until minOf(rawBlocks.length(), ReaderExtractionContract.MAX_BLOCKS)) {
                    val block = rawBlocks.optJSONObject(index) ?: continue
                    val rawLinks = block.optJSONArray("links") ?: JSONArray()
                    val links = buildList {
                        for (linkIndex in 0 until minOf(rawLinks.length(), 40)) {
                            val link = rawLinks.optJSONObject(linkIndex) ?: continue
                            add(
                                ReaderExtractionLink(
                                    label = link.optionalString("label"),
                                    url = link.optionalString("url"),
                                ),
                            )
                        }
                    }
                    add(
                        ReaderExtractionBlock(
                            kind = block.optionalString("kind"),
                            text = block.optionalString("text"),
                            level = block.optInt("level", 0),
                            links = links,
                        ),
                    )
                }
            }
            ReaderExtractionContract.sanitize(
                ReaderExtractionPayload(
                    title = root.optionalString("title"),
                    sourceUrl = root.optionalString("sourceUrl"),
                    siteName = root.optionalString("siteName"),
                    blocks = blocks,
                ),
            )
        }.getOrElse {
            ReaderExtractionResult.Failure(ReaderExtractionFailure.InvalidResponse)
        }
    }

    private fun decodeJavascriptString(result: String?): String? {
        val value = result?.takeIf { it != "null" && it.length <= 2_000_000 } ?: return null
        return runCatching { JSONArray("[$value]").getString(0) }.getOrNull()
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null
}
