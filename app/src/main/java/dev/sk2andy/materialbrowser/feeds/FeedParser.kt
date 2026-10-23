package dev.sk2andy.materialbrowser.feeds

import java.io.StringReader
import java.net.URI
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

internal data class FeedXmlNode(
    val name: String,
    val attributes: Map<String, String>,
    val children: MutableList<FeedXmlNode> = mutableListOf(),
    val text: StringBuilder = StringBuilder(),
) {
    fun child(name: String): FeedXmlNode? = children.firstOrNull { it.name == name }

    fun value(name: String): String = child(name)?.text?.toString().orEmpty()
}

/** Bounded parser; DTDs and entities are rejected before invoking the platform XML reader. */
internal object FeedXml {
    fun parse(xml: String): FeedXmlNode {
        require(xml.toByteArray().size <= FeedRules.MAX_FEED_BYTES) { "Feed too large" }
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) {
            "Unsafe XML"
        }
        val root = FeedXmlNode("document", emptyMap())
        val stack = mutableListOf(root)
        var nodes = 0
        val parser = SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser()
        parser.xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false)
        parser.xmlReader.setFeature(
            "http://xml.org/sax/features/external-parameter-entities",
            false,
        )
        parser.parse(
            InputSource(StringReader(xml.removePrefix("\uFEFF"))),
            object : DefaultHandler() {
                override fun startElement(
                    uri: String?,
                    localName: String?,
                    qName: String?,
                    attributes: Attributes,
                ) {
                    require(stack.size < 64 && ++nodes <= 30_000) { "XML limit exceeded" }
                    val node =
                        FeedXmlNode(
                            localName
                                .orEmpty()
                                .ifBlank { qName.orEmpty().substringAfter(':') }
                                .lowercase(),
                            (0 until attributes.length).associate {
                                attributes.getQName(it).lowercase() to attributes.getValue(it)
                            },
                        )
                    stack.last().children.add(node)
                    stack.add(node)
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    stack.last().text.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    val node = stack.removeAt(stack.lastIndex)
                    // Preserve readable XHTML content without ever rendering executable feed HTML.
                    if (stack.size > 1) stack.last().text.append(' ').append(node.text).append(' ')
                }

                override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
                    InputSource(StringReader(""))
            },
        )
        return root.children.single()
    }
}

object FeedParser {
    fun parse(xml: String, feedUrl: String): FeedPreview {
        val url = requireNotNull(FeedRules.safeUrl(feedUrl)) { "Invalid feed URL" }
        val root = FeedXml.parse(xml)
        val atom = root.name == "feed"
        val channel =
            when (root.name) {
                "feed" -> root
                "rss" -> requireNotNull(root.child("channel")) { "Missing RSS channel" }
                "rdf" -> root.child("channel") ?: root
                else -> throw IllegalArgumentException("Not an RSS or Atom feed")
            }
        val rootBase = resolve(url, root.attributes["xml:base"].orEmpty()).ifBlank { url }
        val channelBase =
            if (channel === root) rootBase
            else resolve(rootBase, channel.attributes["xml:base"].orEmpty()).ifBlank { rootBase }
        val title = plain(channel.value("title")).take(500).ifBlank { URI(url).host.orEmpty() }
        val siteUrl =
            if (atom) atomLink(channel, channelBase)
            else resolve(channelBase, channel.value("link"))
        val sourceId = FeedRules.identity(url)
        val nodes =
            (if (root.name == "rdf") root else channel).children.filter {
                it.name == if (atom) "entry" else "item"
            }
        val articles =
            nodes
                .take(FeedRules.MAX_ITEMS_PER_FEED)
                .mapNotNull { node ->
                    val entryBase =
                        resolve(channelBase, node.attributes["xml:base"].orEmpty()).ifBlank {
                            channelBase
                        }
                    val articleUrl =
                        if (atom) atomLink(node, entryBase)
                        else
                            resolve(entryBase, node.value("link")).ifBlank {
                                node
                                    .child("guid")
                                    ?.takeUnless {
                                        it.attributes["ispermalink"].equals("false", true)
                                    }
                                    ?.text
                                    ?.toString()
                                    ?.let { resolve(entryBase, it) }
                                    .orEmpty()
                            }
                    if (articleUrl.isBlank()) return@mapNotNull null
                    val summary = plain(node.value(if (atom) "summary" else "description"))
                    val body = plain(node.value(if (atom) "content" else "encoded"))
                    val content = body.ifBlank { summary }
                    FeedArticle(
                        id = FeedRules.identity(articleUrl),
                        sourceIds = setOf(sourceId),
                        url = articleUrl,
                        title = plain(node.value("title")).take(1_000).ifBlank { articleUrl },
                        author =
                            plain(
                                    node.child("author")?.value("name").orEmpty().ifBlank {
                                        node.value("creator").ifBlank { node.value("author") }
                                    }
                                )
                                .take(500),
                        summary = summary.take(4_000),
                        content = content.take(FeedRules.MAX_TEXT_CHARS),
                        contentIsSummary = body.isBlank(),
                        publishedAtMillis =
                            date(
                                node.value(if (atom) "published" else "pubdate").ifBlank {
                                    node.value("updated").ifBlank { node.value("date") }
                                }
                            ),
                    )
                }
                .distinctBy { it.id }
        return FeedPreview(url, title, siteUrl, articles)
    }

