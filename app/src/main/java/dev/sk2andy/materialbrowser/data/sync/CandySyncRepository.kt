package dev.sk2andy.materialbrowser.data.sync

import dev.sk2andy.materialbrowser.sync.AndroidArgon2RecoveryKeyDeriver
import dev.sk2andy.materialbrowser.sync.SyncBase64
import dev.sk2andy.materialbrowser.sync.SyncCache
import dev.sk2andy.materialbrowser.sync.SyncConnectionRules
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncCrypto
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDescriptor
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconRules
import dev.sk2andy.materialbrowser.sync.SyncDeviceStatus
import dev.sk2andy.materialbrowser.sync.SyncEncryptedChange
import dev.sk2andy.materialbrowser.sync.SyncEncryptedDelta
import dev.sk2andy.materialbrowser.sync.SyncEnrollmentOutcome
import dev.sk2andy.materialbrowser.sync.SyncMutationResult
import dev.sk2andy.materialbrowser.sync.SyncOutboxRules
import dev.sk2andy.materialbrowser.sync.SyncPendingMutation
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncRecoveryKeyDeriver
import dev.sk2andy.materialbrowser.sync.SyncRealtimeEvent
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState
import dev.sk2andy.materialbrowser.sync.SyncStatus
import dev.sk2andy.materialbrowser.sync.SyncTabRules
import dev.sk2andy.materialbrowser.sync.SyncTabSnapshot
import dev.sk2andy.materialbrowser.sync.SyncVaultSecrets
import dev.sk2andy.materialbrowser.sync.SyncWriteOutcome
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class CandySyncRepository(
    private val settingsStore: SyncSettingsStore,
    private val vaultStore: SyncVaultStore,
    private val cacheStore: SyncCacheStore,
    private val iconCatalog: SyncDeviceIconCatalog,
    private val transportFactory: (String) -> SyncTransport = ::SyncHttpClient,
    private val crypto: SyncCrypto = SyncCrypto(),
    private val recoveryKeyDeriver: SyncRecoveryKeyDeriver = AndroidArgon2RecoveryKeyDeriver(),
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "candy-sync").apply { isDaemon = true }
    },
    private val realtimeScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "candy-sync-realtime").apply { isDaemon = true }
        },
) : AutoCloseable {
    private val listeners = CopyOnWriteArraySet<(SyncRepositoryState) -> Unit>()
    private var cache = cacheStore.load()
    private var currentDeviceId = loadCurrentDeviceId()
    private var configurationWriteFailed = false
    private var realtimeConnection: AutoCloseable? = null
    private var realtimeReconnectAttempt = 0
    private var realtimeGeneration = 0L

    @Volatile
    private var realtimeEnabled = false

    @Volatile
    private var state = stateFrom(
        settings = settingsStore.load(),
        status = when {
            settingsStore.load() == null -> SyncStatus.Unconfigured
            !hasVault() -> SyncStatus.Unconfigured
            else -> SyncStatus.Ready
        },
    )

    fun currentState(): SyncRepositoryState = state

    fun observe(listener: (SyncRepositoryState) -> Unit): AutoCloseable {
        listeners += listener
        listener(state)
        return AutoCloseable { listeners -= listener }
    }

    fun configure(settings: SyncConnectionSettings): Boolean {
        val normalized = SyncConnectionRules.normalize(settings, iconCatalog) ?: return false
        submit { configureNow(normalized) }
        return true
    }

    private fun configureNow(normalized: SyncConnectionSettings): Boolean {
        val previous = settingsStore.load()
        if (!settingsStore.save(normalized)) {
            configurationWriteFailed = true
            return false
        }
        configurationWriteFailed = false
        if (previous != null && (previous.endpoint != normalized.endpoint || previous.username != normalized.username)) {
            closeRealtimeConnection()
            vaultStore.clear()
            cacheStore.clear()
            cache = SyncCache(cursor = "", profiles = emptyMap())
            currentDeviceId = null
        }
        publish(stateFrom(normalized, if (!hasVault()) SyncStatus.Unconfigured else SyncStatus.Ready))
        if (realtimeEnabled) connectRealtimeNow()
        return true
    }

    fun enroll(
        serverPassword: CharArray,
        passphrase: CharArray,
    ): CompletableFuture<SyncEnrollmentOutcome> = submit {
        enrollNow(serverPassword, passphrase)
    }

    fun refresh(): CompletableFuture<Boolean> = submit {
        val settings = settingsStore.load() ?: return@submit false
        val secrets = vaultStore.load() ?: return@submit false
        publish(stateFrom(settings, SyncStatus.Syncing))
        try {
            refreshNow(settings, secrets)
            val pending = cache.pendingMutations.toList()
            pending.forEach { mutation ->
                when (pushWithCasRetry(settings, secrets, mutation)) {
                    is SyncWriteOutcome.Synced,
                    is SyncWriteOutcome.Rejected,
                    -> Unit
                    is SyncWriteOutcome.Conflict,
                    is SyncWriteOutcome.Failed,
                    -> throw SyncTransportException(null, "pending_write_failed")
                }
            }
            publish(stateFrom(settings, SyncStatus.Ready, Instant.now(clock).toString()))
            true
        } catch (error: Exception) {
            publish(stateFrom(settings, statusFor(error)))
            false
        } finally {
            secrets.clear()
        }
    }

    fun mutate(mutation: SyncPendingMutation): CompletableFuture<SyncWriteOutcome> = submit {
        mutateNow(mutation)
    }

    fun startRealtime() {
        realtimeEnabled = true
        submit { connectRealtimeNow() }
    }

    fun stopRealtime() {
        realtimeEnabled = false
        submit { closeRealtimeConnection() }
    }

    override fun close() {
        realtimeEnabled = false
        realtimeConnection?.close()
        realtimeConnection = null
        realtimeScheduler.shutdownNow()
        executor.shutdownNow()
        listeners.clear()
    }

    private fun enrollNow(
        serverPassword: CharArray,
        passphrase: CharArray,
    ): SyncEnrollmentOutcome {
        if (
            serverPassword.isEmpty() ||
            passphrase.size < MIN_PASSPHRASE_CHARS ||
            serverPassword.contentEquals(passphrase)
        ) {
            serverPassword.fill('\u0000')
            passphrase.fill('\u0000')
            return SyncEnrollmentOutcome.InvalidConfiguration
        }
        if (configurationWriteFailed) {
            serverPassword.fill('\u0000')
            passphrase.fill('\u0000')
            return SyncEnrollmentOutcome.InvalidConfiguration
        }
        val settings = settingsStore.load()
            ?: return SyncEnrollmentOutcome.InvalidConfiguration.also {
                serverPassword.fill('\u0000')
                passphrase.fill('\u0000')
            }
        publish(stateFrom(settings, SyncStatus.Enrolling))
        val passwordBytes = serverPassword.toUtf8Bytes()
        val passphraseBytes = passphrase.toUtf8Bytes()
        serverPassword.fill('\u0000')
        passphrase.fill('\u0000')
        var recoveryKey: ByteArray? = null
        var workspaceKey: ByteArray? = null
        var identityPrivate: ByteArray? = null
        return try {
            require(passwordBytes.isNotEmpty() && passphraseBytes.isNotEmpty())
            val transport = transportFactory(settings.endpoint)
            transport.discover()
            val bootstrap = transport.bootstrap(settings.username, passwordBytes)
            recoveryKey = recoveryKeyDeriver.derive(passphraseBytes, bootstrap.kdf)
            workspaceKey = if (bootstrap.initialized) {
                crypto.unlockRecoveryEnvelope(
                    recoveryKey,
                    requireNotNull(bootstrap.recoveryEnvelope),
                    bootstrap.workspaceId,
                )
            } else {
                ByteArray(32).also(random::nextBytes)
            }
            val identity = crypto.generateDeviceIdentity()
            identityPrivate = identity.privateKeyPkcs8
            val icon = SyncDeviceIconDescriptor(settings.iconCatalogId, settings.iconAccentHue)
            require(iconCatalog.contains(icon.catalogId))
            val encryptedName = crypto.encryptDeviceName(
                workspaceKey,
                bootstrap.workspaceId,
                identity.fingerprint,
                settings.deviceName,
            )
            val encryptedIcon = crypto.encryptDeviceIcon(
                workspaceKey,
                bootstrap.workspaceId,
                identity.fingerprint,
                icon,
            )
            val recoveryEnvelope = if (bootstrap.initialized) null else crypto.createRecoveryEnvelope(
                recoveryKey,
                workspaceKey,
                bootstrap.workspaceId,
            )
            val enrollment = transport.enroll(
                settings.username,
                passwordBytes,
                identity,
                encryptedName,
                encryptedIcon,
                recoveryEnvelope,
            )
            require(enrollment.workspaceId == bootstrap.workspaceId)
            val secrets = SyncVaultSecrets(
                workspaceId = bootstrap.workspaceId,
                deviceId = enrollment.deviceId,
                deviceToken = enrollment.token,
                workspaceKey = workspaceKey.copyOf(),
                devicePrivateKeyPkcs8 = identity.privateKeyPkcs8.copyOf(),
            )
            try {
                require(vaultStore.save(secrets))
            } finally {
                secrets.clear()
            }
            val enrolledAt = Instant.now(clock).toString()
            val enrolledProfile = SyncProfile(
                deviceId = enrollment.deviceId,
                displayName = settings.deviceName,
                icon = icon,
                revision = 0,
                tabs = emptyList(),
                lastSeenAt = enrolledAt,
            )
            val enrolledCache = SyncCache(
                cursor = enrollment.cursor,
                profiles = mapOf(enrollment.deviceId to enrolledProfile),
            )
            require(cacheStore.save(enrolledCache))
            cache = enrolledCache
            currentDeviceId = enrollment.deviceId
            publish(stateFrom(settings, SyncStatus.Ready, enrolledAt))
            if (realtimeEnabled) connectRealtimeNow()
            SyncEnrollmentOutcome.Enrolled
        } catch (error: Exception) {
            val outcome = when {
                error is SyncTransportException && error.statusCode == 401 -> SyncEnrollmentOutcome.AuthenticationFailed
                error is javax.crypto.AEADBadTagException -> SyncEnrollmentOutcome.WrongPassphrase
                error is IllegalArgumentException -> SyncEnrollmentOutcome.IncompatibleServer
                else -> SyncEnrollmentOutcome.Failed
            }
            publish(stateFrom(settings, statusFor(error)))
            outcome
        } finally {
            passwordBytes.fill(0)
            passphraseBytes.fill(0)
            recoveryKey?.fill(0)
            workspaceKey?.fill(0)
            identityPrivate?.fill(0)
        }
    }

    private fun refreshNow(settings: SyncConnectionSettings, secrets: SyncVaultSecrets) {
        val transport = transportFactory(settings.endpoint)
        transport.discover()
        val devices = transport.listDevices(secrets.deviceToken).filter { it.status == SyncDeviceStatus.Active }
        val metadata = devices.associate { device ->
            val publicKey = SyncBase64.decode(device.publicKey, expectedBytes = 91)
            val fingerprint = crypto.fingerprint(publicKey)
            val name = crypto.decryptDeviceName(
                secrets.workspaceKey,
                secrets.workspaceId,
                fingerprint,
                device.encryptedName,
            )
            val icon = device.encryptedIcon?.let {
                crypto.decryptDeviceIcon(secrets.workspaceKey, secrets.workspaceId, fingerprint, it)
            } ?: SyncDeviceIconRules.defaultForAndroid(fingerprint).copy(catalogId = "browser")
            require(iconCatalog.contains(icon.catalogId))
            device.deviceId to Triple(name, icon, device)
        }
        var working = cache.copy(
            profiles = metadata.mapValues { (deviceId, values) ->
                val existing = cache.profiles[deviceId]
                SyncProfile(
                    deviceId = deviceId,
                    displayName = values.first,
                    icon = values.second,
                    revision = existing?.revision ?: 0,
                    tabs = existing?.tabs.orEmpty(),
                    lastSeenAt = values.third.lastSeenAt,
                )
            },
        )
        try {
            working = pullAll(transport, secrets.deviceToken, working, metadata.keys, secrets.workspaceKey)
        } catch (error: SyncTransportException) {
            if (error.statusCode != 410 || error.problemCode != "cursor_reset") throw error
            val reset = working.copy(cursor = "", profiles = working.profiles.mapValues { (_, profile) ->
                    profile.copy(revision = 0, tabs = emptyList())
                })
            working = try {
                val snapshot = transport.snapshot(secrets.deviceToken)
                applyChanges(reset, snapshot.changes, metadata.keys, secrets.workspaceKey)
                    .copy(cursor = snapshot.cursor)
            } catch (snapshotError: SyncTransportException) {
                if (snapshotError.statusCode != 413 || snapshotError.problemCode != "snapshot_too_large") {
                    throw snapshotError
                }
                pullAll(transport, secrets.deviceToken, reset, metadata.keys, secrets.workspaceKey)
            }
        }
        if (transport.supportsTabMutationsV2()) {
            if (working.profiles.values.any { profile ->
                    profile.deviceId != secrets.deviceId && profile.revision == 0L
                }
            ) {
                val snapshot = transport.snapshot(secrets.deviceToken)
                working = applyChanges(
                    initial = working,
                    changes = snapshot.changes,
                    knownDeviceIds = metadata.keys,
                    workspaceKey = secrets.workspaceKey,
                ).copy(cursor = snapshot.cursor)
            }
            working = try {
                pullAllDeltas(
                    transport = transport,
                    token = secrets.deviceToken,
                    initial = working,
                    knownDeviceIds = metadata.keys,
                    secrets = secrets,
                )
            } catch (error: SyncTransportException) {
                if (error.statusCode != 410 || error.problemCode != "cursor_reset") throw error
                pullAllDeltas(
                    transport = transport,
                    token = secrets.deviceToken,
                    initial = working.copy(deltaCursor = ""),
                    knownDeviceIds = metadata.keys,
                    secrets = secrets,
                )
            }
        }
        require(cacheStore.save(working))
        transport.acknowledge(secrets.deviceToken, working.cursor)
        cache = working
    }

    private fun applyChanges(
        initial: SyncCache,
        changes: List<SyncEncryptedChange>,
        knownDeviceIds: Set<String>,
        workspaceKey: ByteArray,
    ): SyncCache {
        var profiles = initial.profiles
        changes.sortedWith(compareBy({ it.targetDeviceId }, { it.revision ?: Long.MAX_VALUE })).forEach { change ->
            val revision = requireNotNull(change.revision)
            if (change.targetDeviceId !in knownDeviceIds) return@forEach
            val current = profiles[change.targetDeviceId] ?: return@forEach
            if (revision <= current.revision) return@forEach
            val snapshot = crypto.decryptTabSnapshot(workspaceKey, change)
            profiles = profiles + (change.targetDeviceId to current.copy(revision = revision, tabs = snapshot.tabs))
        }
        return initial.copy(profiles = profiles)
    }

    private fun pullAll(
        transport: SyncTransport,
        token: String,
        initial: SyncCache,
        knownDeviceIds: Set<String>,
        workspaceKey: ByteArray,
    ): SyncCache {
        var working = initial
        val seenCursors = mutableSetOf(working.cursor)
        repeat(MAX_PULL_PAGES) {
            val page = transport.pull(token, working.cursor)
            require(page.nextCursor != working.cursor || !page.hasMore)
            require(seenCursors.add(page.nextCursor) || !page.hasMore)
            working = applyChanges(working, page.changes, knownDeviceIds, workspaceKey)
                .copy(cursor = page.nextCursor)
            if (!page.hasMore) return working
        }
        throw IllegalArgumentException("Too many sync pages")
    }

    private fun pullAllDeltas(
        transport: SyncTransport,
        token: String,
        initial: SyncCache,
        knownDeviceIds: Set<String>,
        secrets: SyncVaultSecrets,
    ): SyncCache {
        var working = initial
        val seenCursors = mutableSetOf(working.deltaCursor)
        repeat(MAX_PULL_PAGES) {
            val page = transport.pullDeltas(token, working.deltaCursor)
            require(page.nextCursor != working.deltaCursor || !page.hasMore)
            require(seenCursors.add(page.nextCursor) || !page.hasMore)
            working = applyDeltas(
                initial = working,
                changes = page.changes,
                knownDeviceIds = knownDeviceIds,
                secrets = secrets,
            ).copy(deltaCursor = page.nextCursor)
            if (!page.hasMore) return working
        }
        throw IllegalArgumentException("Too many delta pages")
    }

    private fun applyDeltas(
        initial: SyncCache,
        changes: List<SyncEncryptedDelta>,
        knownDeviceIds: Set<String>,
        secrets: SyncVaultSecrets,
    ): SyncCache {
        var profiles = initial.profiles
        changes.forEach { change ->
            require(change.workspaceId == secrets.workspaceId)
            if (change.targetDeviceId !in knownDeviceIds) return@forEach
            val current = profiles[change.targetDeviceId] ?: return@forEach
            val revision = requireNotNull(change.revision)
            if (revision <= current.revision) return@forEach
            require(revision == current.revision + 1) { "Delta revision gap" }
            val mutation = crypto.decryptTabMutation(secrets.workspaceKey, change)
            val updated = when (val applied = SyncTabRules.apply(current, mutation)) {
                is SyncMutationResult.Applied -> applied.profile
                SyncMutationResult.AlreadyApplied,
                SyncMutationResult.MissingTab,
                -> current
                SyncMutationResult.InvalidTab -> throw IllegalArgumentException("Invalid encrypted mutation")
            }
            profiles = profiles + (change.targetDeviceId to updated.copy(revision = revision))
        }
        return initial.copy(profiles = profiles)
    }

    private fun mutateNow(mutation: SyncPendingMutation): SyncWriteOutcome {
        val settings = settingsStore.load() ?: return SyncWriteOutcome.Rejected("unconfigured")
        val secrets = vaultStore.load() ?: return SyncWriteOutcome.Rejected("unenrolled")
        return try {
            val queued = cache.pendingMutations.firstOrNull { it.mutationId == mutation.mutationId }
            if (queued != null && queued != mutation) return SyncWriteOutcome.Rejected("duplicate-mutation-id")
            val canonicalMutation = queued ?: mutation
            if (queued == null) {
                if (cache.pendingMutations.size >= MAX_PENDING_MUTATIONS) {
                    return SyncWriteOutcome.Rejected("outbox-full")
                }
                val profile = optimisticProfiles()[mutation.targetDeviceId]
                    ?: return SyncWriteOutcome.Rejected("unknown-profile")
                if (SyncTabRules.apply(profile, mutation) !is SyncMutationResult.Applied) {
                    return when (SyncTabRules.apply(profile, mutation)) {
                        SyncMutationResult.AlreadyApplied -> SyncWriteOutcome.Synced(profile, cache.cursor)
                        SyncMutationResult.InvalidTab -> SyncWriteOutcome.Rejected("invalid-tab")
                        SyncMutationResult.MissingTab -> SyncWriteOutcome.Rejected("missing-tab")
                        is SyncMutationResult.Applied -> error("Handled above")
                    }
                }
                val pendingMutations = SyncOutboxRules.enqueue(cache.pendingMutations, mutation)
                val retainedIds = pendingMutations.mapTo(hashSetOf(), SyncPendingMutation::mutationId)
                val queuedCache = cache.copy(
                    pendingMutations = pendingMutations,
                    preparedWrites = cache.preparedWrites.filterKeys(retainedIds::contains),
                    preparedDeltas = cache.preparedDeltas.filterKeys(retainedIds::contains),
                )
                if (!cacheStore.save(queuedCache)) return SyncWriteOutcome.Failed(retryable = true)
                cache = queuedCache
            }
            publish(stateFrom(settings, SyncStatus.Syncing))
            var outcome: SyncWriteOutcome = SyncWriteOutcome.Failed(retryable = true)
            for (pending in cache.pendingMutations.toList()) {
                outcome = pushWithCasRetry(settings, secrets, pending)
                if (pending.mutationId == canonicalMutation.mutationId) break
                if (outcome is SyncWriteOutcome.Conflict || outcome is SyncWriteOutcome.Failed) break
            }
            publish(stateFrom(settings, if (outcome is SyncWriteOutcome.Synced) SyncStatus.Ready else state.status))
            outcome
        } catch (error: Exception) {
            publish(stateFrom(settings, statusFor(error)))
            SyncWriteOutcome.Failed(retryable = error !is IllegalArgumentException)
        } finally {
            secrets.clear()
        }
    }

    private fun pushWithCasRetry(
        settings: SyncConnectionSettings,
        secrets: SyncVaultSecrets,
        mutation: SyncPendingMutation,
    ): SyncWriteOutcome {
        val transport = transportFactory(settings.endpoint)
        transport.discover()
        if (transport.supportsTabMutationsV2()) {
            return pushDeltaWithCasRetry(transport, settings, secrets, mutation)
        }
        repeat(MAX_CAS_ATTEMPTS) { attempt ->
            val profile = cache.profiles[mutation.targetDeviceId]
                ?: return SyncWriteOutcome.Rejected("unknown-profile").also { removePending(mutation.mutationId) }
            when (val applied = SyncTabRules.apply(profile, mutation)) {
                SyncMutationResult.AlreadyApplied -> {
                    removePending(mutation.mutationId)
                    return SyncWriteOutcome.Synced(profile, cache.cursor)
                }
                SyncMutationResult.InvalidTab -> {
                    removePending(mutation.mutationId)
                    return SyncWriteOutcome.Rejected("invalid-tab")
                }
                SyncMutationResult.MissingTab -> {
                    removePending(mutation.mutationId)
                    return SyncWriteOutcome.Rejected("missing-tab")
                }
                is SyncMutationResult.Applied -> {
                    val existing = cache.preparedWrites[mutation.mutationId]
                        ?.takeIf { prepared ->
                            prepared.baseRevision == profile.revision &&
                                prepared.writerDeviceId == secrets.deviceId &&
                                prepared.targetDeviceId == mutation.targetDeviceId
                        }
                    val encrypted = existing ?: prepareWrite(
                        secrets = secrets,
                        mutation = mutation,
                        profile = profile,
                        applied = applied.profile,
                    )
                    try {
                        val response = transport.putTabs(secrets.deviceToken, encrypted)
                        require(response.revision == encrypted.revision)
                        val synced = applied.profile.copy(revision = response.revision)
                        val updated = cache.copy(
                            profiles = cache.profiles + (synced.deviceId to synced),
                            pendingMutations = cache.pendingMutations.filterNot { it.mutationId == mutation.mutationId },
                            preparedWrites = cache.preparedWrites - mutation.mutationId,
                        )
                        if (!cacheStore.save(updated)) return SyncWriteOutcome.Failed(retryable = true)
                        cache = updated
                        return SyncWriteOutcome.Synced(synced, cache.cursor)
                    } catch (error: SyncTransportException) {
                        if (error.statusCode != 409 || error.problemCode != "snapshot_conflict") throw error
                        val withoutAttempt = cache.copy(preparedWrites = cache.preparedWrites - mutation.mutationId)
                        require(cacheStore.save(withoutAttempt))
                        cache = withoutAttempt
                        refreshNow(settings, secrets)
                        if (attempt == MAX_CAS_ATTEMPTS - 1) {
                            return SyncWriteOutcome.Conflict(cache.profiles[mutation.targetDeviceId], retryable = true)
                        }
                    }
                }
            }
        }
        return SyncWriteOutcome.Conflict(cache.profiles[mutation.targetDeviceId], retryable = true)
    }

    private fun pushDeltaWithCasRetry(
        transport: SyncTransport,
        settings: SyncConnectionSettings,
        secrets: SyncVaultSecrets,
        mutation: SyncPendingMutation,
    ): SyncWriteOutcome {
        repeat(MAX_CAS_ATTEMPTS) { attempt ->
            val profile = cache.profiles[mutation.targetDeviceId]
                ?: return SyncWriteOutcome.Rejected("unknown-profile").also { removePending(mutation.mutationId) }
            when (val applied = SyncTabRules.apply(profile, mutation)) {
                SyncMutationResult.AlreadyApplied -> {
                    removePending(mutation.mutationId)
                    return SyncWriteOutcome.Synced(profile, cache.deltaCursor)
                }
                SyncMutationResult.InvalidTab -> {
                    removePending(mutation.mutationId)
                    return SyncWriteOutcome.Rejected("invalid-tab")
                }
                SyncMutationResult.MissingTab -> {
                    removePending(mutation.mutationId)
                    return SyncWriteOutcome.Rejected("missing-tab")
                }
                is SyncMutationResult.Applied -> {
                    val existing = cache.preparedDeltas[mutation.mutationId]
                        ?.takeIf { prepared ->
                            prepared.baseRevision == profile.revision &&
                                prepared.writerDeviceId == secrets.deviceId &&
                                prepared.targetDeviceId == mutation.targetDeviceId &&
                                prepared.workspaceId == secrets.workspaceId
                        }
                    val encrypted = existing ?: prepareDelta(secrets, mutation, profile)
                    try {
                        val response = transport.pushDelta(secrets.deviceToken, encrypted)
                        require(response.revision == profile.revision + 1)
                        val synced = applied.profile.copy(revision = response.revision)
                        val updated = cache.copy(
                            profiles = cache.profiles + (synced.deviceId to synced),
                            pendingMutations = cache.pendingMutations.filterNot {
                                it.mutationId == mutation.mutationId
                            },
                            preparedDeltas = cache.preparedDeltas - mutation.mutationId,
                        )
                        if (!cacheStore.save(updated)) return SyncWriteOutcome.Failed(retryable = true)
                        cache = updated
                        return SyncWriteOutcome.Synced(synced, cache.deltaCursor)
                    } catch (error: SyncTransportException) {
                        val isConflict = error.statusCode == 409 &&
                            error.problemCode in setOf("revision_conflict", "snapshot_conflict")
                        if (!isConflict) throw error
                        val withoutAttempt = cache.copy(
                            preparedDeltas = cache.preparedDeltas - mutation.mutationId,
                        )
                        require(cacheStore.save(withoutAttempt))
                        cache = withoutAttempt
                        refreshNow(settings, secrets)
                        if (attempt == MAX_CAS_ATTEMPTS - 1) {
                            return SyncWriteOutcome.Conflict(
                                cache.profiles[mutation.targetDeviceId],
                                retryable = true,
                            )
                        }
                    }
                }
            }
        }
        return SyncWriteOutcome.Conflict(cache.profiles[mutation.targetDeviceId], retryable = true)
    }

    private fun removePending(mutationId: String) {
        val updated = cache.copy(
            pendingMutations = cache.pendingMutations.filterNot { it.mutationId == mutationId },
            preparedWrites = cache.preparedWrites - mutationId,
            preparedDeltas = cache.preparedDeltas - mutationId,
        )
        if (cacheStore.save(updated)) cache = updated
    }

    private fun stateFrom(
        settings: SyncConnectionSettings?,
        status: SyncStatus,
        lastSuccessAt: String? = stateOrNull()?.lastSuccessAt,
    ): SyncRepositoryState = SyncRepositoryState(
        settings = settings,
        status = status,
        profiles = optimisticProfiles().values
            .sortedBy(SyncProfile::displayName),
        pendingCount = cache.pendingMutations.size,
        lastCursor = cache.deltaCursor.takeIf(String::isNotEmpty)
            ?: cache.cursor.takeIf(String::isNotEmpty),
        lastSuccessAt = lastSuccessAt,
        currentDeviceId = currentDeviceId,
    )

    private fun stateOrNull(): SyncRepositoryState? = runCatching { state }.getOrNull()

    private fun hasVault(): Boolean = vaultStore.load()?.let { secrets ->
        secrets.clear()
        true
    } ?: false

    private fun loadCurrentDeviceId(): String? = vaultStore.load()?.let { secrets ->
        try {
            secrets.deviceId
        } finally {
            secrets.clear()
        }
    }

    private fun optimisticProfiles(): Map<String, SyncProfile> {
        var profiles = cache.profiles
        cache.pendingMutations.forEach { mutation ->
            val current = profiles[mutation.targetDeviceId] ?: return@forEach
            val applied = SyncTabRules.apply(current, mutation) as? SyncMutationResult.Applied ?: return@forEach
            profiles = profiles + (mutation.targetDeviceId to applied.profile)
        }
        return profiles
    }

    private fun prepareWrite(
        secrets: SyncVaultSecrets,
        mutation: SyncPendingMutation,
        profile: SyncProfile,
        applied: SyncProfile,
    ): SyncEncryptedChange {
        val metadata = SyncEncryptedChange(
            changeId = UUID.randomUUID().toString(),
            writerDeviceId = secrets.deviceId,
            targetDeviceId = mutation.targetDeviceId,
            baseRevision = profile.revision,
            revision = null,
            nonce = "",
            ciphertext = "",
        )
        val encrypted = crypto.encryptTabSnapshot(
            secrets.workspaceKey,
            metadata,
            SyncTabSnapshot(Instant.now(clock).toString(), applied.tabs),
        ).copy(revision = profile.revision + 1)
        val prepared = cache.copy(preparedWrites = cache.preparedWrites + (mutation.mutationId to encrypted))
        require(cacheStore.save(prepared))
        cache = prepared
        return encrypted
    }

    private fun prepareDelta(
        secrets: SyncVaultSecrets,
        mutation: SyncPendingMutation,
        profile: SyncProfile,
    ): SyncEncryptedDelta {
        val metadata = SyncEncryptedDelta(
            changeId = UUID.randomUUID().toString(),
            mutationId = mutation.mutationId,
            workspaceId = secrets.workspaceId,
            writerDeviceId = secrets.deviceId,
            targetDeviceId = mutation.targetDeviceId,
            baseRevision = profile.revision,
            revision = null,
            nonce = "",
            ciphertext = "",
        )
        val encrypted = crypto.encryptTabMutation(secrets.workspaceKey, metadata, mutation)
        val prepared = cache.copy(
            preparedDeltas = cache.preparedDeltas + (mutation.mutationId to encrypted),
        )
        require(cacheStore.save(prepared))
        cache = prepared
        return encrypted
    }

    private fun connectRealtimeNow() {
        if (!realtimeEnabled || realtimeConnection != null) return
        val settings = settingsStore.load() ?: return
        val secrets = vaultStore.load() ?: return
        try {
            val transport = transportFactory(settings.endpoint)
            transport.discover()
            if (!transport.supportsTabMutationsV2() || !transport.supportsRealtime()) return
            val ticket = transport.requestRealtimeTicket(secrets.deviceToken)
            val generation = ++realtimeGeneration
            realtimeConnection = transport.connectRealtime(
                ticket = ticket,
                onEvent = { event -> submit { handleRealtimeEvent(event) } },
                onClosed = {
                    submit {
                        if (generation != realtimeGeneration) return@submit
                        realtimeConnection = null
                        scheduleRealtimeReconnect()
                    }
                },
            )
            realtimeReconnectAttempt = 0
        } catch (_: Exception) {
            scheduleRealtimeReconnect()
        } finally {
            secrets.clear()
        }
    }

    private fun handleRealtimeEvent(event: SyncRealtimeEvent) {
        if (!realtimeEnabled) return
        val settings = settingsStore.load() ?: return
        val secrets = vaultStore.load() ?: return
        try {
            if (cache.deltaCursor.isEmpty()) {
                refreshNow(settings, secrets)
                return
            }
            if (!isNextOrDuplicateCursor(cache.deltaCursor, event.cursor)) {
                refreshNow(settings, secrets)
                return
            }
            if (event.cursor == cache.deltaCursor) return
            val updated = applyDeltas(
                initial = cache,
                changes = listOf(event.change),
                knownDeviceIds = cache.profiles.keys,
                secrets = secrets,
            ).copy(deltaCursor = event.cursor)
            require(cacheStore.save(updated))
            cache = updated
            publish(stateFrom(settings, SyncStatus.Ready, Instant.now(clock).toString()))
        } catch (_: Exception) {
            runCatching { refreshNow(settings, secrets) }
        } finally {
            secrets.clear()
        }
    }

    private fun scheduleRealtimeReconnect() {
        if (!realtimeEnabled || realtimeScheduler.isShutdown) return
        val index = realtimeReconnectAttempt.coerceAtMost(REALTIME_RECONNECT_SECONDS.lastIndex)
        realtimeReconnectAttempt++
        realtimeScheduler.schedule(
            { if (realtimeEnabled) submit { connectRealtimeNow() } },
            REALTIME_RECONNECT_SECONDS[index],
            TimeUnit.SECONDS,
        )
    }

    private fun closeRealtimeConnection() {
        realtimeGeneration++
        realtimeConnection?.close()
        realtimeConnection = null
        realtimeReconnectAttempt = 0
    }

    private fun isNextOrDuplicateCursor(current: String, incoming: String): Boolean {
        if (current.isEmpty()) return true
        val currentParts = current.splitCursor() ?: return false
        val incomingParts = incoming.splitCursor() ?: return false
        return currentParts.first == incomingParts.first &&
            incomingParts.second in currentParts.second..(currentParts.second + 1)
    }

    private fun String.splitCursor(): Pair<String, Long>? {
        val separator = lastIndexOf('.')
        if (separator <= 0 || separator == lastIndex) return null
        val sequence = substring(separator + 1).toLongOrNull() ?: return null
        return substring(0, separator) to sequence
    }

    private fun publish(value: SyncRepositoryState) {
        state = value
        listeners.forEach { listener -> runCatching { listener(value) } }
    }

    private fun statusFor(error: Exception): SyncStatus = when {
        error is SyncTransportException && error.statusCode == 401 -> SyncStatus.AuthError
        error is SyncTransportException && error.statusCode == null -> SyncStatus.Offline
        error is java.security.GeneralSecurityException -> SyncStatus.CryptoError
        error is IllegalArgumentException -> SyncStatus.Incompatible
        else -> SyncStatus.Offline
    }

    private fun <T> submit(block: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync(block, executor)

    private fun CharArray.toUtf8Bytes(): ByteArray {
        val encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(this))
        return ByteArray(encoded.remaining()).also(encoded::get).also { encoded.clearBytes() }
    }

    private fun ByteBuffer.clearBytes() {
        clear()
        while (hasRemaining()) put(0)
        clear()
    }

    private companion object {
        const val MAX_CAS_ATTEMPTS = 3
        const val MAX_PULL_PAGES = 10_000
        const val MAX_PENDING_MUTATIONS = 1_000
        const val MIN_PASSPHRASE_CHARS = 16
        val REALTIME_RECONNECT_SECONDS = longArrayOf(1, 2, 5, 10, 30, 60)
    }
}
