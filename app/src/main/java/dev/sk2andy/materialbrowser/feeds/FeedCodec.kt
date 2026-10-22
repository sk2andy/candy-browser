package dev.sk2andy.materialbrowser.feeds

import dev.sk2andy.materialbrowser.reader.ReaderBlock
import dev.sk2andy.materialbrowser.reader.ReaderBlockKind
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderLink
import org.json.JSONArray
import org.json.JSONObject

internal object FeedCodec {
    const val MAX_STORE_BYTES = 32 * 1024 * 1024

    fun encode(state: FeedState): String = buildString { write(state, this) }

    /** Stream metadata entry by entry; never allocate one JSON object for the whole library. */
    fun write(state: FeedState, output: Appendable) {
        output.append("{\"version\":1,")
        fun <T> list(name: String, values: List<T>, encode: (T) -> JSONObject) {
            output.append(JSONObject.quote(name)).append(":[")
            values.forEachIndexed { index, value ->
                if (index > 0) output.append(',')
                output.append(encode(value).toString())
            }
            output.append("],")
        }
        list("sources", state.sources) { source ->
            obj(
                "id" to source.id,
                "url" to source.url,
                "title" to source.title,
                "siteUrl" to source.siteUrl,
                "folderId" to source.folderId,
                "readingMode" to source.readingMode.name,
                "lastCheckedMillis" to source.lastCheckedMillis,
                "lastSuccessMillis" to source.lastSuccessMillis,
                "error" to source.error,
                "notificationsEnabled" to source.notificationsEnabled,
            )
        }
        list("folders", state.folders) { obj("id" to it.id, "name" to it.name) }
        list("articles", state.articles, ::article)
        list("searches", state.searches) {
            obj(
                "id" to it.id,
                "name" to it.name,
                "query" to it.query,
                "sourceId" to it.sourceId,
                "folderId" to it.folderId,
                "unreadOnly" to it.unreadOnly,
                "tag" to it.tag,
            )
        }
        list("rules", state.rules) {
            obj(
                "id" to it.id,
                "name" to it.name,
                "query" to it.query,
                "action" to it.action.name,
                "sourceId" to it.sourceId,
                "folderId" to it.folderId,
                "tag" to it.tag,
                "enabled" to it.enabled,
            )
        }
        output
            .append("\"settings\":")
            .append(
                state.settings
                    .let {
                        obj(
                            "offlineBudgetMb" to it.offlineBudgetMb,
                            "wifiOnly" to it.wifiOnly,
                            "refreshOnOpen" to it.refreshOnOpen,
                            "backgroundRefresh" to it.backgroundRefresh,
                            "digestEnabled" to it.digestEnabled,
                            "digestHour" to it.digestHour,
                            "quietStartHour" to it.quietStartHour,
                            "quietEndHour" to it.quietEndHour,
                        )
                    }
                    .toString()
            )
        output.append(",\"queue\":").append(JSONArray(state.queue).toString()).append('}')
    }

    private fun article(article: FeedArticle): JSONObject =
        obj(
            "id" to article.id,
            "sourceIds" to JSONArray(article.sourceIds.toList()),
            "url" to article.url,
            "title" to article.title,
            "origin" to article.origin.name,
            "author" to article.author,
            "websiteSnapshotId" to article.websiteSnapshotId,
            "summary" to article.summary,
            "content" to article.content,
            "publishedAtMillis" to article.publishedAtMillis,
            "receivedAtMillis" to article.receivedAtMillis,
            "updatedAtMillis" to article.updatedAtMillis,
            "previousContent" to article.previousContent,
            "isRead" to article.isRead,
            "readingProgress" to article.readingProgress,
            "isStarred" to article.isStarred,
            "isHidden" to article.isHidden,
            "tags" to JSONArray(article.tags.toList()),
            "offlineStatus" to article.offlineStatus.name,
            "offlineDocument" to article.offlineDocument?.let(::document),
            "offlineDocumentId" to article.offlineDocumentId,
        )

