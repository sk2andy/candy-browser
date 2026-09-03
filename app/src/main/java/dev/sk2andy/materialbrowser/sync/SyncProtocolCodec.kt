package dev.sk2andy.materialbrowser.sync

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

object SyncProtocolCodec {
    fun decodeBootstrap(raw: String): SyncBootstrap {
        val value = parseStrictJsonObject(raw)
        value.requireExactKeys(
            "protocolVersion",
            "cryptoVersion",
            "workspaceId",
            "serverEpoch",
            "initialized",
            "kdf",
            "recoveryEnvelope",
        )
        require(value.strictInt("protocolVersion") == 1 && value.strictInt("cryptoVersion") == 1)
        val initialized = value.get("initialized") as? Boolean
            ?: throw IllegalArgumentException("Invalid initialized")
        val kdf = value.getJSONObject("kdf").also {
            it.requireExactKeys("algorithm", "salt", "memoryKiB", "iterations", "parallelism", "keyBytes")
            require(it.strictString("algorithm") == "argon2id-v1")
            require(it.strictInt("memoryKiB") == 65_536)
            require(it.strictInt("iterations") == 3)
            require(it.strictInt("parallelism") == 4)
            require(it.strictInt("keyBytes") == 32)
        }
        val recovery = if (value.isNull("recoveryEnvelope")) {
            null
        } else {
            decodeRecoveryEnvelope(value.getJSONObject("recoveryEnvelope"))
        }
        require(initialized == (recovery != null))
        return SyncBootstrap(
            workspaceId = value.identifier("workspaceId"),
            serverEpoch = value.identifier("serverEpoch"),
            initialized = initialized,
            kdf = SyncRecoveryKdf(
                salt = kdf.strictString("salt", 22, 22).also { SyncBase64.decode(it, expectedBytes = 16) },
                memoryKiB = 65_536,
                iterations = 3,
                parallelism = 4,
            ),
            recoveryEnvelope = recovery,
        )
    }

    fun decodeDevices(raw: String): List<SyncDeviceRecord> {
        val root = parseStrictJsonObject(raw)
        root.requireExactKeys("devices")
        val array = root.get("devices") as? JSONArray ?: throw IllegalArgumentException("Invalid devices")
        require(array.length() <= 1_000)
        return buildList {
            repeat(array.length()) { index -> add(decodeDevice(array.getJSONObject(index))) }
        }
    }

    fun decodeDeviceIcon(raw: String): SyncDeviceIconDescriptor {
        val value = parseStrictJsonObject(raw)
        value.requireExactKeys("schemaVersion", "catalogId", "accentHue")
        require(value.strictInt("schemaVersion") == 1)
        return SyncDeviceIconDescriptor(
            catalogId = value.strictString("catalogId", 1, 48),
            accentHue = value.strictInt("accentHue"),
        ).also(SyncDeviceIconRules::requireValid)
    }

    fun encodeTabSnapshot(snapshot: SyncTabSnapshot): String {
        val safe = requireNotNull(SyncTabRules.normalizeSnapshot(snapshot))
        return JSONObject()
            .put("schemaVersion", 1)
            .put("capturedAt", safe.capturedAt.also(::requireInstant))
            .put(
                "tabs",
                JSONArray().also { array ->
                    safe.tabs.forEach { tab ->
                        array.put(
                            JSONObject()
                                .put("candyId", tab.candyId)
                                .put("windowId", tab.windowId)
                                .put("index", tab.index)
                                .put("groupId", tab.groupId ?: JSONObject.NULL)
                                .put("active", tab.active)
                                .put("pinned", tab.pinned)
                                .put("title", tab.title)
                                .put("url", tab.url),
                        )
                    }
                },
            )
            .toString()
    }

