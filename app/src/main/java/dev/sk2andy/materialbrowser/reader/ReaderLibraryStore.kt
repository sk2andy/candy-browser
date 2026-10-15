package dev.sk2andy.materialbrowser.reader

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class ReaderLibraryStore internal constructor(private val file: AtomicFile) {
    constructor(context: Context) : this(AtomicFile(File(context.filesDir, FILE_NAME)))

    fun load(isPrivate: Boolean): ReaderLibraryState = synchronized(lock) {
        if (isPrivate) ReaderLibraryState() else read()
    }

    fun updateSettings(settings: ReaderSettings, isPrivate: Boolean) = synchronized(lock) {
        mutate(isPrivate) { ReaderLibraryRules.updateSettings(it, settings, isPrivate = false) }
    }

    fun updateProgress(sourceUrl: String, progress: Float, isPrivate: Boolean) = synchronized(lock) {
        mutate(isPrivate) {
            ReaderLibraryRules.updateProgress(it, sourceUrl, progress, isPrivate = false)
        }
    }

    fun saveSnapshot(
        document: ReaderDocument,
        progress: Float,
        isPrivate: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        sourceProfileId: String? = null,
        preserveExistingProvenance: Boolean = false,
    ): ReaderSnapshot? = synchronized(lock) {
        if (isPrivate) return@synchronized null
        val before = read()
        val existing = before.snapshots.firstOrNull { preserveExistingProvenance && it.document == document }
        if (existing?.isPrivate == true) return@synchronized null
        val snapshot = ReaderSnapshot(
            id = UUID.randomUUID().toString(),
            document = document,
            progress = progress.coerceIn(0f, 1f),
            savedAtMillis = nowMillis,
            sourceProfileId = if (existing != null) existing.sourceProfileId else sourceProfileId?.takeIf {
                it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl)
            },
        )
        val after = ReaderLibraryRules.saveSnapshot(before, snapshot, isPrivate = false)
        if (after != before) write(after)
        snapshot
    }

    fun deleteSnapshot(snapshotId: String, isPrivate: Boolean): Boolean = synchronized(lock) {
        if (isPrivate) return@synchronized false
        val before = read()
        val after = ReaderLibraryRules.deleteSnapshot(before, snapshotId, isPrivate = false)
        if (before == after) return@synchronized false
        write(after)
        true
    }

    fun clear() = synchronized(lock) {
        file.delete()
    }

    private fun mutate(isPrivate: Boolean, transform: (ReaderLibraryState) -> ReaderLibraryState) {
        if (isPrivate) return
        val before = read()
        val after = transform(before)
        if (after != before) write(after)
    }

    private fun read(): ReaderLibraryState {
        if (!file.baseFile.isFile || file.baseFile.length() > MAX_JSON_BYTES) {
            return ReaderLibraryState()
        }
        val bytes = runCatching {
            file.openRead().use { it.readNBytes(MAX_JSON_BYTES + 1) }
        }.getOrNull() ?: return ReaderLibraryState()
        if (bytes.size > MAX_JSON_BYTES) return ReaderLibraryState()
        return runCatching { decode(JSONObject(bytes.toString(StandardCharsets.UTF_8))) }
            .getOrDefault(ReaderLibraryState())
    }

    private fun write(state: ReaderLibraryState) {
        val boundedSnapshots = ReaderStorageBudget.evictOldestUntil(
            newestFirst = state.snapshots,
            maxBytes = MAX_JSON_BYTES,
        ) { snapshots -> encodedBytes(state.copy(snapshots = snapshots)).size }
        val bounded = state.copy(snapshots = boundedSnapshots)
        val bytes = encodedBytes(bounded)
        if (bytes.size > MAX_JSON_BYTES) return
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    private fun encodedBytes(state: ReaderLibraryState): ByteArray =
        encode(state).toString().toByteArray(StandardCharsets.UTF_8)

    private fun encode(state: ReaderLibraryState): JSONObject = JSONObject()
        .put("version", CURRENT_VERSION)
        .put(
            "settings",
            JSONObject()
                .put("fontScale", state.settings.fontScale.toDouble())
                .put("theme", state.settings.theme.name)
                .put("textAlignment", state.settings.textAlignment.name),
        )
        .put(
            "progress",
            JSONObject().apply {
                state.progressByUrl.forEach { (url, progress) -> put(url, progress.toDouble()) }
            },
        )
        .put("snapshots", JSONArray().apply { state.snapshots.forEach { put(encodeSnapshot(it)) } })

    private fun encodeSnapshot(snapshot: ReaderSnapshot): JSONObject = JSONObject()
        .put("id", snapshot.id)
        .put("progress", snapshot.progress.toDouble())
        .put("savedAt", snapshot.savedAtMillis)
        .put("sourceProfileId", snapshot.sourceProfileId)
        .put("isPrivate", snapshot.isPrivate)
        .put("document", encodeDocument(snapshot.document))

    private fun encodeDocument(document: ReaderDocument): JSONObject = JSONObject()
        .put("title", document.title)
        .put("sourceUrl", document.sourceUrl)
        .put("siteName", document.siteName)
        .put(
            "blocks",
            JSONArray().apply {
                document.blocks.forEach { block ->
                    put(
                        JSONObject()
                            .put("kind", block.kind.name)
                            .put("text", block.text)
                            .put("level", block.level)
                            .put(
                                "links",
                                JSONArray().apply {
                                    block.links.forEach { link ->
                                        put(JSONObject().put("label", link.label).put("url", link.url))
                                    }
                                },
                            ),
                    )
                }
            },
        )

    private fun decode(root: JSONObject): ReaderLibraryState {
        if (root.optInt("version") != CURRENT_VERSION) return ReaderLibraryState()
        val settingsJson = root.optJSONObject("settings") ?: JSONObject()
        val settings = ReaderSettings(
            fontScale = settingsJson.optDouble("fontScale", 1.0).toFloat().coerceIn(0.8f, 1.6f),
            theme = runCatching {
                ReaderTheme.valueOf(settingsJson.optString("theme", ReaderTheme.System.name))
            }.getOrDefault(ReaderTheme.System),
            textAlignment = runCatching {
                ReaderTextAlignment.valueOf(
                    settingsJson.optString(
                        "textAlignment",
                        ReaderTextAlignment.Start.name,
                    ),
                )
            }.getOrDefault(ReaderTextAlignment.Start),
        )
        val progressJson = root.optJSONObject("progress") ?: JSONObject()
        val progress = buildMap {
            progressJson.keys().forEach { url ->
                if (ReaderExtractionContract.safeHttpUrl(url) != null) {
                    put(url, progressJson.optDouble(url, 0.0).toFloat().coerceIn(0f, 1f))
                }
            }
        }
        val snapshotsJson = root.optJSONArray("snapshots") ?: JSONArray()
        val snapshots = buildList {
            for (index in 0 until minOf(snapshotsJson.length(), ReaderLibraryRules.MAX_SNAPSHOTS)) {
                decodeSnapshot(snapshotsJson.optJSONObject(index) ?: continue)?.let(::add)
            }
        }
        return ReaderLibraryState(settings, progress, snapshots)
    }

    private fun decodeSnapshot(json: JSONObject): ReaderSnapshot? = runCatching {
        val document = decodeDocument(json.getJSONObject("document")) ?: return null
        ReaderSnapshot(
            id = json.getString("id").takeIf { UUID.fromString(it).toString() == it } ?: return null,
            document = document,
            progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
            savedAtMillis = json.optLong("savedAt", 0L),
            sourceProfileId = json.optionalString("sourceProfileId")?.takeIf {
                it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl)
            },
            isPrivate = json.optBoolean("isPrivate", false),
        )
    }.getOrNull()

    private fun decodeDocument(json: JSONObject): ReaderDocument? {
        val blocksJson = json.optJSONArray("blocks") ?: JSONArray()
        val blocks = buildList {
            for (index in 0 until minOf(blocksJson.length(), ReaderExtractionContract.MAX_BLOCKS)) {
                val block = blocksJson.optJSONObject(index) ?: continue
                val linksJson = block.optJSONArray("links") ?: JSONArray()
                val links = buildList {
                    for (linkIndex in 0 until minOf(linksJson.length(), 40)) {
                        val link = linksJson.optJSONObject(linkIndex) ?: continue
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
        return (ReaderExtractionContract.sanitize(
            ReaderExtractionPayload(
                title = json.optionalString("title"),
                sourceUrl = json.optionalString("sourceUrl"),
                siteName = json.optionalString("siteName"),
                blocks = blocks,
            ),
        ) as? ReaderExtractionResult.Success)?.document
    }

    private companion object {
        val lock = Any()
        const val FILE_NAME = "reader_library_v1.json"
        const val CURRENT_VERSION = 1
        const val MAX_JSON_BYTES = 8 * 1024 * 1024
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null
}
