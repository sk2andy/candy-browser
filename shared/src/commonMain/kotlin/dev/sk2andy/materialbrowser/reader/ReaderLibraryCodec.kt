package dev.sk2andy.materialbrowser.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Shared versioned wire format used by Android AtomicFile and Apple's atomic text adapter. */
object ReaderLibraryCodec {
    const val CURRENT_VERSION = 1
    const val MAX_JSON_BYTES = 8 * 1_024 * 1_024
    private val snapshotIdPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun decode(raw: String?): ReaderLibraryState {
        if (raw == null || raw.length > MAX_JSON_BYTES || raw.encodeToByteArray().size > MAX_JSON_BYTES) {
            return ReaderLibraryState()
        }
        return runCatching {
            val root = Json.parseToJsonElement(raw) as? JsonObject ?: return ReaderLibraryState()
            if (root.primitive("version")?.intOrNull != CURRENT_VERSION) return ReaderLibraryState()
            val settingsJson = root["settings"] as? JsonObject ?: JsonObject(emptyMap())
            val settings = ReaderSettings(
                fontScale = finiteFloat(settingsJson.primitive("fontScale")?.floatOrNull, 1f)
                    .coerceIn(0.8f, 1.6f),
                theme = ReaderTheme.entries.firstOrNull { it.name == settingsJson.string("theme") }
                    ?: ReaderTheme.Paper,
                textAlignment = ReaderTextAlignment.entries.firstOrNull {
                    it.name == settingsJson.string("textAlignment")
                } ?: ReaderTextAlignment.Start,
                font = ReaderFont.entries.firstOrNull { it.name == settingsJson.string("font") }
                    ?: ReaderFont.Sans,
            )
            val progress = (root["progress"] as? JsonObject).orEmpty().entries.asSequence()
                .filter { ReaderExtractionContract.safeHttpUrl(it.key) != null }
                .map { it.key to finiteFloat((it.value as? JsonPrimitive)?.floatOrNull, 0f).coerceIn(0f, 1f) }
                .take(ReaderLibraryRules.MAX_PROGRESS_ENTRIES)
                .toMap()
            val snapshots = (root["snapshots"] as? JsonArray).orEmpty()
                .take(ReaderLibraryRules.MAX_SNAPSHOTS)
                .mapNotNull { decodeSnapshot(it as? JsonObject ?: return@mapNotNull null) }
            ReaderLibraryState(settings, progress, snapshots)
        }.getOrDefault(ReaderLibraryState())
    }

    fun encode(state: ReaderLibraryState): String = buildJsonObject {
        put("version", CURRENT_VERSION)
        put("settings", buildJsonObject {
            put("fontScale", finiteFloat(state.settings.fontScale, 1f).coerceIn(0.8f, 1.6f))
            put("theme", state.settings.theme.name)
            put("textAlignment", state.settings.textAlignment.name)
            put("font", state.settings.font.name)
        })
        put("progress", buildJsonObject {
            state.progressByUrl.entries.toList().takeLast(ReaderLibraryRules.MAX_PROGRESS_ENTRIES).forEach { (url, progress) ->
                if (ReaderExtractionContract.safeHttpUrl(url) != null) {
                    put(url, finiteFloat(progress, 0f).coerceIn(0f, 1f))
                }
            }
        })
        put("snapshots", JsonArray(state.snapshots.filterNot(ReaderSnapshot::isPrivate).map(::encodeSnapshot)))
    }.toString()

    fun bounded(state: ReaderLibraryState): ReaderLibraryState {
        val publicState = state.copy(snapshots = state.snapshots.filterNot(ReaderSnapshot::isPrivate))
        val snapshots = ReaderStorageBudget.evictOldestUntil(
            newestFirst = publicState.snapshots,
            maxBytes = MAX_JSON_BYTES,
        ) { encode(publicState.copy(snapshots = it)).encodeToByteArray().size }
        return publicState.copy(snapshots = snapshots)
    }

