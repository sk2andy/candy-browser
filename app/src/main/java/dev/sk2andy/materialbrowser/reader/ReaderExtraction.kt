package dev.sk2andy.materialbrowser.reader

import org.json.JSONArray
import org.json.JSONObject

object ReaderExtractionScript {
    val javascript: String = """
        (() => {
          const pageRoot = document.body;
          const visibleText = (pageRoot?.innerText || '')
            .replace(/[\u0000-\u001f\u007f]+/g, ' ')
            .replace(/\s+/g, ' ')
            .trim()
            .slice(0, 2000);
          const hasVisibleContent = (() => {
            if (!pageRoot) return false;
            if (visibleText) return true;
            return Array.from(
              pageRoot.querySelectorAll('img,svg,canvas,video,iframe,object,embed')
            ).some(node => {
              if (node.id === 'gt-nvframe' || node.closest('#gt-nvframe,[hidden],[aria-hidden="true"]')) return false;
              const style = getComputedStyle(node);
              if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0' || style.contentVisibility === 'hidden') return false;
              const rect = node.getBoundingClientRect();
              return rect.width >= 32 && rect.height >= 32 && rect.width * rect.height >= 4096;
            });
          })();
          const source = document.querySelector('article') || document.querySelector('main') || document.body;
          if (!source) {
            return JSON.stringify({error: 'missing-root', hasVisibleContent, visibleText});
          }
          const isHidden = node => {
            const style = getComputedStyle(node);
            return node.matches('[hidden],[aria-hidden="true"]') || style.display === 'none' ||
              style.visibility === 'hidden' || style.visibility === 'collapse' ||
              style.opacity === '0' || style.contentVisibility === 'hidden';
          };
          const root = source.cloneNode(true);
          const originals = [source, ...source.querySelectorAll('*')];
          const copies = [root, ...root.querySelectorAll('*')];
          originals.forEach((node, index) => {
            if (!isHidden(node)) return;
            copies[index].replaceChildren();
            copies[index].remove();
          });
          for (let ancestor = source.parentElement; ancestor; ancestor = ancestor.parentElement) {
            if (isHidden(ancestor)) {
              root.replaceChildren();
              break;
            }
          }
          root.querySelectorAll('script,style,noscript,template,iframe,object,embed,canvas,svg,form,input,button,nav,aside,footer,video,audio').forEach(node => node.remove());
          const clean = value => (value || '').replace(/[\u0000-\u001f\u007f]+/g, ' ').replace(/\s+/g, ' ').trim();
          const blocks = [];
          let totalChars = 0;
          let totalLinks = 0;
          const appendBlock = ({node, text: rawText, anchors}) => {
            if (blocks.length >= 600 || totalChars >= 500000) return;
            const text = clean(rawText).slice(0, Math.min(12000, 500000 - totalChars));
            if (!text || text.length < 2) return;
            const tag = node.tagName.toLowerCase();
            const kind = tag.startsWith('h') ? 'heading' : tag === 'blockquote' ? 'quote' : tag === 'li' ? 'listitem' : 'paragraph';
            const links = Array.from(anchors).slice(0, Math.min(40, 500 - totalLinks)).map(anchor => ({
              label: clean(anchor.innerText || anchor.textContent),
              url: anchor.href
            }));
            blocks.push({kind, level: kind === 'heading' ? Number(tag.substring(1)) : 0, text, links});
            totalChars += text.length;
            totalLinks += links.length;
          };
          const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT);
          let textNode;
          let run;
          while ((textNode = walker.nextNode())) {
            if (textNode.nodeType === 1 && textNode.tagName !== 'BR') continue;
            const node = textNode.parentElement.closest('h1,h2,h3,h4,h5,h6,p,blockquote,li');
            if (!node) continue;
            if (run?.node !== node) {
              if (run) appendBlock(run);
              if (blocks.length >= 600 || totalChars >= 500000) {
                run = null;
                break;
              }
              run = {node, text: '', anchors: new Set()};
            }
            run.text += textNode.nodeType === 3 ? textNode.textContent : ' ';
            const anchor = textNode.parentElement.closest('a[href]');
            if (anchor) run.anchors.add(anchor);
          }
          if (run) appendBlock(run);
          return JSON.stringify({
            title: clean(document.querySelector('meta[property="og:title"]')?.content) || clean(document.title),
            siteName: clean(document.querySelector('meta[property="og:site_name"]')?.content) || clean(location.hostname),
            sourceUrl: location.href,
            hasVisibleContent,
            visibleText,
            blocks
          });
        })()
    """.trimIndent()
}

object ReaderExtractionParser {
    fun parse(webViewResult: String?): ReaderExtractionResult {
        val jsonText = decodeJavascriptString(webViewResult)
            ?: return ReaderExtractionResult.Failure(ReaderExtractionFailure.InvalidResponse)
        return parseJson(jsonText)
    }

    internal fun parseJson(jsonText: String?): ReaderExtractionResult {
        val boundedJson = jsonText
            ?.takeIf { value -> value.length <= MAX_JSON_CHARS }
            ?: return ReaderExtractionResult.Failure(ReaderExtractionFailure.InvalidResponse)
        return runCatching {
            val root = JSONObject(boundedJson)
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
        val value = result?.takeIf { it != "null" && it.length <= MAX_JSON_CHARS } ?: return null
        return runCatching { JSONArray("[$value]").getString(0) }.getOrNull()
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private const val MAX_JSON_CHARS = 2_000_000
}
