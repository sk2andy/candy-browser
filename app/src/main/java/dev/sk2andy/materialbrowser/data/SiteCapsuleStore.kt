package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.capsule.CapsuleChromeMode
import dev.sk2andy.materialbrowser.capsule.CapsuleIconMode
import dev.sk2andy.materialbrowser.capsule.CapsuleNavigationMode
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

class SiteCapsuleStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))

    @Synchronized
    fun load(): List<SiteCapsule> {
        if (file.baseFile.isFile && file.baseFile.length() > MAX_JSON_BYTES) return emptyList()
        val bytes = runCatching {
            file.openRead().use { input -> input.readNBytes(MAX_JSON_BYTES + 1) }
        }
            .getOrNull()
            ?: return emptyList()
        if (bytes.size > MAX_JSON_BYTES) return emptyList()
        return runCatching {
            val root = JSONObject(bytes.toString(StandardCharsets.UTF_8))
            val version = root.optInt("version", 0)
            if (version !in 1..CURRENT_VERSION) return@runCatching emptyList()
            val array = root.optJSONArray("capsules") ?: JSONArray()
            buildList {
                for (index in 0 until minOf(array.length(), SiteCapsuleRules.MAX_CAPSULES * 2)) {
                    val item = array.optJSONObject(index) ?: continue
                    readCapsule(item)?.let(::add)
                }
            }.mapNotNull(SiteCapsuleRules::sanitizePersisted)
                .let(SiteCapsuleRules::bounded)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(capsules: List<SiteCapsule>) {
        val array = JSONArray()
        SiteCapsuleRules.bounded(capsules)
            .mapNotNull(SiteCapsuleRules::sanitizePersisted)
            .forEach { capsule ->
                array.put(
                    JSONObject()
                        .put("id", capsule.id)
                        .put("name", capsule.name)
                        .put("startUrl", capsule.startUrl)
                        .put("profileId", capsule.profileId)
                        .put("ownsDedicatedProfile", capsule.ownsDedicatedProfile)
                        .put("isolatedStorageRequested", capsule.isolatedStorageRequested)
                        .put("navigationMode", capsule.navigationMode.wireValue)
                        .put("chromeMode", capsule.chromeMode.wireValue)
                        .put("iconMode", capsule.iconMode.wireValue)
                        .put("createdAtMillis", capsule.createdAtMillis)
                        .put("updatedAtMillis", capsule.updatedAtMillis),
                )
            }
        val bytes = JSONObject()
            .put("version", CURRENT_VERSION)
            .put("capsules", array)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        check(bytes.size <= MAX_JSON_BYTES)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun readCapsule(item: JSONObject): SiteCapsule? = runCatching {
        SiteCapsule(
            id = item.getString("id"),
            name = item.getString("name"),
            startUrl = item.getString("startUrl"),
            profileId = item.getString("profileId"),
            ownsDedicatedProfile = item.optBoolean("ownsDedicatedProfile", false),
            isolatedStorageRequested = item.optBoolean("isolatedStorageRequested", false),
            navigationMode = CapsuleNavigationMode.fromWireValue(item.optString("navigationMode")),
            chromeMode = CapsuleChromeMode.fromWireValue(item.optString("chromeMode")),
            iconMode = CapsuleIconMode.fromWireValue(item.optString("iconMode")),
            createdAtMillis = item.optLong("createdAtMillis", 0L),
            updatedAtMillis = item.optLong("updatedAtMillis", 0L),
        )
    }.getOrNull()

    private companion object {
        const val CURRENT_VERSION = 1
        const val FILE_NAME = "site_capsules_v1.json"
        const val MAX_JSON_BYTES = 512 * 1024
    }
}
