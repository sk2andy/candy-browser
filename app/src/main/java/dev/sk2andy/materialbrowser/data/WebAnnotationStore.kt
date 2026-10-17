package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotation
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Explicitly saved quotes, isolated by profile. Call from a worker; private content is rejected. */
internal class WebAnnotationStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "web_annotations.json"))

    fun all(): List<WebAnnotation> = synchronized(LOCK) { read() }

    fun save(
        annotation: WebAnnotation,
        isPrivate: Boolean,
        expectedEpoch: Long = epoch,
    ): Boolean = synchronized(LOCK) {
        if (isPrivate || expectedEpoch != epoch) return false
        val value = WebAnnotationRules.sanitize(annotation) ?: return false
        val existing = read()
        if (existing.size >= WebAnnotationRules.MAX_ENTRIES && existing.none { it.id == value.id }) return false
        write(existing.filterNot { it.id == value.id } + value)
    }

    fun delete(id: String, profileId: String): Boolean = synchronized(LOCK) {
        write(read().filterNot { it.id == id && it.profileId == profileId })
    }

    fun deleteProfile(profileId: String): Boolean = synchronized(LOCK) {
        write(read().filterNot { it.profileId == profileId })
    }

    fun clear(): Boolean = synchronized(LOCK) {
        epoch++
        file.delete()
        !file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists() &&
            !File(file.baseFile.path + ".new").exists()
    }

    private fun read(): List<WebAnnotation> = runCatching {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val bytes = file.openRead().use { it.readNBytes(MAX_BYTES + 1) }
        require(bytes.size <= MAX_BYTES)
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        require(array.length() <= WebAnnotationRules.MAX_ENTRIES)
        (0 until array.length()).mapNotNull { index ->
            val value = array.optJSONObject(index) ?: return@mapNotNull null
            WebAnnotationRules.sanitize(
                WebAnnotation(
                    id = value.optString("id"),
                    profileId = value.optString("profileId"),
                    url = value.optString("url"),
                    title = value.optString("title"),
                    quote = value.optString("quote"),
                    note = value.optString("note"),
                    createdAt = value.optLong("createdAt", -1),
                ),
            )
        }
    }.getOrElse { emptyList() }

    private fun write(values: List<WebAnnotation>): Boolean = runCatching {
        val data = JSONArray().apply {
            values.forEach { value ->
                put(
                    JSONObject()
                        .put("id", value.id)
                        .put("profileId", value.profileId)
                        .put("url", value.url)
                        .put("title", value.title)
                        .put("quote", value.quote)
                        .put("note", value.note)
                        .put("createdAt", value.createdAt),
                )
            }
        }.toString().toByteArray(Charsets.UTF_8)
        require(data.size <= MAX_BYTES)
        val output = file.startWrite()
        try {
            output.write(data)
            file.finishWrite(output)
        } catch (failure: Exception) {
            file.failWrite(output)
            throw failure
        }
        true
    }.getOrDefault(false)

    companion object {
        @Volatile
        internal var epoch = 0L
            private set
        private val LOCK = Any()
        private const val MAX_BYTES = 12 * 1_024 * 1_024
    }
}
