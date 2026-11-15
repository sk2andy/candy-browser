package dev.sk2andy.materialbrowser.data.sync

import android.content.Context
import android.os.Build
import androidx.lifecycle.ProcessLifecycleOwner
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.sync.MozillaSyncDevice
import dev.sk2andy.materialbrowser.sync.MozillaSyncState
import dev.sk2andy.materialbrowser.sync.MozillaSyncStatus
import dev.sk2andy.materialbrowser.sync.MozillaSyncTab
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.appservices.fxaclient.FxaServer
import mozilla.appservices.remotetabs.TabsStore
import mozilla.components.browser.storage.sync.RemoteTabsStorage
import mozilla.components.browser.storage.sync.Tab
import mozilla.components.browser.storage.sync.TabEntry
import mozilla.components.concept.base.crash.Breadcrumb
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthFlowError
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.DeviceCapability
import mozilla.components.concept.sync.DeviceCommandOutgoing
import mozilla.components.concept.sync.DeviceConfig
import mozilla.components.concept.sync.DeviceType
import mozilla.components.concept.sync.FxAEntryPoint
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.concept.sync.PeriodicSyncConfig
import mozilla.components.concept.sync.SyncConfig
import mozilla.components.concept.sync.SyncEngine
import mozilla.components.concept.sync.TabPrivacy
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient
import mozilla.components.service.fxa.FxaService
import mozilla.components.service.fxa.ServerConfig
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.sync.GlobalSyncableStoreProvider
import mozilla.components.service.fxa.sync.SyncReason
import mozilla.components.service.fxa.sync.SyncStatusObserver
import mozilla.components.support.AppServicesInitializer
import mozilla.components.support.rusthttp.RustHttpConfig