    fun decode(json: String): FeedState = decode(JSONObject(json))

    fun decode(root: JSONObject, decodedArticles: List<FeedArticle>? = null): FeedState {
        require(root.optInt("version") == 1) { "Unsupported feeds version" }
        val sources =
            root
                .objects("sources", FeedRules.MAX_SOURCES)
                .mapNotNull { source ->
                    val url =
                        FeedRules.safeUrl(source.string("url", 2048)) ?: return@mapNotNull null
                    FeedSource(
                        id = FeedRules.identity(url),
                        url = url,
                        title = source.string("title", 500),
                        siteUrl = FeedRules.safeUrl(source.string("siteUrl", 2048)).orEmpty(),
                        folderId = source.optional("folderId"),
                        readingMode = enumValue(source, "readingMode", FeedReadingMode.Feed),
                        lastCheckedMillis = source.optLong("lastCheckedMillis").coerceAtLeast(0),
                        lastSuccessMillis = source.optLong("lastSuccessMillis").coerceAtLeast(0),
                        error = source.optional("error", 500),
                        notificationsEnabled = source.optBoolean("notificationsEnabled"),
                    )
                }
                .distinctBy { it.id }
        val articles =
            decodedArticles
                ?: root.objects("articles", 20_000).mapNotNull(::readArticle).distinctBy { it.id }
        val settings = root.optJSONObject("settings") ?: JSONObject()
        return FeedState(
            sources = sources,
            folders =
                root
                    .objects("folders", 500)
                    .map { FeedFolder(it.string("id", 128), it.string("name", 100)) }
                    .distinctBy { it.id },
            articles = articles,
            searches =
                root.objects("searches", 100).map {
                    FeedSearch(
                        it.string("id", 128),
                        it.string("name", 100),
                        it.string("query", 500),
                        it.optional("sourceId"),
                        it.optional("folderId"),
                        it.optBoolean("unreadOnly"),
                        it.optional("tag", 80),
                    )
                },
            rules =
                root.objects("rules", 100).map {
                    FeedRule(
                        it.string("id", 128),
                        it.string("name", 100),
                        it.string("query", 500),
                        enumValue(it, "action", FeedRuleAction.MarkRead),
                        it.optional("sourceId"),
                        it.optional("folderId"),
                        it.string("tag", 80),
                        it.optBoolean("enabled", true),
                    )
                },
            settings =
                FeedSettings(
                    settings.optInt("offlineBudgetMb", 250).coerceIn(10, 250),
                    settings.optBoolean("wifiOnly", true),
                    settings.optBoolean("refreshOnOpen", true),
                    settings.optBoolean("backgroundRefresh"),
                    settings.optBoolean("digestEnabled"),
                    settings.optInt("digestHour", 18).coerceIn(0, 23),
                    settings.optInt("quietStartHour", 22).coerceIn(0, 23),
                    settings.optInt("quietEndHour", 8).coerceIn(0, 23),
                ),
            queue =
                root.strings("queue", 500).filter { id -> articles.any { it.id == id } }.distinct(),
            loaded = true,
        )
    }