    fun decodeTabSnapshot(raw: String): SyncTabSnapshot {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_PLAINTEXT_BYTES)
        val root = parseStrictJsonObject(raw)
        root.requireExactKeys("schemaVersion", "capturedAt", "tabs")
        require(root.strictInt("schemaVersion") == 1)
        val array = root.get("tabs") as? JSONArray ?: throw IllegalArgumentException("Invalid tabs")
        require(array.length() <= SyncTabRules.MAX_TABS)
        val snapshot = SyncTabSnapshot(
            capturedAt = root.strictString("capturedAt", 1, 64).also(::requireInstant),
            tabs = buildList {
                repeat(array.length()) { index ->
                    val tab = array.getJSONObject(index)
                    tab.requireExactKeys(
                        "candyId",
                        "windowId",
                        "index",
                        "groupId",
                        "active",
                        "pinned",
                        "title",
                        "url",
                    )
                    add(
                        SyncTab(
                            candyId = tab.candyIdentifier("candyId"),
                            windowId = tab.nonNegativeInt("windowId"),
                            index = tab.nonNegativeInt("index"),
                            groupId = if (tab.isNull("groupId")) null else tab.nonNegativeInt("groupId"),
                            active = tab.strictBoolean("active"),
                            pinned = tab.strictBoolean("pinned"),
                            title = tab.strictString("title", 0, SyncTabRules.MAX_TITLE_LENGTH),
                            url = tab.strictString("url", 1, SyncTabRules.MAX_URL_LENGTH),
                        ),
                    )
                }
            },
        )
        return requireNotNull(SyncTabRules.normalizeSnapshot(snapshot))
    }

    fun decodePull(raw: String): SyncPullPage {
        val root = parseStrictJsonObject(raw)
        root.requireExactKeys("changes", "nextCursor", "hasMore")
        val changes = root.get("changes") as? JSONArray ?: throw IllegalArgumentException("Invalid changes")
        require(changes.length() <= 100)
        return SyncPullPage(
            changes = buildList {
                repeat(changes.length()) { index -> add(decodeChange(changes.getJSONObject(index))) }
            },
            nextCursor = root.strictString("nextCursor", 1, 260).also(::requireCursor),
            hasMore = root.strictBoolean("hasMore"),
        )
    }

    fun encodeDelta(change: SyncEncryptedDelta): String {
        require(change.revision == null)
        return encodeDeltaObject(change).toString()
    }

    fun decodeDeltaPull(raw: String): SyncDeltaPullPage {
        val root = parseStrictJsonObject(raw)
        root.requireExactKeys("changes", "nextCursor", "hasMore")
        val changes = root.get("changes") as? JSONArray ?: throw IllegalArgumentException("Invalid changes")
        require(changes.length() <= 100)
        return SyncDeltaPullPage(
            changes = buildList {
                repeat(changes.length()) { index -> add(decodeDelta(changes.getJSONObject(index))) }
            },
            nextCursor = root.strictString("nextCursor", 1, 260).also(::requireCursor),
            hasMore = root.strictBoolean("hasMore"),
        )
    }

    fun decodeRealtimeEvent(raw: String): SyncRealtimeEvent {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_PLAINTEXT_BYTES)
        val root = parseStrictJsonObject(raw)
        root.requireExactKeys("type", "cursor", "change")
        require(root.strictString("type") == "change")
        return SyncRealtimeEvent(
            cursor = root.strictString("cursor", 1, 260).also(::requireCursor),
            change = decodeDelta(root.getJSONObject("change")),
        )
    }

    fun decodeServerSnapshot(raw: String): SyncServerSnapshot {
        val root = parseStrictJsonObject(raw)
        root.requireExactKeys("cursor", "changes", "tabSnapshots")
        val tabSnapshots = root.get("tabSnapshots") as? JSONArray
            ?: throw IllegalArgumentException("Invalid snapshots")
        require(tabSnapshots.length() <= 1_000)
        return SyncServerSnapshot(
            changes = buildList {
                repeat(tabSnapshots.length()) { index -> add(decodeChange(tabSnapshots.getJSONObject(index))) }
            },
            cursor = root.strictString("cursor", 1, 260).also(::requireCursor),
        )
    }

    fun encodeEnrollment(
        identity: SyncDeviceIdentity,
        name: SyncEncryptedValue,
        icon: SyncEncryptedValue,
        recovery: SyncRecoveryEnvelope?,
    ): String = JSONObject()
        .put("deviceKeyFingerprint", identity.fingerprint)
        .put("publicKeyAlgorithm", "ECDH-P256-SPKI")
        .put("publicKey", SyncBase64.encode(identity.publicKeySpki))
        .put("encryptedName", name.toJson())
        .put("encryptedIcon", icon.toJson())
        .put("capabilities", JSONArray(listOf("tabs")))
        .apply { if (recovery != null) put("recoveryEnvelope", recovery.toJson()) }
        .toString()

    fun encodePutSnapshot(change: SyncEncryptedChange): String {
        val revision = requireNotNull(change.revision)
        require(revision == change.baseRevision + 1)
        return JSONObject()
            .put("changeId", change.changeId)
            .put("expectedRevision", change.baseRevision.toString())
            .put("revision", revision.toString())
            .put("schemaVersion", 1)
            .put("cryptoVersion", 1)
            .put("keyVersion", 1)
            .put("nonce", change.nonce)
            .put("ciphertext", change.ciphertext)
            .toString()
    }

    private fun encodeDeltaObject(change: SyncEncryptedDelta): JSONObject = JSONObject()
        .put("changeId", change.changeId)
        .put("mutationId", change.mutationId)
        .put("workspaceId", change.workspaceId)
        .put("deviceId", change.writerDeviceId)
        .put("entity", "tabs")
        .put("entityId", change.targetDeviceId)
        .put("operation", "delta")
        .put("baseRevision", change.baseRevision.toString())
        .apply { change.revision?.let { put("revision", it.toString()) } }
        .put("schemaVersion", 2)
        .put("cryptoVersion", 1)
        .put("keyVersion", 1)
        .put("nonce", change.nonce)
        .put("ciphertext", change.ciphertext)

    private fun decodeDelta(value: JSONObject): SyncEncryptedDelta {
        val required = setOf(
            "changeId",
            "mutationId",
            "workspaceId",
            "deviceId",
            "entity",
            "entityId",
            "operation",
            "baseRevision",
            "revision",
            "schemaVersion",
            "cryptoVersion",
            "keyVersion",
            "nonce",
            "ciphertext",
        )
        require(value.keys().asSequence().toSet() == required)
        require(value.strictString("entity") == "tabs" && value.strictString("operation") == "delta")
        require(value.strictInt("schemaVersion") == 2)
        require(value.strictInt("cryptoVersion") == 1)
        require(value.strictInt("keyVersion") == 1)
        return SyncEncryptedDelta(
            changeId = value.identifier("changeId"),
            mutationId = value.identifier("mutationId"),
            workspaceId = value.identifier("workspaceId"),
            writerDeviceId = value.identifier("deviceId"),
            targetDeviceId = value.identifier("entityId"),
            baseRevision = value.revision("baseRevision"),
            revision = value.revision("revision"),
            nonce = value.strictString("nonce", 16, 16).also { SyncBase64.decode(it, expectedBytes = 12) },
            ciphertext = value.strictString("ciphertext", 22, 262_166).also {
                SyncBase64.decode(it, maxBytes = 196_624)
            },
        ).also { require(it.revision == it.baseRevision + 1) }
    }

    private fun decodeDevice(value: JSONObject): SyncDeviceRecord {
        value.requireExactKeys(
            "deviceId",
            "publicKeyAlgorithm",
            "publicKey",
            "encryptedName",
            "encryptedIcon",
            "capabilities",
            "status",
            "createdAt",
            "lastSeenAt",
        )
        require(value.strictString("publicKeyAlgorithm") == "ECDH-P256-SPKI")
        val publicKey = value.strictString("publicKey", 122, 122)
        SyncBase64.decode(publicKey, expectedBytes = 91)
        val capabilities = value.getJSONArray("capabilities")
        require(capabilities.length() in 1..16)
        val capabilitySet = buildSet {
            repeat(capabilities.length()) { index ->
                val capability = capabilities.get(index) as? String
                    ?: throw IllegalArgumentException("Invalid capability")
                require(capability in setOf("tabs", "bookmarks", "groups") && add(capability))
            }
        }
        return SyncDeviceRecord(
            deviceId = value.identifier("deviceId"),
            publicKey = publicKey,
            encryptedName = decodeEncrypted(value.getJSONObject("encryptedName")),
            encryptedIcon = if (value.isNull("encryptedIcon")) null else decodeEncrypted(
                value.getJSONObject("encryptedIcon"),
                maxCiphertextBytes = 4_096,
            ),
            capabilities = capabilitySet,
            status = when (value.strictString("status")) {
                "active" -> SyncDeviceStatus.Active
                "revoked" -> SyncDeviceStatus.Revoked
                else -> throw IllegalArgumentException("Invalid status")
            },
            createdAt = value.strictString("createdAt", 1, 64).also(::requireInstant),
            lastSeenAt = value.strictString("lastSeenAt", 1, 64).also(::requireInstant),
        )
    }

    private fun decodeChange(value: JSONObject): SyncEncryptedChange {
        value.requireExactKeys(
            "changeId",
            "deviceId",
            "entity",
            "entityId",
            "operation",
            "baseRevision",
            "revision",
            "schemaVersion",
            "cryptoVersion",
            "keyVersion",
            "nonce",
            "ciphertext",
        )
        require(value.strictString("entity") == "tabs" && value.strictString("operation") == "snapshot")
        require(value.strictInt("schemaVersion") == 1)
        require(value.strictInt("cryptoVersion") == 1)
        require(value.strictInt("keyVersion") == 1)
        return SyncEncryptedChange(
            changeId = value.identifier("changeId"),
            writerDeviceId = value.identifier("deviceId"),
            targetDeviceId = value.identifier("entityId"),
            baseRevision = value.revision("baseRevision"),
            revision = value.revision("revision"),
            nonce = value.strictString("nonce", 16, 16).also { SyncBase64.decode(it, expectedBytes = 12) },
            ciphertext = value.strictString("ciphertext", 22, 524_288).also { SyncBase64.decode(it) },
        ).also { require(it.revision == it.baseRevision + 1) }
    }

    private fun decodeRecoveryEnvelope(value: JSONObject): SyncRecoveryEnvelope {
        value.requireExactKeys("cryptoVersion", "nonce", "ciphertext")
        require(value.strictInt("cryptoVersion") == 1)
        return SyncRecoveryEnvelope(
            nonce = value.strictString("nonce", 16, 16).also { SyncBase64.decode(it, expectedBytes = 12) },
            ciphertext = value.strictString("ciphertext", 64, 64).also { SyncBase64.decode(it, expectedBytes = 48) },
        )
    }

    private fun decodeEncrypted(value: JSONObject, maxCiphertextBytes: Int = 393_216): SyncEncryptedValue {
        value.requireExactKeys("nonce", "ciphertext")
        return SyncEncryptedValue(
            nonce = value.strictString("nonce", 16, 16).also { SyncBase64.decode(it, expectedBytes = 12) },
            ciphertext = value.strictString("ciphertext", 22, 524_288).also {
                SyncBase64.decode(it, maxBytes = maxCiphertextBytes)
            },
        )
    }

    private fun SyncEncryptedValue.toJson(): JSONObject = JSONObject()
        .put("nonce", nonce)
        .put("ciphertext", ciphertext)

    private fun SyncRecoveryEnvelope.toJson(): JSONObject = JSONObject()
        .put("cryptoVersion", cryptoVersion)
        .put("nonce", nonce)
        .put("ciphertext", ciphertext)

    private fun JSONObject.identifier(name: String): String = strictString(name, 1, 128).also {
        require(it.matches(IDENTIFIER))
    }

    private fun JSONObject.candyIdentifier(name: String): String = strictString(name, 1, 128).also {
        require(it.matches(CANDY_IDENTIFIER))
    }

    private fun JSONObject.nonNegativeInt(name: String): Int = strictInt(name).also { require(it >= 0) }

    private fun JSONObject.strictBoolean(name: String): Boolean = get(name) as? Boolean
        ?: throw IllegalArgumentException("Invalid $name")

    private fun JSONObject.revision(name: String): Long {
        val value = strictString(name, 1, 19)
        require(value == "0" || value.first() in '1'..'9')
        return value.toLongOrNull()?.also { require(it >= 0) }
            ?: throw IllegalArgumentException("Invalid revision")
    }

    private fun requireInstant(value: String) {
        require(runCatching { Instant.parse(value) }.isSuccess)
    }

    internal fun requireCursor(value: String) {
        val separator = value.lastIndexOf('.')
        require(separator in 1 until value.lastIndex)
        require(value.substring(0, separator).matches(IDENTIFIER))
        val sequence = value.substring(separator + 1)
        require(sequence == "0" || sequence.firstOrNull() in '1'..'9')
        require(sequence.toLongOrNull()?.let { it >= 0 } == true)
    }

    private val IDENTIFIER = Regex("[A-Za-z0-9_-]+")
    private val CANDY_IDENTIFIER = Regex("[A-Za-z0-9._:-]+")
    private const val MAX_PLAINTEXT_BYTES = 393_216
}
