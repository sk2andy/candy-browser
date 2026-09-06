package dev.sk2andy.materialbrowser.data.sync

import dev.sk2andy.materialbrowser.sync.SyncBase64
import dev.sk2andy.materialbrowser.sync.SyncCache
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDescriptor
import dev.sk2andy.materialbrowser.sync.SyncEncryptedChange
import dev.sk2andy.materialbrowser.sync.SyncEncryptedDelta
import dev.sk2andy.materialbrowser.sync.SyncPendingMutation
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncTab
import dev.sk2andy.materialbrowser.sync.SyncVaultSecrets
import dev.sk2andy.materialbrowser.sync.isJsonNull
import dev.sk2andy.materialbrowser.sync.parseStrictJsonObject
import dev.sk2andy.materialbrowser.sync.requireArray
import dev.sk2andy.materialbrowser.sync.requireExactKeys
import dev.sk2andy.materialbrowser.sync.requireObject
import dev.sk2andy.materialbrowser.sync.requireString
import dev.sk2andy.materialbrowser.sync.strictBoolean
import dev.sk2andy.materialbrowser.sync.strictInt
import dev.sk2andy.materialbrowser.sync.strictString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SyncStateCodec {
    @Throws(Exception::class)
    fun encodeSettings(value: SyncConnectionSettings): String = buildJsonObject { put("schemaVersion", 2); put("endpoint", value.endpoint); put("username", value.username); put("deviceName", value.deviceName); put("iconCatalogId", value.iconCatalogId); put("iconAccentHue", value.iconAccentHue); put("localProfileId", value.localProfileId?.let(::JsonPrimitive) ?: JsonNull) }.toString()
    @Throws(Exception::class)
    fun decodeSettings(raw: String): SyncConnectionSettings {
        val value = parseStrictJsonObject(raw); val version = value.strictInt("schemaVersion")
        if (version == 1) value.requireExactKeys("schemaVersion", "endpoint", "username", "deviceName", "iconCatalogId", "iconAccentHue") else if (version == 2) value.requireExactKeys("schemaVersion", "endpoint", "username", "deviceName", "iconCatalogId", "iconAccentHue", "localProfileId") else error("Unknown settings schema")
        return SyncConnectionSettings(value.string("endpoint"), value.string("username"), value.string("deviceName"), value.string("iconCatalogId"), value.strictInt("iconAccentHue"), if (version == 1 || value.isJsonNull("localProfileId")) null else value.boundedString("localProfileId", 128))
    }
    @Throws(Exception::class)
    fun encodeVault(value: SyncVaultSecrets): ByteArray = buildJsonObject { put("schemaVersion", 1); put("workspaceId", value.workspaceId); put("deviceId", value.deviceId); put("deviceToken", value.deviceToken); put("workspaceKey", SyncBase64.encode(value.workspaceKey)); put("devicePrivateKeyPkcs8", SyncBase64.encode(value.devicePrivateKeyPkcs8)) }.toString().encodeToByteArray()
    @Throws(Exception::class)
    fun decodeVault(raw: ByteArray): SyncVaultSecrets { val value = parseStrictJsonObject(raw.decodeToString()).also { it.requireExactKeys("schemaVersion", "workspaceId", "deviceId", "deviceToken", "workspaceKey", "devicePrivateKeyPkcs8"); require(it.strictInt("schemaVersion") == 1) }; return SyncVaultSecrets(value.boundedString("workspaceId", 128), value.boundedString("deviceId", 128), value.boundedString("deviceToken", 512), SyncBase64.decode(value.strictString("workspaceKey"), expectedBytes = 32), SyncBase64.decode(value.strictString("devicePrivateKeyPkcs8"), maxBytes = 512)) }
    @Throws(Exception::class)
    fun encodeCache(value: SyncCache): ByteArray { require(value.profiles.size <= MAX_CACHE_ENTRIES && value.pendingMutations.size <= MAX_CACHE_ENTRIES && value.preparedWrites.size <= MAX_CACHE_ENTRIES && value.preparedDeltas.size <= MAX_CACHE_ENTRIES); val pending = value.pendingMutations.associateBy(SyncPendingMutation::mutationId); require(value.preparedWrites.keys.all(pending::containsKey) && value.preparedDeltas.keys.all(pending::containsKey)); return buildJsonObject { put("schemaVersion", 2); put("cursor", value.cursor); put("deltaCursor", value.deltaCursor); put("profiles", JsonArray(value.profiles.values.sortedBy(SyncProfile::deviceId).map(::profileJson))); put("pendingMutations", JsonArray(value.pendingMutations.map(::mutationJson))); put("preparedWrites", JsonArray(value.preparedWrites.entries.sortedBy { it.key }.map { preparedWriteJson(it.key, it.value) })); put("preparedDeltas", JsonArray(value.preparedDeltas.entries.sortedBy { it.key }.map { preparedDeltaJson(it.key, it.value) })) }.toString().encodeToByteArray() }
    @Throws(Exception::class)
    fun decodeCache(raw: ByteArray): SyncCache {
        require(raw.size <= MAX_CACHE_BYTES); val value = parseStrictJsonObject(raw.decodeToString()); val version = value.strictInt("schemaVersion")
        if (version == 1) value.requireExactKeys("schemaVersion", "cursor", "profiles", "pendingMutations", "preparedWrites") else if (version == 2) value.requireExactKeys("schemaVersion", "cursor", "deltaCursor", "profiles", "pendingMutations", "preparedWrites", "preparedDeltas") else error("Unknown cache schema")
        val profiles = value.requireArray("profiles").map { profile(it as? JsonObject ?: error("Invalid profile")) }; val mutations = value.requireArray("pendingMutations").map { mutation(it as? JsonObject ?: error("Invalid mutation")) }; val writes = value.requireArray("preparedWrites").map { preparedWrite(it as? JsonObject ?: error("Invalid write")) }.toMap(); val deltas = if (version == 2) value.requireArray("preparedDeltas").map { preparedDelta(it as? JsonObject ?: error("Invalid delta")) }.toMap() else emptyMap()
        require(profiles.size <= MAX_CACHE_ENTRIES && mutations.size <= MAX_CACHE_ENTRIES && writes.size <= MAX_CACHE_ENTRIES && deltas.size <= MAX_CACHE_ENTRIES && profiles.map(SyncProfile::deviceId).distinct().size == profiles.size && mutations.map(SyncPendingMutation::mutationId).distinct().size == mutations.size); val pending = mutations.associateBy(SyncPendingMutation::mutationId); require(writes.keys.all(pending::containsKey) && deltas.keys.all(pending::containsKey)); writes.forEach { (id, change) -> require(pending.getValue(id).targetDeviceId == change.targetDeviceId) }; deltas.forEach { (id, change) -> require(pending.getValue(id).targetDeviceId == change.targetDeviceId) }
        return SyncCache(value.strictString("cursor", 0, 260), profiles.associateBy(SyncProfile::deviceId), mutations, writes, if (version == 2) value.strictString("deltaCursor", 0, 260) else "", deltas)
    }
    private fun profileJson(value: SyncProfile) = buildJsonObject { put("deviceId", value.deviceId); put("displayName", value.displayName); put("iconCatalogId", value.icon.catalogId); put("iconAccentHue", value.icon.accentHue); put("revision", value.revision.toString()); put("lastSeenAt", value.lastSeenAt); put("tabs", JsonArray(value.tabs.map(::tabJson))) }
    private fun profile(value: JsonObject): SyncProfile { value.requireExactKeys("deviceId", "displayName", "iconCatalogId", "iconAccentHue", "revision", "lastSeenAt", "tabs"); val tabs = value.requireArray("tabs").also { require(it.size <= 10_000) }.map { tab(it as? JsonObject ?: error("Invalid tab")) }; return SyncProfile(value.boundedString("deviceId", 128), value.boundedString("displayName", 80), SyncDeviceIconDescriptor(value.boundedString("iconCatalogId", 48), value.strictInt("iconAccentHue")), value.revision("revision"), tabs, value.boundedString("lastSeenAt", 64)) }
    private fun tabJson(value: SyncTab) = buildJsonObject { put("candyId", value.candyId); put("windowId", value.windowId); put("index", value.index); put("groupId", value.groupId?.let(::JsonPrimitive) ?: JsonNull); put("active", value.active); put("pinned", value.pinned); put("title", value.title); put("url", value.url) }
    private fun tab(value: JsonObject): SyncTab { value.requireExactKeys("candyId", "windowId", "index", "groupId", "active", "pinned", "title", "url"); return SyncTab(value.boundedString("candyId", 128), value.strictInt("windowId"), value.strictInt("index"), if (value.isJsonNull("groupId")) null else value.strictInt("groupId"), value.strictBoolean("active"), value.strictBoolean("pinned"), value.strictString("title", 0, 4_096), value.boundedString("url", 32_768)) }
    private fun mutationJson(value: SyncPendingMutation) = buildJsonObject { put("mutationId", value.mutationId); put("targetDeviceId", value.targetDeviceId); when (value) { is SyncPendingMutation.Open -> { put("type", "open"); put("tab", tabJson(value.tab)); put("isPrivate", value.isPrivate) }; is SyncPendingMutation.Navigate -> { put("type", "navigate"); put("candyId", value.candyId); put("title", value.title); put("url", value.url) }; is SyncPendingMutation.Close -> { put("type", "close"); put("candyId", value.candyId) }; is SyncPendingMutation.Reorder -> { put("type", "reorder"); put("orderedCandyIds", JsonArray(value.orderedCandyIds.map(::JsonPrimitive))) }; is SyncPendingMutation.SetPinned -> { put("type", "set-pinned"); put("candyId", value.candyId); put("pinned", value.pinned) } } }
    private fun mutation(value: JsonObject): SyncPendingMutation { val id = value.boundedString("mutationId", 128); val target = value.boundedString("targetDeviceId", 128); return when (value.strictString("type")) { "open" -> { value.requireExactKeys("mutationId", "targetDeviceId", "type", "tab", "isPrivate"); SyncPendingMutation.Open(id, target, tab(value.requireObject("tab")), value.strictBoolean("isPrivate")) }; "navigate" -> { value.requireExactKeys("mutationId", "targetDeviceId", "type", "candyId", "title", "url"); SyncPendingMutation.Navigate(id, target, value.boundedString("candyId", 128), value.strictString("title", 0, 4_096), value.boundedString("url", 32_768)) }; "close" -> { value.requireExactKeys("mutationId", "targetDeviceId", "type", "candyId"); SyncPendingMutation.Close(id, target, value.boundedString("candyId", 128)) }; "reorder" -> { value.requireExactKeys("mutationId", "targetDeviceId", "type", "orderedCandyIds"); val ids = value.requireArray("orderedCandyIds").also { require(it.size <= 10_000) }.map { it.requireString() }; SyncPendingMutation.Reorder(id, target, ids) }; "set-pinned" -> { value.requireExactKeys("mutationId", "targetDeviceId", "type", "candyId", "pinned"); SyncPendingMutation.SetPinned(id, target, value.boundedString("candyId", 128), value.strictBoolean("pinned")) }; else -> error("Unknown mutation") } }
    private fun preparedWriteJson(id: String, change: SyncEncryptedChange) = buildJsonObject { put("mutationId", id); put("changeId", change.changeId); put("writerDeviceId", change.writerDeviceId); put("targetDeviceId", change.targetDeviceId); put("baseRevision", change.baseRevision.toString()); put("revision", requireNotNull(change.revision).toString()); put("nonce", change.nonce); put("ciphertext", change.ciphertext) }
    private fun preparedWrite(value: JsonObject): Pair<String, SyncEncryptedChange> { value.requireExactKeys("mutationId", "changeId", "writerDeviceId", "targetDeviceId", "baseRevision", "revision", "nonce", "ciphertext"); val base = value.revision("baseRevision"); val revision = value.revision("revision").also { require(it == base + 1) }; return value.boundedString("mutationId", 128) to SyncEncryptedChange(value.boundedString("changeId", 128), value.boundedString("writerDeviceId", 128), value.boundedString("targetDeviceId", 128), base, revision, value.boundedString("nonce", 16), value.boundedString("ciphertext", 524_288)) }
    private fun preparedDeltaJson(id: String, change: SyncEncryptedDelta) = buildJsonObject { put("mutationId", id); put("changeId", change.changeId); put("workspaceId", change.workspaceId); put("writerDeviceId", change.writerDeviceId); put("targetDeviceId", change.targetDeviceId); put("baseRevision", change.baseRevision.toString()); put("nonce", change.nonce); put("ciphertext", change.ciphertext) }
    private fun preparedDelta(value: JsonObject): Pair<String, SyncEncryptedDelta> { value.requireExactKeys("mutationId", "changeId", "workspaceId", "writerDeviceId", "targetDeviceId", "baseRevision", "nonce", "ciphertext"); val id = value.boundedString("mutationId", 128); return id to SyncEncryptedDelta(value.boundedString("changeId", 128), id, value.boundedString("workspaceId", 128), value.boundedString("writerDeviceId", 128), value.boundedString("targetDeviceId", 128), value.revision("baseRevision"), null, value.boundedString("nonce", 16), value.boundedString("ciphertext", 87_424)) }
    private fun JsonObject.boundedString(name: String, max: Int) = strictString(name, 1, max)
    private fun JsonObject.string(name: String) = strictString(name, 0, Int.MAX_VALUE)
    private fun JsonObject.revision(name: String) = strictString(name, 1, 19).toLongOrNull()?.also { require(it >= 0) } ?: error("Invalid revision")
    private const val MAX_CACHE_BYTES = 8 * 1_024 * 1_024
    private const val MAX_CACHE_ENTRIES = 1_000
}