    fun validSnapshotId(value: String): Boolean = snapshotIdPattern.matches(value)

    fun sanitizeDocument(document: ReaderDocument): ReaderDocument? = sanitizedDocument(
        ReaderExtractionPayload(
            title = document.title,
            sourceUrl = document.sourceUrl,
            siteName = document.siteName,
            blocks = document.blocks.map { block ->
                ReaderExtractionBlock(
                    kind = block.kind.name,
                    text = block.text,
                    level = block.level,
                    links = block.links.map { ReaderExtractionLink(it.label, it.url) },
                )
            },
        ),
    )

    private fun decodeSnapshot(json: JsonObject): ReaderSnapshot? {
        if (json.primitive("isPrivate")?.booleanOrNull == true) return null
        val id = json.string("id")?.takeIf(::validSnapshotId) ?: return null
        val documentJson = json["document"] as? JsonObject ?: return null
        val blocks = (documentJson["blocks"] as? JsonArray).orEmpty()
            .take(ReaderExtractionContract.MAX_BLOCKS).mapNotNull { raw ->
                val block = raw as? JsonObject ?: return@mapNotNull null
                val links = (block["links"] as? JsonArray).orEmpty().take(40).mapNotNull linkLoop@ { rawLink ->
                    val link = rawLink as? JsonObject ?: return@linkLoop null
                    ReaderExtractionLink(link.string("label"), link.string("url"))
                }
                ReaderExtractionBlock(
                    kind = block.string("kind"),
                    text = block.string("text"),
                    level = block.primitive("level")?.intOrNull ?: 0,
                    links = links,
                )
            }
        val document = sanitizedDocument(
            ReaderExtractionPayload(
                title = documentJson.string("title"),
                sourceUrl = documentJson.string("sourceUrl"),
                siteName = documentJson.string("siteName"),
                blocks = blocks,
            ),
        ) ?: return null
        return ReaderSnapshot(
            id = id,
            document = document,
            progress = finiteFloat(json.primitive("progress")?.floatOrNull, 0f).coerceIn(0f, 1f),
            savedAtMillis = (json.primitive("savedAt")?.longOrNull ?: 0L).coerceAtLeast(0L),
            sourceProfileId = json.string("sourceProfileId")?.takeIf {
                it.isNotBlank() && it.length <= 128 && it.none { character -> character.code < 32 || character.code in 127..159 }
            },
        )
    }

    private fun encodeSnapshot(snapshot: ReaderSnapshot): JsonObject = buildJsonObject {
        put("id", snapshot.id)
        put("progress", finiteFloat(snapshot.progress, 0f).coerceIn(0f, 1f))
        put("savedAt", snapshot.savedAtMillis)
        put("sourceProfileId", snapshot.sourceProfileId?.let(::JsonPrimitive) ?: JsonNull)
        put("isPrivate", false)
        put("document", buildJsonObject {
            put("title", snapshot.document.title)
            put("sourceUrl", snapshot.document.sourceUrl)
            put("siteName", snapshot.document.siteName)
            put("blocks", JsonArray(snapshot.document.blocks.map { block ->
                buildJsonObject {
                    put("kind", block.kind.name)
                    put("text", block.text)
                    put("level", block.level)
                    put("links", JsonArray(block.links.map { link ->
                        buildJsonObject {
                            put("label", link.label)
                            put("url", link.url)
                        }
                    }))
                }
            }))
        })
    }

    private fun sanitizedDocument(payload: ReaderExtractionPayload): ReaderDocument? =
        (ReaderExtractionContract.sanitize(payload) as? ReaderExtractionResult.Success)?.document

    private fun finiteFloat(value: Float?, fallback: Float): Float = value?.takeIf(Float::isFinite) ?: fallback

    private fun JsonObject.primitive(key: String): JsonPrimitive? = get(key) as? JsonPrimitive

    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf(JsonPrimitive::isString)?.content
}
