package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Only known regular downloads receive device-local document-picker eligibility. */
internal class DownloadProvenanceStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, FILE_NAME))

    fun record(id: Long, name: String, profileId: String?, isPrivate: Boolean) = synchronized(lock) {
        val before = read()
        val remaining = before.filterNot { it.id == id }
        if (
            isPrivate || profileId.isNullOrBlank() || profileId.length > 128 ||
            profileId.any(Char::isISOControl) || name.isBlank() || name.length > 256 ||
            DownloadRuntimeRegistry.isRuntimeId(id)
        ) {
            if (remaining.size != before.size) write(remaining)
            return@synchronized
        }
        val records = remaining.takeLast(MAX_RECORDS - 1) +
            DownloadProvenance(id, name, profileId)
        write(records)
    }

    private fun write(records: List<DownloadProvenance>) {
        val data = JSONArray().apply {
            records.forEach { record ->
                put(JSONObject().put("id", record.id).put("name", record.name).put("profileId", record.profileId))
            }
        }.toString().toByteArray(Charsets.UTF_8)
        if (data.size > MAX_BYTES) {
            if (records.isNotEmpty()) write(records.drop(1))
            return
        }
        file.writeSafely { it.write(data) }
    }

    fun snapshot(): List<DownloadProvenance> = synchronized(lock) { read() }

    private fun read(): List<DownloadProvenance> = runCatching {
        val bytes = file.openRead().use { it.readNBytes(MAX_BYTES + 1) }
        if (bytes.size > MAX_BYTES) return emptyList()
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        buildList {
            repeat(minOf(array.length(), MAX_RECORDS)) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                val id = item.optLong("id", Long.MIN_VALUE)
                val name = item.optString("name")
                val profile = item.optString("profileId")
                if (
                    !DownloadRuntimeRegistry.isRuntimeId(id) && name.isNotBlank() && name.length <= 256 &&
                    profile.isNotBlank() && profile.length <= 128 && profile.none(Char::isISOControl)
                ) add(DownloadProvenance(id, name, profile))
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        val lock = Any()
        const val FILE_NAME = "download_document_sources_v1.json"
        const val MAX_RECORDS = 1_024
        const val MAX_BYTES = 512 * 1_024
    }
}

internal data class DownloadProvenance(val id: Long, val name: String, val profileId: String)
