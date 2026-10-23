package dev.sk2andy.materialbrowser.feeds

import android.util.JsonReader
import android.util.JsonToken
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import java.io.IOException
import java.io.Reader
import org.json.JSONArray
import org.json.JSONObject

/** Reads one article at a time, including migration of older inline offline documents. */
internal object FeedJsonStream {
    fun read(input: Reader, saveLegacyDocument: (ReaderDocument) -> String): FeedState {
        val root = JSONObject()
        val articles = mutableListOf<FeedArticle>()
        var metadataBytes = 0L
        var articleCount = 0
        val seen = mutableSetOf<String>()
        JsonReader(StringBoundedReader(input)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(name.length <= 64 && seen.size < 32) { "Too many feed metadata fields" }
                require(seen.add(name)) { "Duplicate feed metadata field" }
                if (name == "articles") {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        require(++articleCount <= MAX_ARTICLES) { "Too many saved articles" }
                        val value =
                            readValue(reader, 0, ValueBudget()) as? JSONObject
                                ?: throw IOException("Invalid article metadata")
                        value.optJSONObject("offlineDocument")?.let { inline ->
                            val document =
                                FeedCodec.readDocument(inline)
                                    ?: throw IOException("Invalid offline document")
                            require(
                                FeedRules.identity(document.sourceUrl) ==
                                    FeedRules.identity(value.optString("url"))
                            ) {
                                "Offline article identity mismatch"
                            }
                            value.put("offlineDocumentId", saveLegacyDocument(document))
                            value.remove("offlineDocument")
                        }
                        metadataBytes += value.toString().toByteArray(Charsets.UTF_8).size + 1L
                        require(metadataBytes <= FeedCodec.MAX_STORE_BYTES) {
                            "Feed metadata too large"
                        }
                        FeedCodec.readArticle(value)?.let(articles::add)
                    }
                    reader.endArray()
                } else if (name in ROOT_FIELDS) {
                    root.put(name, readValue(reader, 0, ValueBudget()))
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing feed metadata" }
        }
        metadataBytes += root.toString().toByteArray(Charsets.UTF_8).size + 16L
        require(metadataBytes <= FeedCodec.MAX_STORE_BYTES) { "Feed metadata too large" }
        return FeedCodec.decode(root, articles.distinctBy { it.id })
    }

    private fun readValue(reader: JsonReader, depth: Int, budget: ValueBudget): Any {
        require(depth < 16 && ++budget.nodes < 100_000) { "Feed JSON nesting limit exceeded" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT ->
                JSONObject().apply {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        budget.add(name.length)
                        put(name, readValue(reader, depth + 1, budget))
                    }
                    reader.endObject()
                }
            JsonToken.BEGIN_ARRAY ->
                JSONArray().apply {
                    reader.beginArray()
                    while (reader.hasNext()) put(readValue(reader, depth + 1, budget))
                    reader.endArray()
                }
            JsonToken.STRING -> reader.nextString().also { budget.add(it.length) }
            JsonToken.NUMBER ->
                reader.nextString().let { value ->
                    budget.add(value.length)
                    value.toLongOrNull()
                        ?: value.toDoubleOrNull()?.takeIf(Double::isFinite)
                        ?: throw IOException("Invalid JSON number")
                }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> {
                reader.nextNull()
                JSONObject.NULL
            }
            else -> throw IOException("Invalid feed JSON")
        }
    }

    private class ValueBudget {
        var nodes = 0
        private var chars = 0

        fun add(count: Int) {
            chars += count
            require(chars <= 6 * 1024 * 1024) { "Feed JSON value too large" }
        }
    }

    /** Prevent nextString() from allocating a hostile multi-megabyte token before validation. */
    private class StringBoundedReader(private val source: Reader) : Reader() {
        private var inString = false
        private var escaped = false
        private var stringChars = 0
        private var unquotedChars = 0
        private var depth = 0

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            val count = source.read(buffer, offset, length)
            if (count <= 0) return count
            for (index in offset until offset + count) {
                val char = buffer[index]
                if (!inString) {
                    when {
                        char == '"' -> {
                            inString = true
                            stringChars = 0
                            unquotedChars = 0
                        }
                        char == '[' || char == '{' -> {
                            if (++depth > 16) throw IOException("Feed JSON nesting limit exceeded")
                            unquotedChars = 0
                        }
                        char == ']' || char == '}' -> {
                            depth--
                            unquotedChars = 0
                        }
                        char.isWhitespace() || char == ',' || char == ':' -> unquotedChars = 0
                        else ->
                            if (++unquotedChars > 128)
                                throw IOException("Feed JSON token too large")
                    }
                } else {
                    if (++stringChars > FeedRules.MAX_TEXT_CHARS * 6 + 2) {
                        throw IOException("Feed JSON string too large")
                    }
                    if (escaped) escaped = false
                    else if (char == '\\') escaped = true else if (char == '"') inString = false
                }
            }
            return count
        }

        override fun close() = source.close()
    }

    private val ROOT_FIELDS =
        setOf("version", "sources", "folders", "searches", "rules", "settings", "queue")
    private const val MAX_ARTICLES = 20_000
}