class MozillaSyncRepository private constructor(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("mozilla_sync", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listeners = mutableSetOf<(MozillaSyncState) -> Unit>()
    private val configured = !BuildConfig.SYSTEM_WEBVIEW_ONLY
    @Volatile private var state = MozillaSyncState(
        status = when {
            !configured -> MozillaSyncStatus.Unavailable
            preferences.getString("profile_id", null) != null -> MozillaSyncStatus.Connecting
            else -> MozillaSyncStatus.SignedOut
        },
        localProfileId = preferences.getString("profile_id", null),
        deviceName = preferences.getString("device_name", "") ?: "",
    )
    private var manager: FxaAccountManager? = null
    private var webChannel: MozillaAccountWebChannel? = null
    private var started = false
    private val storageFailures = AtomicLong()
    private var tabsStorage = newTabsStorage()

    private fun newTabsStorage() = RemoteTabsStorage(
        context,
        object : CrashReporting {
            override fun submitCaughtException(throwable: Throwable): Job {
                storageFailures.incrementAndGet()
                update(state.copy(status = MozillaSyncStatus.Error))
                return Job().apply { complete() }
            }

            override fun recordCrashBreadcrumb(breadcrumb: Breadcrumb) = Unit
        },
    )

    fun currentState(): MozillaSyncState = state

    fun observe(listener: (MozillaSyncState) -> Unit): AutoCloseable {
        listeners.add(listener)
        listener(state)
        return AutoCloseable { listeners.remove(listener) }
    }

    fun configureLocalProfile(profileId: String, deviceName: String): Boolean {
        if (!configured || profileId.isBlank() || deviceName.isBlank()) return false
        if (state.localProfileId != null && state.localProfileId != profileId &&
            state.status != MozillaSyncStatus.SignedOut
        ) return false
        if (!preferences.edit().putString("profile_id", profileId).putString("device_name", deviceName).commit()) {
            return false
        }
        update(state.copy(localProfileId = profileId, deviceName = deviceName))
        manager?.connectedAccount()?.deviceConstellation()?.let { constellation ->
            scope.launch {
                attempt(Unit) {
                    constellation.setDeviceName(deviceName, context)
                    Unit
                }
            }
        }
        return true
    }

    suspend fun start() {
        if (!configured || state.localProfileId == null || started) return
        attempt(Unit) {
            val accountManager = createManager()
            started = true
            try {
                accountManager.start()
            } catch (error: Exception) {
                started = false
                throw error
            }
        }
    }

    suspend fun beginAuthentication(): String? {
        start()
        val accountManager = manager ?: return null
        update(state.copy(status = MozillaSyncStatus.Connecting))
        return attempt(null) {
            accountManager.beginAuthentication(
                entrypoint = object : FxAEntryPoint { override val entryName = "candy-sync" },
                service = FxaService.Sync.identifier,
            )
        }.also { if (it == null) update(state.copy(status = MozillaSyncStatus.Error)) }
    }

    fun handleWebChannelMessage(message: String): String? = webChannel?.handle(message)

    fun authenticationCancelled() {
        if (state.status == MozillaSyncStatus.Connecting) {
            update(state.copy(status = MozillaSyncStatus.Error))
        }
    }

    suspend fun logout() {
        attempt(Unit) {
            manager?.logout()
            withContext(Dispatchers.IO) {
                TabsStore(File(context.filesDir, "tabs.sqlite").canonicalPath).use { store ->
                    store.bridgedEngine().use { it.wipe() }
                }
            }
            tabsStorage.cleanup()
            tabsStorage = newTabsStorage()
            GlobalSyncableStoreProvider.configureStore(SyncEngine.Tabs to lazy { tabsStorage })
            check(preferences.edit().clear().commit()) { "Unable to clear Mozilla Sync binding" }
            update(MozillaSyncState(status = if (configured) MozillaSyncStatus.SignedOut else MozillaSyncStatus.Unavailable))
        }
    }

    suspend fun sync(tabs: List<MozillaSyncTab>) {
        val accountManager = manager ?: return
        if (accountManager.connectedAccount() == null) return
        attempt(Unit) {
            val failuresBefore = storageFailures.get()
            tabsStorage.store(tabs.map { Tab(listOf(TabEntry(it.title, it.url, null)), 0, it.lastUsedMillis, false) })
            if (storageFailures.get() != failuresBefore) return@attempt
            accountManager.syncNow(SyncReason.User, debounce = true, customEngineSubset = listOf(SyncEngine.Tabs))
        }
    }

    suspend fun refresh() {
        attempt(Unit) { refreshDevices() }
    }

    private suspend fun refreshDevices() {
        val accountManager = manager ?: return
        val constellation = accountManager.connectedAccount()?.deviceConstellation() ?: return
        if (!constellation.refreshDevices()) {
            update(state.copy(status = MozillaSyncStatus.Error))
            return
        }
        val devices = constellation.state() ?: return
        val failuresBefore = storageFailures.get()
        val remoteTabs = tabsStorage.getAll()
        if (storageFailures.get() != failuresBefore) return
        update(state.copy(
            status = MozillaSyncStatus.Connected,
            currentDeviceId = devices.currentDevice?.id,
            devices = devices.otherDevices.map { device ->
                MozillaSyncDevice(
                    id = device.id,
                    displayName = device.displayName,
                    canReceiveTabs = DeviceCapability.SEND_TAB in device.capabilities,
                    tabs = remoteTabs.entries.firstOrNull { it.key.id == device.id }?.value.orEmpty().mapNotNull { tab ->
                        tab.history.getOrNull(tab.active)?.let { MozillaSyncTab(it.title, it.url, tab.lastUsed) }
                    },
                )
            },
        ))
    }

    suspend fun sendTab(deviceId: String, title: String, url: String): Boolean = attempt(false) {
        manager?.connectedAccount()?.deviceConstellation()?.sendCommandToDevice(
            deviceId,
            DeviceCommandOutgoing.SendTab(title, url, TabPrivacy.Normal),
        ) ?: false
    }

    private fun createManager(): FxaAccountManager {
        manager?.let { return it }
        AppServicesInitializer.init(AppServicesInitializer.Config(null))
        RustHttpConfig.setClient(lazy { HttpURLConnectionClient() })
        GlobalSyncableStoreProvider.configureStore(SyncEngine.Tabs to lazy { tabsStorage })
        return FxaAccountManager(
            context = context,
            serverConfig = ServerConfig(FxaServer.Release, BuildConfig.MOZILLA_CLIENT_ID, BuildConfig.MOZILLA_REDIRECT_URI),
            deviceConfig = DeviceConfig(
                name = state.deviceName.ifBlank { "Candy Browser ${Build.MODEL}" },
                type = DeviceType.MOBILE,
                capabilities = emptySet(),
                secureStateAtRest = true,
            ),
            syncConfig = SyncConfig(setOf(SyncEngine.Tabs), PeriodicSyncConfig()),
        ).also { accountManager ->
            manager = accountManager
            webChannel = MozillaAccountWebChannel(
                accountManager = accountManager,
                scope = scope,
                onError = { update(state.copy(status = MozillaSyncStatus.Error)) },
                onLogout = { logout() },
            )
            accountManager.register(object : AccountObserver {
                override fun onReady(authenticatedAccount: OAuthAccount?) {
                    update(state.copy(status = when {
                        accountManager.accountNeedsReauth() -> MozillaSyncStatus.Error
                        authenticatedAccount == null -> MozillaSyncStatus.SignedOut
                        else -> MozillaSyncStatus.Connected
                    }))
                    if (authenticatedAccount != null && !accountManager.accountNeedsReauth()) scope.launch { refresh() }
                }
                override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
                    update(state.copy(status = MozillaSyncStatus.Connected))
                    scope.launch {
                        attempt(Unit) {
                            account.deviceConstellation().setDeviceName(state.deviceName, context)
                            Unit
                        }
                        refresh()
                    }
                }
                override fun onLoggedOut() {
                    update(state.copy(status = MozillaSyncStatus.SignedOut, devices = emptyList(), currentDeviceId = null))
                }
                override fun onAuthenticationProblems() { update(state.copy(status = MozillaSyncStatus.Error)) }
                override fun onFlowError(error: AuthFlowError) { update(state.copy(status = MozillaSyncStatus.Error)) }
            })
            accountManager.registerForSyncEvents(object : SyncStatusObserver {
                override fun onStarted() = Unit
                override fun onIdle() { scope.launch { refresh() } }
                override fun onError(error: Exception?) { update(state.copy(status = MozillaSyncStatus.Error)) }
            }, ProcessLifecycleOwner.get(), false)
        }
    }

    private fun update(value: MozillaSyncState) {
        state = value
        scope.launch { listeners.toList().forEach { it(value) } }
    }

    private suspend fun <T> attempt(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        update(state.copy(status = MozillaSyncStatus.Error))
        fallback
    }

    companion object {
        @Volatile private var instance: MozillaSyncRepository? = null

        fun getInstance(context: Context): MozillaSyncRepository = instance ?: synchronized(this) {
            instance ?: MozillaSyncRepository(context).also { instance = it }
        }

        fun install(context: Context) {
            val repository = getInstance(context)
            if (repository.configured && repository.state.localProfileId != null) {
                try {
                    repository.createManager()
                    repository.scope.launch { repository.start() }
                } catch (error: Exception) {
                    repository.update(repository.state.copy(status = MozillaSyncStatus.Error))
                }
            }
        }
    }
}