    fun readArticle(article: JSONObject): FeedArticle? {
        val url = FeedRules.safeUrl(article.string("url", 2048)) ?: return null
        val document =
            article.optJSONObject("offlineDocument")?.let(::readDocument)?.takeIf {
                FeedRules.identity(it.sourceUrl) == FeedRules.identity(url)
            }
        val reference = article.optional("offlineDocumentId")?.takeIf(::validDocumentId)
        return FeedArticle(
            id = FeedRules.identity(url),
            sourceIds = article.strings("sourceIds", 500).toSet(),
            url = url,
            title = article.string("title", 1000),
            origin = enumValue(article, "origin", FeedArticleOrigin.Feed),
            websiteSnapshotId = article.optional("websiteSnapshotId", 256),
            author = article.string("author", 500),
            summary = article.string("summary", 4000),
            content = article.string("content", FeedRules.MAX_TEXT_CHARS),
            publishedAtMillis = article.optLong("publishedAtMillis").coerceAtLeast(0),
            receivedAtMillis = article.optLong("receivedAtMillis").coerceAtLeast(0),
            updatedAtMillis = article.optLong("updatedAtMillis").coerceAtLeast(0),
            previousContent = article.optional("previousContent", FeedRules.MAX_TEXT_CHARS),
            isRead = article.optBoolean("isRead"),
            readingProgress =
                article.optDouble("readingProgress", 0.0).toFloat().let {
                    if (it.isFinite()) it.coerceIn(0f, 1f) else 0f
                },
            isStarred = article.optBoolean("isStarred"),
            isHidden = article.optBoolean("isHidden"),
            tags = article.strings("tags", 50, 80).toSet(),
            offlineStatus =
                if (document != null || reference != null) FeedOfflineStatus.Ready
                else
                    enumValue(article, "offlineStatus", FeedOfflineStatus.None).let {
                        when (it) {
                            FeedOfflineStatus.Ready -> FeedOfflineStatus.Failed
                            FeedOfflineStatus.Downloading -> FeedOfflineStatus.Waiting
                            else -> it
                        }
                    },
            offlineDocument = document,
            offlineDocumentId = reference,
        )
    }

    fun validDocumentId(value: String): Boolean =
        value.matches(Regex("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))

    fun document(value: ReaderDocument): JSONObject =
        obj(
            "title" to value.title,
            "sourceUrl" to value.sourceUrl,
            "siteName" to value.siteName,
            "blocks" to
                array(
                    value.blocks.map { block ->
                        obj(
                            "kind" to block.kind.name,
                            "text" to block.text,
                            "level" to block.level,
                            "links" to
                                array(block.links.map { obj("label" to it.label, "url" to it.url) }),
                        )
                    }
                ),
        )

    fun readDocument(value: JSONObject): ReaderDocument? {
        val url = FeedRules.safeUrl(value.string("sourceUrl", 2048)) ?: return null
        var remaining = 500_000
        val blocks =
            value.objects("blocks", 600).mapNotNull { block ->
                val text = block.string("text", minOf(12_000, remaining))
                remaining -= text.length
                if (text.isBlank()) null
                else
                    ReaderBlock(
                        enumValue(block, "kind", ReaderBlockKind.Paragraph),
                        text,
                        block.optInt("level").coerceIn(0, 6),
                        block.objects("links", 100).mapNotNull { link ->
                            FeedRules.safeUrl(link.string("url", 2048))?.let {
                                ReaderLink(link.string("label", 1000), it)
                            }
                        },
                    )
            }
        return ReaderDocument(
            value.string("title", 1000),
            url,
            value.string("siteName", 500),
            blocks,
        )
    }

    private fun obj(vararg values: Pair<String, Any?>): JSONObject =
        JSONObject().apply { values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }

    private fun array(values: List<JSONObject>) = JSONArray().apply { values.forEach(::put) }

    private fun JSONObject.string(key: String, max: Int): String = optString(key, "").take(max)

    private fun JSONObject.optional(key: String, max: Int = 128): String? =
        if (isNull(key)) null else string(key, max).takeIf(String::isNotBlank)

    private fun JSONObject.objects(key: String, max: Int): List<JSONObject> =
        optJSONArray(key)
            ?.let { array -> (0 until minOf(array.length(), max)).mapNotNull(array::optJSONObject) }
            .orEmpty()

    private fun JSONObject.strings(key: String, max: Int, length: Int = 128): List<String> =
        optJSONArray(key)
            ?.let { array ->
                (0 until minOf(array.length(), max))
                    .map { array.optString(it).take(length) }
                    .filter(String::isNotBlank)
            }
            .orEmpty()

    private inline fun <reified T : Enum<T>> enumValue(
        value: JSONObject,
        key: String,
        default: T,
    ): T = enumValues<T>().firstOrNull { it.name == value.optString(key) } ?: default
}
