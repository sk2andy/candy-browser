package dev.sk2andy.materialbrowser.data.sync

import dev.sk2andy.materialbrowser.sync.SyncBase64
import dev.sk2andy.materialbrowser.sync.SyncCache
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDescriptor
import dev.sk2andy.materialbrowser.sync.SyncPendingMutation
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncTab
import dev.sk2andy.materialbrowser.sync.SyncVaultSecrets
import dev.sk2andy.materialbrowser.sync.parseStrictJsonObject
import org.json.JSONArray
import org.json.JSONObject

internal object SyncStateCodec {
    fun encodeSettings(value: SyncConnectionSettings): String = JSONObject()
        .put("schemaVersion", 1)
        .put("endpoint", value.endpoint)
        .put("username", value.username)
        .put("deviceName", value.deviceName)
        .put("iconCatalogId", value.iconCatalogId)
        .put("iconAccentHue", value.iconAccentHue)
        .toString()

    fun decodeSettings(raw: String): SyncConnectionSettings {
        val value = parseStrictJsonObject(raw).requireKeys(
            "schemaVersion",
            "endpoint",
            "username",
            "deviceName",
            "iconCatalogId",
            "iconAccentHue",
        )
        require(value.getInt("schemaVersion") == 1)
        return SyncConnectionSettings(
            endpoint = value.getString("endpoint"),
            username = value.getString("username"),
            deviceName = value.getString("deviceName"),
            iconCatalogId = value.getString("iconCatalogId"),
            iconAccentHue = value.getInt("iconAccentHue"),
        )
    }

    fun encodeVault(value: SyncVaultSecrets): ByteArray = JSONObject()
        .put("schemaVersion", 1)
        .put("workspaceId", value.workspaceId)
        .put("deviceId", value.deviceId)
        .put("deviceToken", value.deviceToken)
        .put("workspaceKey", SyncBase64.encode(value.workspaceKey))
        .put("devicePrivateKeyPkcs8", SyncBase64.encode(value.devicePrivateKeyPkcs8))
        .toString()
        .toByteArray(Charsets.UTF_8)

    fun decodeVault(raw: ByteArray): SyncVaultSecrets {
        val value = parseStrictJsonObject(raw.toString(Charsets.UTF_8)).requireKeys(
            "schemaVersion",
            "workspaceId",
            "deviceId",
            "deviceToken",
            "workspaceKey",
            "devicePrivateKeyPkcs8",
        )
        require(value.getInt("schemaVersion") == 1)
        return SyncVaultSecrets(
            workspaceId = value.boundedString("workspaceId", 128),
            deviceId = value.boundedString("deviceId", 128),
            deviceToken = value.boundedString("deviceToken", 512),
            workspaceKey = SyncBase64.decode(value.getString("workspaceKey"), expectedBytes = 32),
            devicePrivateKeyPkcs8 = SyncBase64.decode(
                value.getString("devicePrivateKeyPkcs8"),
                maxBytes = 512,
            ),
        )
    }

