package dev.sk2andy.materialbrowser.sync

/**
 * Cross-platform Candy Sync decision owner. Stores, crypto, network and scheduling stay outside
 * this class; it only serializes their already-defined protocol operations through [SyncDispatch].
 */
class CandySyncRepository(
    private val settingsStore: SyncSettingsStore,
    private val vaultStore: SyncVaultStore,
    private val cacheStore: SyncCacheStore,
    private val iconCatalog: SyncDeviceIconCatalog,
    private val transportFactory: (String) -> SyncTransport,
    private val crypto: SyncCryptoProvider,
    private val recoveryKeyDeriver: SyncRecoveryKeyDeriver,
    private val dispatch: SyncDispatch,
) : AutoCloseable {
    private val listeners = mutableSetOf<(SyncRepositoryState) -> Unit>()
    private var cache = cacheStore.load()
    private var currentDeviceId = loadCurrentDeviceId()
    private var configurationWriteFailed = false
    private var realtimeConnection: AutoCloseable? = null
    private var realtimeEnabled = false
    private var realtimeAttempt = 0
    private var realtimeGeneration = 0L
    private var lastSuccessAt: String? = null
    private var state = stateFrom(settingsStore.load(), if (settingsStore.load() == null || !hasVault()) SyncStatus.Unconfigured else SyncStatus.Ready)

    fun currentState(): SyncRepositoryState = state

    fun observe(listener: (SyncRepositoryState) -> Unit): AutoCloseable {
        listeners += listener
        listener(state)
        return AutoCloseable { listeners -= listener }
    }

    fun configure(settings: SyncConnectionSettings): Boolean {
        val normalized = SyncConnectionRules.normalize(settings, iconCatalog) ?: return false
        dispatch.dispatch { configureNow(normalized) }
        return true
    }

    fun enroll(serverPassword: CharArray, passphrase: CharArray, onComplete: (SyncEnrollmentOutcome) -> Unit) {
        dispatch.dispatch { onComplete(enrollNow(serverPassword, passphrase)) }
    }

    fun refresh(onComplete: (Boolean) -> Unit = {}) {
        dispatch.dispatch { onComplete(refreshNow()) }
    }

    fun mutate(mutation: SyncPendingMutation, onComplete: (SyncWriteOutcome) -> Unit) {
        dispatch.dispatch { onComplete(mutateNow(mutation)) }
    }

    fun startRealtime() {
        realtimeEnabled = true
        dispatch.dispatch(::connectRealtimeNow)
    }

    fun stopRealtime() {
        realtimeEnabled = false
        dispatch.dispatch(::closeRealtimeConnection)
    }

    override fun close() {
        realtimeEnabled = false
        closeRealtimeConnection()
        listeners.clear()
    }

    private fun configureNow(settings: SyncConnectionSettings) {
        val previous = settingsStore.load()
        if (!settingsStore.save(settings)) {
            configurationWriteFailed = true
            publish(stateFrom(previous, SyncStatus.Offline))
            return
        }
        configurationWriteFailed = false
        if (previous != null && (previous.endpoint != settings.endpoint || previous.username != settings.username)) {
            closeRealtimeConnection()
            vaultStore.clear()
            cacheStore.clear()
            cache = SyncCache(cursor = "", profiles = emptyMap())
            currentDeviceId = null
        }
        publish(stateFrom(settings, if (hasVault()) SyncStatus.Ready else SyncStatus.Unconfigured))
        if (realtimeEnabled) connectRealtimeNow()
    }

    private fun enrollNow(serverPassword: CharArray, passphrase: CharArray): SyncEnrollmentOutcome {
        if (serverPassword.isEmpty() || passphrase.size < MIN_PASSPHRASE_CHARS || serverPassword.contentEquals(passphrase)) {
            serverPassword.fill('\u0000'); passphrase.fill('\u0000')
            return SyncEnrollmentOutcome.InvalidConfiguration
        }
        if (configurationWriteFailed) {
            serverPassword.fill('\u0000'); passphrase.fill('\u0000')
            return SyncEnrollmentOutcome.InvalidConfiguration
        }
        val settings = settingsStore.load() ?: return SyncEnrollmentOutcome.InvalidConfiguration.also {
            serverPassword.fill('\u0000'); passphrase.fill('\u0000')
            publish(stateFrom(null, SyncStatus.Unconfigured))
        }
        publish(stateFrom(settings, SyncStatus.Enrolling))
        val password = serverPassword.concatToString().encodeToByteArray()
        val phrase = passphrase.concatToString().encodeToByteArray()
        serverPassword.fill('\u0000'); passphrase.fill('\u0000')
        var recovery: ByteArray? = null
        var workspace: ByteArray? = null
        return try {
            val transport = transportFactory(settings.endpoint)
            transport.discover()
            val bootstrap = transport.bootstrap(settings.username, password)
            recovery = recoveryKeyDeriver.derive(phrase, bootstrap.kdf)
            workspace = if (bootstrap.initialized) {
                crypto.unlockRecoveryEnvelope(
                    recovery,
                    requireNotNull(bootstrap.recoveryEnvelope),
                    bootstrap.workspaceId,
                ) ?: throw WrongPassphraseException()
            } else {
                crypto.randomBytes(32)
            }
            val identity = crypto.generateDeviceIdentity()
            val icon = SyncDeviceIconDescriptor(settings.iconCatalogId, settings.iconAccentHue)
            require(iconCatalog.contains(icon.catalogId))
            val enrollment = transport.enroll(
                settings.username, password, identity,
                crypto.encryptDeviceName(workspace, bootstrap.workspaceId, identity.fingerprint, settings.deviceName),
                crypto.encryptDeviceIcon(workspace, bootstrap.workspaceId, identity.fingerprint, icon),
                if (bootstrap.initialized) null else crypto.createRecoveryEnvelope(recovery, workspace, bootstrap.workspaceId),
            )
            require(enrollment.workspaceId == bootstrap.workspaceId)
            val secrets = SyncVaultSecrets(bootstrap.workspaceId, enrollment.deviceId, enrollment.token, workspace.copyOf(), identity.privateKeyPkcs8.copyOf())
            try { require(vaultStore.save(secrets)) } finally { secrets.clear() }
            val now = crypto.nowIso8601()
            cache = SyncCache(enrollment.cursor, mapOf(enrollment.deviceId to SyncProfile(enrollment.deviceId, settings.deviceName, icon, 0, emptyList(), now)))
            require(cacheStore.save(cache))
            currentDeviceId = enrollment.deviceId
            publish(stateFrom(settings, SyncStatus.Ready, now))
            if (realtimeEnabled) connectRealtimeNow()
            SyncEnrollmentOutcome.Enrolled
        } catch (error: Throwable) {
            publish(stateFrom(settings, statusFor(error)))
            when {
                error is SyncTransportException && error.statusCode == 401 -> SyncEnrollmentOutcome.AuthenticationFailed
                error is WrongPassphraseException -> SyncEnrollmentOutcome.WrongPassphrase
                error is IllegalArgumentException -> SyncEnrollmentOutcome.IncompatibleServer
                else -> SyncEnrollmentOutcome.Failed
            }
        } finally {
            password.fill(0); phrase.fill(0); recovery?.fill(0); workspace?.fill(0)
        }
    }

    private fun refreshNow(): Boolean {
        val settings = settingsStore.load() ?: return false
        val secrets = vaultStore.load() ?: return false
        publish(stateFrom(settings, SyncStatus.Syncing))
        return try {
            refreshRemote(settings, secrets)
            cache.pendingMutations.toList().forEach { mutation ->
                when (pushWithCasRetry(settings, secrets, mutation)) {
                    is SyncWriteOutcome.Synced, is SyncWriteOutcome.Rejected -> Unit
                    else -> throw SyncTransportException(null, "pending_write_failed")
                }
            }
            publish(stateFrom(settings, SyncStatus.Ready, crypto.nowIso8601()))
            true
        } catch (error: Throwable) {
            publish(stateFrom(settings, statusFor(error)))
            false
        } finally { secrets.clear() }
    }

    private fun refreshRemote(settings: SyncConnectionSettings, secrets: SyncVaultSecrets) {
        val transport = transportFactory(settings.endpoint)
        transport.discover()
        val devices = transport.listDevices(secrets.deviceToken).filter { it.status == SyncDeviceStatus.Active }
        val metadata = devices.associate { device ->
            val fingerprint = crypto.fingerprint(SyncBase64.decode(device.publicKey, expectedBytes = 91))
            val icon = device.encryptedIcon?.let { crypto.decryptDeviceIcon(secrets.workspaceKey, secrets.workspaceId, fingerprint, it) }
                ?: SyncDeviceIconRules.defaultForAndroid(fingerprint).copy(catalogId = "browser")
            require(iconCatalog.contains(icon.catalogId))
            device.deviceId to Triple(crypto.decryptDeviceName(secrets.workspaceKey, secrets.workspaceId, fingerprint, device.encryptedName), icon, device)
        }
        var working = cache.copy(profiles = metadata.mapValues { (id, value) ->
            val old = cache.profiles[id]
            SyncProfile(id, value.first, value.second, old?.revision ?: 0, old?.tabs.orEmpty(), value.third.lastSeenAt)
        })
        working = recoverV1(transport, secrets, working, metadata.keys)
        if (transport.supportsTabMutationsV2()) {
            if (working.profiles.values.any { profile ->
                    profile.deviceId != secrets.deviceId && profile.revision == 0L
                }
            ) {
                val snapshot = transport.snapshot(secrets.deviceToken)
                working = applyChanges(working, snapshot.changes, metadata.keys, secrets.workspaceKey)
                    .copy(cursor = snapshot.cursor)
            }
            working = try {
                pullDeltas(transport, secrets, working, metadata.keys)
            } catch (error: SyncTransportException) {
                if (error.statusCode != 410 || error.problemCode != "cursor_reset") throw error
                pullDeltas(transport, secrets, working.copy(deltaCursor = ""), metadata.keys)
            }
        }
        require(cacheStore.save(working)); transport.acknowledge(secrets.deviceToken, working.cursor); cache = working
    }

    private fun recoverV1(transport: SyncTransport, secrets: SyncVaultSecrets, initial: SyncCache, ids: Set<String>): SyncCache = try {
        pullSnapshots(transport, secrets, initial, ids)
    } catch (error: SyncTransportException) {
        if (error.statusCode != 410 || error.problemCode != "cursor_reset") throw error
        val reset = initial.copy(cursor = "", profiles = initial.profiles.mapValues { (_, profile) -> profile.copy(revision = 0, tabs = emptyList()) })
        try {
            val snapshot = transport.snapshot(secrets.deviceToken)
            applyChanges(reset, snapshot.changes, ids, secrets.workspaceKey).copy(cursor = snapshot.cursor)
        } catch (snapshotError: SyncTransportException) {
            if (snapshotError.statusCode != 413 || snapshotError.problemCode != "snapshot_too_large") throw snapshotError
            pullSnapshots(transport, secrets, reset, ids)
        }
    }

    private fun pullSnapshots(transport: SyncTransport, secrets: SyncVaultSecrets, initial: SyncCache, ids: Set<String>): SyncCache {
        var working = initial; val cursors = mutableSetOf(working.cursor)
        repeat(MAX_PULL_PAGES) {
            val page = transport.pull(secrets.deviceToken, working.cursor)
            require(page.nextCursor != working.cursor || !page.hasMore); require(cursors.add(page.nextCursor) || !page.hasMore)
            working = applyChanges(working, page.changes, ids, secrets.workspaceKey).copy(cursor = page.nextCursor)
            if (!page.hasMore) return working
        }
        throw IllegalArgumentException("Too many sync pages")
    }

    private fun pullDeltas(transport: SyncTransport, secrets: SyncVaultSecrets, initial: SyncCache, ids: Set<String>): SyncCache {
        var working = initial; val cursors = mutableSetOf(working.deltaCursor)
        repeat(MAX_PULL_PAGES) {
            val page = transport.pullDeltas(secrets.deviceToken, working.deltaCursor)
            require(page.nextCursor != working.deltaCursor || !page.hasMore); require(cursors.add(page.nextCursor) || !page.hasMore)
            working = applyDeltas(working, page.changes, ids, secrets).copy(deltaCursor = page.nextCursor)
            if (!page.hasMore) return working
        }
        throw IllegalArgumentException("Too many delta pages")
    }

    private fun applyChanges(initial: SyncCache, changes: List<SyncEncryptedChange>, ids: Set<String>, key: ByteArray): SyncCache {
        var profiles = initial.profiles
        changes.sortedWith(compareBy({ it.targetDeviceId }, { it.revision ?: Long.MAX_VALUE })).forEach { change ->
            val current = profiles[change.targetDeviceId] ?: return@forEach
            val revision = requireNotNull(change.revision)
            if (change.targetDeviceId in ids && revision > current.revision) profiles += change.targetDeviceId to current.copy(revision = revision, tabs = crypto.decryptTabSnapshot(key, change).tabs)
        }
        return initial.copy(profiles = profiles)
    }

    private fun applyDeltas(initial: SyncCache, changes: List<SyncEncryptedDelta>, ids: Set<String>, secrets: SyncVaultSecrets): SyncCache {
        var profiles = initial.profiles
        changes.forEach { change ->
            require(change.workspaceId == secrets.workspaceId)
            val current = profiles[change.targetDeviceId] ?: return@forEach
            val revision = requireNotNull(change.revision)
            if (change.targetDeviceId !in ids || revision <= current.revision) return@forEach
            require(revision == current.revision + 1)
            val next = when (val result = SyncTabRules.apply(current, crypto.decryptTabMutation(secrets.workspaceKey, change))) {
                is SyncMutationResult.Applied -> result.profile
                SyncMutationResult.AlreadyApplied, SyncMutationResult.MissingTab -> current
                SyncMutationResult.InvalidTab -> throw IllegalArgumentException("Invalid encrypted mutation")
            }
            profiles += change.targetDeviceId to next.copy(revision = revision)
        }
        return initial.copy(profiles = profiles)
    }

    private fun mutateNow(mutation: SyncPendingMutation): SyncWriteOutcome {
        val settings = settingsStore.load() ?: return SyncWriteOutcome.Rejected("unconfigured")
        val secrets = vaultStore.load() ?: return SyncWriteOutcome.Rejected("unenrolled")
        return try {
            val existing = cache.pendingMutations.firstOrNull { it.mutationId == mutation.mutationId }
            if (existing != null && existing != mutation) return SyncWriteOutcome.Rejected("duplicate-mutation-id")
            val pending = existing ?: mutation
            if (existing == null) {
                if (cache.pendingMutations.size >= MAX_PENDING_MUTATIONS) return SyncWriteOutcome.Rejected("outbox-full")
                val profile = optimisticProfiles()[mutation.targetDeviceId] ?: return SyncWriteOutcome.Rejected("unknown-profile")
                when (SyncTabRules.apply(profile, mutation)) {
                    is SyncMutationResult.Applied -> Unit
                    SyncMutationResult.AlreadyApplied -> return SyncWriteOutcome.Synced(profile, cache.deltaCursor.ifEmpty { cache.cursor })
                    SyncMutationResult.InvalidTab -> return SyncWriteOutcome.Rejected("invalid-tab")
                    SyncMutationResult.MissingTab -> return SyncWriteOutcome.Rejected("missing-tab")
                }
                val mutations = SyncOutboxRules.enqueue(cache.pendingMutations, mutation)
                val ids = mutations.mapTo(hashSetOf(), SyncPendingMutation::mutationId)
                val updated = cache.copy(pendingMutations = mutations, preparedWrites = cache.preparedWrites.filterKeys(ids::contains), preparedDeltas = cache.preparedDeltas.filterKeys(ids::contains))
                if (!cacheStore.save(updated)) return SyncWriteOutcome.Failed(retryable = true)
                cache = updated
            }
            publish(stateFrom(settings, SyncStatus.Syncing))
            var result: SyncWriteOutcome = SyncWriteOutcome.Failed(retryable = true)
            for (queued in cache.pendingMutations.toList()) {
                result = pushWithCasRetry(settings, secrets, queued)
                if (
                    queued.mutationId == pending.mutationId ||
                    result is SyncWriteOutcome.Conflict ||
                    result is SyncWriteOutcome.Failed
                ) break
            }
            publish(stateFrom(settings, if (result is SyncWriteOutcome.Synced) SyncStatus.Ready else state.status))
            result
        } catch (error: Throwable) {
            publish(stateFrom(settings, statusFor(error)))
            SyncWriteOutcome.Failed(retryable = error !is IllegalArgumentException)
        } finally { secrets.clear() }
    }

    private fun pushWithCasRetry(settings: SyncConnectionSettings, secrets: SyncVaultSecrets, mutation: SyncPendingMutation): SyncWriteOutcome {
        val transport = transportFactory(settings.endpoint)
        transport.discover()
        return if (transport.supportsTabMutationsV2()) pushDelta(transport, settings, secrets, mutation) else pushSnapshot(transport, settings, secrets, mutation)
    }

    private fun pushSnapshot(transport: SyncTransport, settings: SyncConnectionSettings, secrets: SyncVaultSecrets, mutation: SyncPendingMutation): SyncWriteOutcome {
        repeat(MAX_CAS_ATTEMPTS) {
            val profile = cache.profiles[mutation.targetDeviceId] ?: return rejectMissing(mutation)
            when (val applied = SyncTabRules.apply(profile, mutation)) {
                SyncMutationResult.AlreadyApplied -> return syncedAndRemove(mutation, profile, cache.cursor)
                SyncMutationResult.InvalidTab -> return rejectedAndRemove(mutation, "invalid-tab")
                SyncMutationResult.MissingTab -> return rejectedAndRemove(mutation, "missing-tab")
                is SyncMutationResult.Applied -> {
                    val encrypted = cache.preparedWrites[mutation.mutationId]?.takeIf { it.baseRevision == profile.revision && it.writerDeviceId == secrets.deviceId && it.targetDeviceId == mutation.targetDeviceId }
                        ?: prepareSnapshot(secrets, mutation, profile, applied.profile)
                    try {
                        val response = transport.putTabs(secrets.deviceToken, encrypted)
                        require(response.revision == encrypted.revision)
                        return syncedAndRemove(mutation, applied.profile.copy(revision = response.revision), cache.cursor)
                    } catch (error: SyncTransportException) {
                        if (error.statusCode != 409 || error.problemCode != "snapshot_conflict") throw error
                        dropPrepared(mutation.mutationId, delta = false); refreshRemote(settings, secrets)
                    }
                }
            }
        }
        return SyncWriteOutcome.Conflict(cache.profiles[mutation.targetDeviceId], retryable = true)
    }

    private fun pushDelta(transport: SyncTransport, settings: SyncConnectionSettings, secrets: SyncVaultSecrets, mutation: SyncPendingMutation): SyncWriteOutcome {
        repeat(MAX_CAS_ATTEMPTS) {
            val profile = cache.profiles[mutation.targetDeviceId] ?: return rejectMissing(mutation)
            when (val applied = SyncTabRules.apply(profile, mutation)) {
                SyncMutationResult.AlreadyApplied -> return syncedAndRemove(mutation, profile, cache.deltaCursor)
                SyncMutationResult.InvalidTab -> return rejectedAndRemove(mutation, "invalid-tab")
                SyncMutationResult.MissingTab -> return rejectedAndRemove(mutation, "missing-tab")
                is SyncMutationResult.Applied -> {
                    val encrypted = cache.preparedDeltas[mutation.mutationId]?.takeIf { it.baseRevision == profile.revision && it.writerDeviceId == secrets.deviceId && it.targetDeviceId == mutation.targetDeviceId && it.workspaceId == secrets.workspaceId }
                        ?: prepareDelta(secrets, mutation, profile)
                    try {
                        val response = transport.pushDelta(secrets.deviceToken, encrypted)
                        require(response.revision == profile.revision + 1)
                        return syncedAndRemove(mutation, applied.profile.copy(revision = response.revision), cache.deltaCursor)
                    } catch (error: SyncTransportException) {
                        val conflict = error.statusCode == 409 && error.problemCode in setOf("revision_conflict", "snapshot_conflict")
                        if (!conflict) throw error
                        dropPrepared(mutation.mutationId, delta = true); refreshRemote(settings, secrets)
                    }
                }
            }
        }
        return SyncWriteOutcome.Conflict(cache.profiles[mutation.targetDeviceId], retryable = true)
    }

    private fun prepareSnapshot(secrets: SyncVaultSecrets, mutation: SyncPendingMutation, profile: SyncProfile, applied: SyncProfile): SyncEncryptedChange {
        val metadata = SyncEncryptedChange(crypto.newChangeId(), secrets.deviceId, mutation.targetDeviceId, profile.revision, null, "", "")
        val encrypted = crypto.encryptTabSnapshot(secrets.workspaceKey, metadata, SyncTabSnapshot(crypto.nowIso8601(), applied.tabs)).copy(revision = profile.revision + 1)
        cache = cache.copy(preparedWrites = cache.preparedWrites + (mutation.mutationId to encrypted)); require(cacheStore.save(cache)); return encrypted
    }

    private fun prepareDelta(secrets: SyncVaultSecrets, mutation: SyncPendingMutation, profile: SyncProfile): SyncEncryptedDelta {
        val metadata = SyncEncryptedDelta(crypto.newChangeId(), mutation.mutationId, secrets.workspaceId, secrets.deviceId, mutation.targetDeviceId, profile.revision, null, "", "")
        val encrypted = crypto.encryptTabMutation(secrets.workspaceKey, metadata, mutation)
        cache = cache.copy(preparedDeltas = cache.preparedDeltas + (mutation.mutationId to encrypted)); require(cacheStore.save(cache)); return encrypted
    }

    private fun syncedAndRemove(mutation: SyncPendingMutation, profile: SyncProfile, cursor: String): SyncWriteOutcome {
        val updated = cache.copy(profiles = cache.profiles + (profile.deviceId to profile), pendingMutations = cache.pendingMutations.filterNot { it.mutationId == mutation.mutationId }, preparedWrites = cache.preparedWrites - mutation.mutationId, preparedDeltas = cache.preparedDeltas - mutation.mutationId)
        if (!cacheStore.save(updated)) return SyncWriteOutcome.Failed(retryable = true)
        cache = updated; return SyncWriteOutcome.Synced(profile, cursor)
    }

    private fun rejectedAndRemove(mutation: SyncPendingMutation, reason: String): SyncWriteOutcome {
        removePending(mutation.mutationId); return SyncWriteOutcome.Rejected(reason)
    }

    private fun rejectMissing(mutation: SyncPendingMutation): SyncWriteOutcome = rejectedAndRemove(mutation, "unknown-profile")

    private fun dropPrepared(mutationId: String, delta: Boolean) {
        val updated = if (delta) cache.copy(preparedDeltas = cache.preparedDeltas - mutationId) else cache.copy(preparedWrites = cache.preparedWrites - mutationId)
        require(cacheStore.save(updated)); cache = updated
    }

    private fun removePending(mutationId: String) {
        val updated = cache.copy(pendingMutations = cache.pendingMutations.filterNot { it.mutationId == mutationId }, preparedWrites = cache.preparedWrites - mutationId, preparedDeltas = cache.preparedDeltas - mutationId)
        if (cacheStore.save(updated)) cache = updated
    }

    private fun connectRealtimeNow() {
        if (!realtimeEnabled || realtimeConnection != null) return
        val settings = settingsStore.load() ?: return
        val secrets = vaultStore.load() ?: return
        try {
            val transport = transportFactory(settings.endpoint)
            transport.discover()
            if (!transport.supportsTabMutationsV2() || !transport.supportsRealtime()) return
            val generation = ++realtimeGeneration
            realtimeConnection = transport.connectRealtime(
                transport.requestRealtimeTicket(secrets.deviceToken),
                onEvent = { event -> dispatch.dispatch { handleRealtimeEvent(event) } },
                onClosed = { dispatch.dispatch { if (generation == realtimeGeneration) { realtimeConnection = null; scheduleRealtimeReconnect() } } },
            )
            realtimeAttempt = 0
        } catch (_: Throwable) { scheduleRealtimeReconnect() } finally { secrets.clear() }
    }

    private fun handleRealtimeEvent(event: SyncRealtimeEvent) {
        if (!realtimeEnabled) return
        val settings = settingsStore.load() ?: return
        val secrets = vaultStore.load() ?: return
        try {
            if (cache.deltaCursor.isEmpty() || !isNextOrDuplicateCursor(cache.deltaCursor, event.cursor)) { refreshRemote(settings, secrets); return }
            if (event.cursor == cache.deltaCursor) return
            val updated = applyDeltas(cache, listOf(event.change), cache.profiles.keys, secrets).copy(deltaCursor = event.cursor)
            require(cacheStore.save(updated)); cache = updated
            publish(stateFrom(settings, SyncStatus.Ready, crypto.nowIso8601()))
        } catch (_: Throwable) { runCatching { refreshRemote(settings, secrets) } } finally { secrets.clear() }
    }

    private fun scheduleRealtimeReconnect() {
        if (!realtimeEnabled) return
        val delay = REALTIME_RECONNECT_SECONDS[realtimeAttempt.coerceAtMost(REALTIME_RECONNECT_SECONDS.lastIndex)]
        realtimeAttempt++
        dispatch.dispatchAfter(delay) { if (realtimeEnabled) connectRealtimeNow() }
    }

    private fun closeRealtimeConnection() {
        realtimeGeneration++
        realtimeConnection?.close(); realtimeConnection = null; realtimeAttempt = 0
    }

    private fun isNextOrDuplicateCursor(current: String, incoming: String): Boolean {
        if (current.isEmpty()) return true
        val old = current.cursorParts() ?: return false
        val next = incoming.cursorParts() ?: return false
        return old.first == next.first && next.second in old.second..(old.second + 1)
    }

    private fun String.cursorParts(): Pair<String, Long>? {
        val at = lastIndexOf('.')
        if (at <= 0 || at == lastIndex) return null
        return substring(at + 1).toLongOrNull()?.let { substring(0, at) to it }
    }

    private fun optimisticProfiles(): Map<String, SyncProfile> {
        var profiles = cache.profiles
        cache.pendingMutations.forEach { mutation ->
            val profile = profiles[mutation.targetDeviceId] ?: return@forEach
            val result = SyncTabRules.apply(profile, mutation) as? SyncMutationResult.Applied ?: return@forEach
            profiles += mutation.targetDeviceId to result.profile
        }
        return profiles
    }

    private fun stateFrom(settings: SyncConnectionSettings?, status: SyncStatus, lastSuccessAt: String? = this.lastSuccessAt): SyncRepositoryState = SyncRepositoryState(
        settings = settings,
        status = status,
        profiles = optimisticProfiles().values.sortedBy(SyncProfile::displayName),
        pendingCount = cache.pendingMutations.size,
        lastCursor = cache.deltaCursor.ifEmpty { cache.cursor }.ifEmpty { null },
        lastSuccessAt = lastSuccessAt,
        currentDeviceId = currentDeviceId,
    )

    private fun hasVault(): Boolean = vaultStore.load()?.let { secrets -> secrets.clear(); true } ?: false

    private fun loadCurrentDeviceId(): String? = vaultStore.load()?.let { secrets -> try { secrets.deviceId } finally { secrets.clear() } }

    private fun publish(value: SyncRepositoryState) {
        lastSuccessAt = value.lastSuccessAt
        state = value
        listeners.toList().forEach { listener -> runCatching { listener(value) } }
    }

    private fun statusFor(error: Throwable): SyncStatus = when {
        error is SyncTransportException && error.statusCode == 401 -> SyncStatus.AuthError
        error is SyncTransportException && error.statusCode == null -> SyncStatus.Offline
        error is IllegalArgumentException -> SyncStatus.Incompatible
        else -> SyncStatus.Offline
    }

    private companion object {
        const val MIN_PASSPHRASE_CHARS = 16
        const val MAX_PENDING_MUTATIONS = 1_000
        const val MAX_CAS_ATTEMPTS = 3
        const val MAX_PULL_PAGES = 10_000
        val REALTIME_RECONNECT_SECONDS = longArrayOf(1, 2, 5, 10, 30, 60)
    }

    private class WrongPassphraseException : Exception()
}
