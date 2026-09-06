package dev.sk2andy.materialbrowser.sync

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SyncProtocolCodec {
    @Throws(Exception::class)
    fun decodeBootstrap(raw: String): SyncBootstrap {
        val value = parseStrictJsonObject(raw)
        value.requireExactKeys("protocolVersion", "cryptoVersion", "workspaceId", "serverEpoch", "initialized", "kdf", "recoveryEnvelope")
        require(value.strictInt("protocolVersion") == 1 && value.strictInt("cryptoVersion") == 1)
        val kdf = value.requireObject("kdf").also {
            it.requireExactKeys("algorithm", "salt", "memoryKiB", "iterations", "parallelism", "keyBytes")
            require(it.strictString("algorithm") == "argon2id-v1" && it.strictInt("memoryKiB") == 65_536 && it.strictInt("iterations") == 3 && it.strictInt("parallelism") == 4 && it.strictInt("keyBytes") == 32)
        }
        val recovery = if (value.isJsonNull("recoveryEnvelope")) null else decodeRecoveryEnvelope(value.requireObject("recoveryEnvelope"))
        val initialized = value.strictBoolean("initialized")
        require(initialized == (recovery != null))
        return SyncBootstrap(value.identifier("workspaceId"), value.identifier("serverEpoch"), initialized, SyncRecoveryKdf(kdf.strictString("salt", 22, 22).also { SyncBase64.decode(it, expectedBytes = 16) }, 65_536, 3, 4), recovery)
    }

    @Throws(Exception::class)
    fun decodeDevices(raw: String): List<SyncDeviceRecord> {
        val root = parseStrictJsonObject(raw).also { it.requireExactKeys("devices") }
        return root.requireArray("devices").also { require(it.size <= 1_000) }.map { decodeDevice(it as? JsonObject ?: error("Invalid device")) }
    }

    @Throws(Exception::class)
    fun decodeDeviceIcon(raw: String): SyncDeviceIconDescriptor {
        val value = parseStrictJsonObject(raw).also { it.requireExactKeys("schemaVersion", "catalogId", "accentHue"); require(it.strictInt("schemaVersion") == 1) }
        return SyncDeviceIconDescriptor(value.strictString("catalogId", 1, 48), value.strictInt("accentHue")).also(SyncDeviceIconRules::requireValid)
    }

    @Throws(Exception::class)
    fun encodeDeviceIcon(value: SyncDeviceIconDescriptor): String {
        SyncDeviceIconRules.requireValid(value)
        return buildJsonObject {
            put("schemaVersion", 1)
            put("catalogId", value.catalogId)
            put("accentHue", value.accentHue)
        }.toString()
    }

    @Throws(Exception::class)
    fun encodeTabSnapshot(snapshot: SyncTabSnapshot): String {
        val safe = requireNotNull(SyncTabRules.normalizeSnapshot(snapshot))
        requireInstant(safe.capturedAt)
        return buildJsonObject { put("schemaVersion", 1); put("capturedAt", safe.capturedAt); put("tabs", JsonArray(safe.tabs.map(::tabJson))) }.toString()
    }

    @Throws(Exception::class)
    fun decodeTabSnapshot(raw: String): SyncTabSnapshot {
        require(raw.encodeToByteArray().size <= MAX_PLAINTEXT_BYTES)
        val root = parseStrictJsonObject(raw).also { it.requireExactKeys("schemaVersion", "capturedAt", "tabs"); require(it.strictInt("schemaVersion") == 1) }
        val snapshot = SyncTabSnapshot(root.strictString("capturedAt", 1, 64).also(::requireInstant), root.requireArray("tabs").also { require(it.size <= SyncTabRules.MAX_TABS) }.map { decodeTab(it as? JsonObject ?: error("Invalid tab")) })
        return requireNotNull(SyncTabRules.normalizeSnapshot(snapshot))
    }

    @Throws(Exception::class)
    fun decodePull(raw: String): SyncPullPage = decodePage(raw, ::decodeChange) { changes, cursor, more -> SyncPullPage(changes, cursor, more) }
    @Throws(Exception::class)
    fun encodeDelta(change: SyncEncryptedDelta): String { require(change.revision == null); return deltaJson(change).toString() }
    @Throws(Exception::class)
    fun decodeDeltaPull(raw: String): SyncDeltaPullPage = decodePage(raw, ::decodeDelta) { changes, cursor, more -> SyncDeltaPullPage(changes, cursor, more) }

    @Throws(Exception::class)
    fun decodeRealtimeEvent(raw: String): SyncRealtimeEvent {
        require(raw.encodeToByteArray().size <= MAX_PLAINTEXT_BYTES)
        val root = parseStrictJsonObject(raw).also { it.requireExactKeys("type", "cursor", "change"); require(it.strictString("type") == "change") }
        return SyncRealtimeEvent(root.strictString("cursor", 1, 260).also(::requireCursor), decodeDelta(root.requireObject("change")))
    }

    @Throws(Exception::class)
    fun decodeServerSnapshot(raw: String): SyncServerSnapshot {
        val root = parseStrictJsonObject(raw).also { it.requireExactKeys("cursor", "changes", "tabSnapshots") }
        val snapshots = root.requireArray("tabSnapshots").also { require(it.size <= 1_000) }
        return SyncServerSnapshot(snapshots.map { decodeChange(it as? JsonObject ?: error("Invalid change")) }, root.strictString("cursor", 1, 260).also(::requireCursor))
    }

    @Throws(Exception::class)
    fun encodeEnrollment(identity: SyncDeviceIdentity, name: SyncEncryptedValue, icon: SyncEncryptedValue, recovery: SyncRecoveryEnvelope?): String = buildJsonObject {
        put("deviceKeyFingerprint", identity.fingerprint); put("publicKeyAlgorithm", "ECDH-P256-SPKI"); put("publicKey", SyncBase64.encode(identity.publicKeySpki)); put("encryptedName", name.json()); put("encryptedIcon", icon.json()); put("capabilities", JsonArray(listOf(JsonPrimitive("tabs")))); recovery?.let { put("recoveryEnvelope", it.json()) }
    }.toString()

    @Throws(Exception::class)
    fun encodePutSnapshot(change: SyncEncryptedChange): String {
        val revision = requireNotNull(change.revision); require(revision == change.baseRevision + 1)
        return buildJsonObject { put("changeId", change.changeId); put("expectedRevision", change.baseRevision.toString()); put("revision", revision.toString()); put("schemaVersion", 1); put("cryptoVersion", 1); put("keyVersion", 1); put("nonce", change.nonce); put("ciphertext", change.ciphertext) }.toString()
    }

    private fun <T, R> decodePage(raw: String, decode: (JsonObject) -> T, build: (List<T>, String, Boolean) -> R): R {
        val root = parseStrictJsonObject(raw).also { it.requireExactKeys("changes", "nextCursor", "hasMore") }
        val changes = root.requireArray("changes").also { require(it.size <= 100) }.map { decode(it as? JsonObject ?: error("Invalid change")) }
        return build(changes, root.strictString("nextCursor", 1, 260).also(::requireCursor), root.strictBoolean("hasMore"))
    }

    private fun deltaJson(change: SyncEncryptedDelta): JsonObject = buildJsonObject {
        put("changeId", change.changeId); put("mutationId", change.mutationId); put("workspaceId", change.workspaceId); put("deviceId", change.writerDeviceId); put("entity", "tabs"); put("entityId", change.targetDeviceId); put("operation", "delta"); put("baseRevision", change.baseRevision.toString()); change.revision?.let { put("revision", it.toString()) }; put("schemaVersion", 2); put("cryptoVersion", 1); put("keyVersion", 1); put("nonce", change.nonce); put("ciphertext", change.ciphertext)
    }

    private fun decodeDelta(value: JsonObject): SyncEncryptedDelta {
        value.requireExactKeys("changeId", "mutationId", "workspaceId", "deviceId", "entity", "entityId", "operation", "baseRevision", "revision", "schemaVersion", "cryptoVersion", "keyVersion", "nonce", "ciphertext")
        require(value.strictString("entity") == "tabs" && value.strictString("operation") == "delta" && value.strictInt("schemaVersion") == 2 && value.strictInt("cryptoVersion") == 1 && value.strictInt("keyVersion") == 1)
        return SyncEncryptedDelta(value.identifier("changeId"), value.identifier("mutationId"), value.identifier("workspaceId"), value.identifier("deviceId"), value.identifier("entityId"), value.revision("baseRevision"), value.revision("revision"), value.strictString("nonce", 16, 16).also { SyncBase64.decode(it, expectedBytes = 12) }, value.strictString("ciphertext", 22, 262_166).also { SyncBase64.decode(it, maxBytes = 196_624) }).also { require(it.revision == it.baseRevision + 1) }
    }

    private fun decodeDevice(value: JsonObject): SyncDeviceRecord {
        value.requireExactKeys("deviceId", "publicKeyAlgorithm", "publicKey", "encryptedName", "encryptedIcon", "capabilities", "status", "createdAt", "lastSeenAt")
        require(value.strictString("publicKeyAlgorithm") == "ECDH-P256-SPKI")
        val publicKey = value.strictString("publicKey", 122, 122).also { SyncBase64.decode(it, expectedBytes = 91) }
        val capabilityValues = value.requireArray("capabilities").also { require(it.size in 1..16) }.map(JsonElement::requireString)
        val capabilities = capabilityValues.toSet()
        require(capabilities.size == capabilityValues.size && capabilities.all { it in setOf("tabs", "bookmarks", "groups") })
        return SyncDeviceRecord(value.identifier("deviceId"), publicKey, decodeEncrypted(value.requireObject("encryptedName")), if (value.isJsonNull("encryptedIcon")) null else decodeEncrypted(value.requireObject("encryptedIcon"), 4_096), capabilities, when (value.strictString("status")) { "active" -> SyncDeviceStatus.Active; "revoked" -> SyncDeviceStatus.Revoked; else -> error("Invalid status") }, value.strictString("createdAt", 1, 64).also(::requireInstant), value.strictString("lastSeenAt", 1, 64).also(::requireInstant))
    }

    private fun decodeChange(value: JsonObject): SyncEncryptedChange {
        value.requireExactKeys("changeId", "deviceId", "entity", "entityId", "operation", "baseRevision", "revision", "schemaVersion", "cryptoVersion", "keyVersion", "nonce", "ciphertext")
        require(value.strictString("entity") == "tabs" && value.strictString("operation") == "snapshot" && value.strictInt("schemaVersion") == 1 && value.strictInt("cryptoVersion") == 1 && value.strictInt("keyVersion") == 1)
        return SyncEncryptedChange(value.identifier("changeId"), value.identifier("deviceId"), value.identifier("entityId"), value.revision("baseRevision"), value.revision("revision"), value.strictString("nonce", 16, 16).also { SyncBase64.decode(it, expectedBytes = 12) }, value.strictString("ciphertext", 22, 524_288).also { SyncBase64.decode(it) }).also { require(it.revision == it.baseRevision + 1) }
    }

    private fun decodeRecoveryEnvelope(value: JsonObject): SyncRecoveryEnvelope { value.requireExactKeys("cryptoVersion", "nonce", "ciphertext"); require(value.strictInt("cryptoVersion") == 1); return SyncRecoveryEnvelope(nonce = value.strictString("nonce", 16, 16).also { SyncBase64.decode(it, expectedBytes = 12) }, ciphertext = value.strictString("ciphertext", 64, 64).also { SyncBase64.decode(it, expectedBytes = 48) }) }
    private fun decodeEncrypted(value: JsonObject, maxCiphertextBytes: Int = 393_216): SyncEncryptedValue { value.requireExactKeys("nonce", "ciphertext"); return SyncEncryptedValue(value.strictString("nonce", 16, 16).also { SyncBase64.decode(it, expectedBytes = 12) }, value.strictString("ciphertext", 22, 524_288).also { SyncBase64.decode(it, maxBytes = maxCiphertextBytes) }) }
    private fun tabJson(value: SyncTab): JsonObject = buildJsonObject { put("candyId", value.candyId); put("windowId", value.windowId); put("index", value.index); put("groupId", value.groupId?.let(::JsonPrimitive) ?: JsonNull); put("active", value.active); put("pinned", value.pinned); put("title", value.title); put("url", value.url) }
    private fun decodeTab(value: JsonObject): SyncTab { value.requireExactKeys("candyId", "windowId", "index", "groupId", "active", "pinned", "title", "url"); return SyncTab(value.candyIdentifier("candyId"), value.nonNegativeInt("windowId"), value.nonNegativeInt("index"), if (value.isJsonNull("groupId")) null else value.nonNegativeInt("groupId"), value.strictBoolean("active"), value.strictBoolean("pinned"), value.strictString("title", 0, SyncTabRules.MAX_TITLE_LENGTH), value.strictString("url", 1, SyncTabRules.MAX_URL_LENGTH)) }
    private fun SyncEncryptedValue.json() = buildJsonObject { put("nonce", nonce); put("ciphertext", ciphertext) }
    private fun SyncRecoveryEnvelope.json() = buildJsonObject { put("cryptoVersion", cryptoVersion); put("nonce", nonce); put("ciphertext", ciphertext) }
    private fun JsonObject.identifier(name: String) = strictString(name, 1, 128).also { require(it.matches(IDENTIFIER)) }
    private fun JsonObject.candyIdentifier(name: String) = strictString(name, 1, 128).also { require(it.matches(CANDY_IDENTIFIER)) }
    private fun JsonObject.nonNegativeInt(name: String) = strictInt(name).also { require(it >= 0) }
    private fun JsonObject.revision(name: String): Long { val value = strictString(name, 1, 19); require(value == "0" || value.first() in '1'..'9'); return value.toLongOrNull()?.also { require(it >= 0) } ?: error("Invalid revision") }
    private fun requireInstant(value: String) { require(runCatching { Instant.parse(value) }.isSuccess) }
    fun requireCursor(value: String) { val separator = value.lastIndexOf('.'); require(separator in 1 until value.lastIndex); require(value.substring(0, separator).matches(IDENTIFIER)); val sequence = value.substring(separator + 1); require(sequence == "0" || sequence.firstOrNull() in '1'..'9'); require(sequence.toLongOrNull()?.let { it >= 0 } == true) }
    private val IDENTIFIER = Regex("[A-Za-z0-9_-]+")
    private val CANDY_IDENTIFIER = Regex("[A-Za-z0-9._:-]+")
    private const val MAX_PLAINTEXT_BYTES = 393_216
}