    fun discover(html: String, pageUrl: String): List<FeedCandidate> =
        Regex("<link\\b[^>]{0,8192}>", RegexOption.IGNORE_CASE)
            .findAll(html.take(FeedRules.MAX_FEED_BYTES))
            .mapNotNull { match ->
                val attrs =
                    Regex("([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
                        .findAll(match.value)
                        .associate {
                            it.groupValues[1].lowercase() to
                                it.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
                        }
                if (
                    !attrs["rel"].orEmpty().split(Regex("\\s+")).any {
                        it.equals("alternate", true)
                    }
                )
                    return@mapNotNull null
                if (
                    attrs["type"].orEmpty().lowercase().substringBefore(';') !in
                        setOf("application/rss+xml", "application/atom+xml", "application/rdf+xml")
                )
                    return@mapNotNull null
                val url = resolve(pageUrl, entities(attrs["href"].orEmpty()))
                if (url.isBlank()) null
                else FeedCandidate(url, plain(attrs["title"].orEmpty()).take(500).ifBlank { url })
            }
            .distinctBy { it.url }
            .take(32)
            .toList()

    internal fun plain(value: String): String =
        entities(
                value
                    .replace(
                        Regex(
                            "<(script|style)\\b[^>]*>.*?</\\1\\s*>",
                            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                        ),
                        " ",
                    )
                    .replace(Regex("<[^>]*>"), " ")
            )
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun entities(value: String): String =
        Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);").replace(value) { match ->
            when (val token = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else ->
                    runCatching {
                            String(
                                Character.toChars(
                                    if (token.startsWith("#x")) token.drop(2).toInt(16)
                                    else token.drop(1).toInt()
                                )
                            )
                        }
                        .getOrDefault(" ")
            }
        }

    private fun atomLink(node: FeedXmlNode, base: String): String =
        node.children
            .filter {
                it.name == "link" && it.attributes["rel"].orEmpty() in setOf("", "alternate")
            }
            .firstNotNullOfOrNull { link ->
                val linkBase = resolve(base, link.attributes["xml:base"].orEmpty()).ifBlank { base }
                resolve(linkBase, link.attributes["href"].orEmpty()).takeIf(String::isNotBlank)
            }
            .orEmpty()

    internal fun resolve(base: String, value: String): String =
        runCatching {
                if (value.isBlank()) ""
                else FeedRules.safeUrl(URI(base).resolve(value.trim()).toString()).orEmpty()
            }
            .getOrDefault("")

    private fun date(value: String): Long =
        runCatching { Instant.parse(value.trim()).toEpochMilli() }
            .recoverCatching {
                ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
            }
            .getOrDefault(0L)
            .coerceAtLeast(0L)
}