    fun encodeCache(value: SyncCache): ByteArray {
        require(value.profiles.size <= MAX_CACHE_ENTRIES)
        require(value.pendingMutations.size <= MAX_CACHE_ENTRIES)
        require(value.preparedWrites.size <= MAX_CACHE_ENTRIES)
        require(value.preparedWrites.keys.all(value.pendingMutations.associateBy(SyncPendingMutation::mutationId)::containsKey))
        return JSONObject()
            .put("schemaVersion", 1)
            .put("cursor", value.cursor)
            .put("profiles", JSONArray(value.profiles.values.sortedBy(SyncProfile::deviceId).map(::encodeProfile)))
            .put("pendingMutations", JSONArray(value.pendingMutations.map(::encodeMutation)))
            .put(
                "preparedWrites",
                JSONArray(value.preparedWrites.entries.sortedBy { it.key }.map { (mutationId, change) ->
                    encodePreparedWrite(mutationId, change)
                }),
            )
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    fun decodeCache(raw: ByteArray): SyncCache {
        require(raw.size <= MAX_CACHE_BYTES)
        val value = parseStrictJsonObject(raw.toString(Charsets.UTF_8)).requireKeys(
            "schemaVersion",
            "cursor",
            "profiles",
            "pendingMutations",
            "preparedWrites",
        )
        require(value.getInt("schemaVersion") == 1)
        val profiles = value.getJSONArray("profiles")
        val pending = value.getJSONArray("pendingMutations")
        val prepared = value.getJSONArray("preparedWrites")
        require(
            profiles.length() <= MAX_CACHE_ENTRIES &&
                pending.length() <= MAX_CACHE_ENTRIES &&
                prepared.length() <= MAX_CACHE_ENTRIES,
        )
        val decodedProfiles = buildList {
            repeat(profiles.length()) { add(decodeProfile(profiles.getJSONObject(it))) }
        }
        require(decodedProfiles.map(SyncProfile::deviceId).distinct().size == decodedProfiles.size)
        val pendingMutations = buildList {
            repeat(pending.length()) { add(decodeMutation(pending.getJSONObject(it))) }
        }
        require(pendingMutations.map(SyncPendingMutation::mutationId).distinct().size == pendingMutations.size)
        val preparedWrites = buildMap {
            repeat(prepared.length()) {
                val (mutationId, change) = decodePreparedWrite(prepared.getJSONObject(it))
                require(put(mutationId, change) == null)
            }
        }
        val pendingById = pendingMutations.associateBy(SyncPendingMutation::mutationId)
        require(preparedWrites.keys.all(pendingById::containsKey))
        preparedWrites.forEach { (mutationId, change) ->
            require(pendingById.getValue(mutationId).targetDeviceId == change.targetDeviceId)
        }
        return SyncCache(
            cursor = value.getString("cursor").also { require(it.length <= 260) },
            profiles = decodedProfiles.associateBy(SyncProfile::deviceId),
            pendingMutations = pendingMutations,
            preparedWrites = preparedWrites,
        )
    }

    private fun encodeProfile(value: SyncProfile): JSONObject = JSONObject()
        .put("deviceId", value.deviceId)
        .put("displayName", value.displayName)
        .put("iconCatalogId", value.icon.catalogId)
        .put("iconAccentHue", value.icon.accentHue)
        .put("revision", value.revision.toString())
        .put("lastSeenAt", value.lastSeenAt)
        .put("tabs", JSONArray(value.tabs.map(::encodeTab)))

    private fun decodeProfile(value: JSONObject): SyncProfile {
        value.requireKeys(
            "deviceId",
            "displayName",
            "iconCatalogId",
            "iconAccentHue",
            "revision",
            "lastSeenAt",
            "tabs",
        )
        val tabs = value.getJSONArray("tabs")
        require(tabs.length() <= 10_000)
        return SyncProfile(
            deviceId = value.boundedString("deviceId", 128),
            displayName = value.boundedString("displayName", 80),
            icon = SyncDeviceIconDescriptor(
                catalogId = value.boundedString("iconCatalogId", 48),
                accentHue = value.getInt("iconAccentHue"),
            ),
            revision = value.getString("revision").toLong().also { require(it >= 0) },
            tabs = buildList { repeat(tabs.length()) { add(decodeTab(tabs.getJSONObject(it))) } },
            lastSeenAt = value.boundedString("lastSeenAt", 64),
        )
    }

    private fun encodeTab(value: SyncTab): JSONObject = JSONObject()
        .put("candyId", value.candyId)
        .put("windowId", value.windowId)
        .put("index", value.index)
        .put("groupId", value.groupId ?: JSONObject.NULL)
        .put("active", value.active)
        .put("pinned", value.pinned)
        .put("title", value.title)
        .put("url", value.url)

    private fun decodeTab(value: JSONObject): SyncTab {
        value.requireKeys("candyId", "windowId", "index", "groupId", "active", "pinned", "title", "url")
        return SyncTab(
            candyId = value.boundedString("candyId", 128),
            windowId = value.getInt("windowId"),
            index = value.getInt("index"),
            groupId = if (value.isNull("groupId")) null else value.getInt("groupId"),
            active = value.getBoolean("active"),
            pinned = value.getBoolean("pinned"),
            title = value.getString("title").also { require(it.length <= 4_096) },
            url = value.boundedString("url", 32_768),
        )
    }

    private fun encodeMutation(value: SyncPendingMutation): JSONObject = JSONObject()
        .put("mutationId", value.mutationId)
        .put("targetDeviceId", value.targetDeviceId)
        .apply {
            when (value) {
                is SyncPendingMutation.Open -> put("type", "open").put("tab", encodeTab(value.tab))
                    .put("isPrivate", value.isPrivate)
                is SyncPendingMutation.Navigate -> put("type", "navigate")
                    .put("candyId", value.candyId)
                    .put("title", value.title)
                    .put("url", value.url)
                is SyncPendingMutation.Close -> put("type", "close").put("candyId", value.candyId)
                is SyncPendingMutation.Reorder -> put("type", "reorder")
                    .put("orderedCandyIds", JSONArray(value.orderedCandyIds))
                is SyncPendingMutation.SetPinned -> put("type", "set-pinned")
                    .put("candyId", value.candyId)
                    .put("pinned", value.pinned)
            }
        }

    private fun decodeMutation(value: JSONObject): SyncPendingMutation {
        val mutationId = value.boundedString("mutationId", 128)
        val target = value.boundedString("targetDeviceId", 128)
        return when (value.getString("type")) {
            "open" -> {
                value.requireKeys("mutationId", "targetDeviceId", "type", "tab", "isPrivate")
                SyncPendingMutation.Open(
                    mutationId,
                    target,
                    decodeTab(value.getJSONObject("tab")),
                    value.getBoolean("isPrivate"),
                )
            }
            "navigate" -> {
                value.requireKeys("mutationId", "targetDeviceId", "type", "candyId", "title", "url")
                SyncPendingMutation.Navigate(
                    mutationId,
                    target,
                    value.boundedString("candyId", 128),
                    value.getString("title").also { require(it.length <= 4_096) },
                    value.boundedString("url", 32_768),
                )
            }
            "close" -> {
                value.requireKeys("mutationId", "targetDeviceId", "type", "candyId")
                SyncPendingMutation.Close(mutationId, target, value.boundedString("candyId", 128))
            }
            "reorder" -> {
                value.requireKeys("mutationId", "targetDeviceId", "type", "orderedCandyIds")
                val ids = value.getJSONArray("orderedCandyIds")
                require(ids.length() <= 10_000)
                SyncPendingMutation.Reorder(
                    mutationId,
                    target,
                    buildList { repeat(ids.length()) { add(ids.getString(it)) } },
                )
            }
            "set-pinned" -> {
                value.requireKeys("mutationId", "targetDeviceId", "type", "candyId", "pinned")
                SyncPendingMutation.SetPinned(
                    mutationId,
                    target,
                    value.boundedString("candyId", 128),
                    value.getBoolean("pinned"),
                )
            }
            else -> throw IllegalArgumentException("Unknown mutation")
        }
    }

    private fun encodePreparedWrite(mutationId: String, change: dev.sk2andy.materialbrowser.sync.SyncEncryptedChange) =
        JSONObject()
            .put("mutationId", mutationId)
            .put("changeId", change.changeId)
            .put("writerDeviceId", change.writerDeviceId)
            .put("targetDeviceId", change.targetDeviceId)
            .put("baseRevision", change.baseRevision.toString())
            .put("revision", requireNotNull(change.revision).toString())
            .put("nonce", change.nonce)
            .put("ciphertext", change.ciphertext)

    private fun decodePreparedWrite(value: JSONObject): Pair<String, dev.sk2andy.materialbrowser.sync.SyncEncryptedChange> {
        value.requireKeys(
            "mutationId",
            "changeId",
            "writerDeviceId",
            "targetDeviceId",
            "baseRevision",
            "revision",
            "nonce",
            "ciphertext",
        )
        val base = value.getString("baseRevision").toLong().also { require(it >= 0) }
        val revision = value.getString("revision").toLong().also { require(it == base + 1) }
        return value.boundedString("mutationId", 128) to dev.sk2andy.materialbrowser.sync.SyncEncryptedChange(
            changeId = value.boundedString("changeId", 128),
            writerDeviceId = value.boundedString("writerDeviceId", 128),
            targetDeviceId = value.boundedString("targetDeviceId", 128),
            baseRevision = base,
            revision = revision,
            nonce = value.boundedString("nonce", 16),
            ciphertext = value.boundedString("ciphertext", 524_288),
        )
    }

    private fun JSONObject.requireKeys(vararg expected: String): JSONObject = apply {
        require(keys().asSequence().toSet() == expected.toSet())
    }

    private fun JSONObject.boundedString(name: String, maximum: Int): String = getString(name).also {
        require(it.isNotEmpty() && it.length <= maximum)
    }

    private const val MAX_CACHE_BYTES = 8 * 1_024 * 1_024
    private const val MAX_CACHE_ENTRIES = 1_000
}
