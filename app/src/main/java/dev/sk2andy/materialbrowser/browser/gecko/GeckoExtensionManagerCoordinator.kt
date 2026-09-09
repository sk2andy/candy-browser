package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class GeckoExtensionManagerMessage {
    InvalidSignedXpi,
    ActionRejected,
    ActionFailed,
}

internal enum class GeckoExtensionManagerPresentation {
    Management,
    Options,
}

internal data class GeckoExtensionManagerState(
    val snapshot: GeckoExtensionSnapshot = GeckoExtensionSnapshot(emptyList()),
    val managementContext: GeckoExtensionManagementContext =
        GeckoExtensionManagementContext(profileId = DEFAULT_PROFILE_ID, isPrivate = false),
    val presentation: GeckoExtensionManagerPresentation =
        GeckoExtensionManagerPresentation.Management,
    val busy: Boolean = false,
    val message: GeckoExtensionManagerMessage? = null,
    val permissionRequest: GeckoExtensionPermissionRequest? = null,
) {
    val canManage: Boolean
        get() = !busy && !managementContext.isPrivate

    private companion object {
        const val DEFAULT_PROFILE_ID = "default"
    }
}

/**
 * Activity-scoped state owner for the process-wide Gecko extension runtime.
 *
 * The browser tab only supplies the management boundary. Installed extensions remain owned by
 * Gecko's process profile and mutations are therefore rejected while a private tab is selected.
 */
internal class GeckoExtensionManagerCoordinator(
    runtime: GeckoExtensionRuntime,
    private val scope: CoroutineScope,
    private val onPageRuntimeChanged: () -> Unit = {},
    private val onOpenOptionsPage: (GeckoExtensionOptionsTarget) -> Boolean = { false },
) {
    private val repository = GeckoExtensionRepository(runtime, ::requestPermission)
    private val extensionRuntime = runtime
    private var permissionDecision: CompletableDeferred<GeckoExtensionPermissionDecision>? = null
    private var visible = false
    private var stateGeneration = 0L

    var state by mutableStateOf(GeckoExtensionManagerState())
        private set

    fun open(
        context: GeckoExtensionManagementContext,
        presentation: GeckoExtensionManagerPresentation =
            GeckoExtensionManagerPresentation.Management,
    ): Job? {
        visible = false
        stateGeneration++
        denyPendingPermission()
        visible = true
        state = state.copy(
            snapshot = GeckoExtensionSnapshot(emptyList()),
            managementContext = context,
            presentation = presentation,
            busy = false,
            message = null,
            permissionRequest = null,
        )
        if (context.isPrivate) return null
        return refresh()
    }

    fun openOptionsPage(
        extensionId: String,
        onOpened: () -> Unit = {},
    ): Job? {
        if (
            !visible ||
            !state.canManage ||
            state.presentation != GeckoExtensionManagerPresentation.Options
        ) {
            return null
        }
        val generation = stateGeneration
        state = state.copy(busy = true, message = null)
        return scope.launch {
            when (val result = repository.refresh()) {
                is GeckoExtensionReadResult.Failed -> {
                    if (!isCurrentRegularState(generation)) return@launch
                    state = state.copy(
                        busy = false,
                        message = GeckoExtensionManagerMessage.ActionFailed,
                    )
                }
                is GeckoExtensionReadResult.Loaded -> {
                    if (!isCurrentRegularState(generation)) return@launch
                    state = state.copy(snapshot = result.snapshot, busy = false)
                    val target = result.snapshot.extension(extensionId)
                        ?.let(GeckoExtensionChromeRules::optionsPageTarget)
                    if (target == null) {
                        state = state.copy(message = GeckoExtensionManagerMessage.ActionRejected)
                    } else if (onOpenOptionsPage(target)) {
                        dismiss()
                        onOpened()
                    } else {
                        state = state.copy(message = GeckoExtensionManagerMessage.ActionFailed)
                    }
                }
            }
        }
    }

    fun refresh(): Job? {
        if (!visible || !state.canManage) return null
        val generation = stateGeneration
        state = state.copy(busy = true, message = null)
        return scope.launch {
            val result = repository.refresh()
            if (!isCurrentRegularState(generation)) return@launch
            state = when (result) {
                is GeckoExtensionReadResult.Loaded -> state.copy(
                    snapshot = result.snapshot,
                    busy = false,
                )
                is GeckoExtensionReadResult.Failed -> state.copy(
                    busy = false,
                    message = GeckoExtensionManagerMessage.ActionFailed,
                )
            }
        }
    }

    fun install(rawUri: String): Job? = mutate {
        repository.installSignedXpi(rawUri, state.managementContext)
    }

    fun setEnabled(extension: GeckoExtension, enabled: Boolean): Job? = mutate {
        if (enabled) {
            repository.enable(extension.id, state.managementContext)
        } else {
            repository.disable(extension.id, state.managementContext)
        }
    }

    fun setAllowedInPrivateBrowsing(extension: GeckoExtension, allowed: Boolean): Job? = mutate(
        reloadSelectedPage = false,
    ) {
        repository.setAllowedInPrivateBrowsing(extension.id, allowed, state.managementContext)
    }

    fun update(extension: GeckoExtension): Job? = mutate {
        repository.update(extension.id, state.managementContext)
    }

    fun uninstall(extension: GeckoExtension): Job? = mutate {
        repository.uninstall(extension.id, state.managementContext)
    }

    fun completePermissionRequest(granted: Boolean, allowPrivate: Boolean) {
        val deferred = permissionDecision ?: return
        deferred.complete(
            GeckoExtensionPermissionDecision(
                grantPermissions = granted,
                allowInPrivateBrowsing = granted && allowPrivate,
            ),
        )
        permissionDecision = null
        state = state.copy(permissionRequest = null)
    }

    fun dismiss() {
        visible = false
        stateGeneration++
        denyPendingPermission()
        state = state.copy(busy = false, permissionRequest = null)
    }

    fun close() {
        dismiss()
        extensionRuntime.setPermissionPrompt { GeckoExtensionPermissionDecision.Denied }
        extensionRuntime.setChangeListener { }
    }

    private fun mutate(
        reloadSelectedPage: Boolean = true,
        mutation: suspend () -> GeckoExtensionMutationResult,
    ): Job? {
        if (!visible || !state.canManage) return null
        val generation = stateGeneration
        state = state.copy(busy = true, message = null)
        return scope.launch {
            val result = mutation()
            if (!isCurrentRegularState(generation)) return@launch
            state = when (result) {
                is GeckoExtensionMutationResult.Applied -> {
                    if (reloadSelectedPage) onPageRuntimeChanged()
                    state.copy(
                        snapshot = result.snapshot,
                        busy = false,
                    )
                }
                is GeckoExtensionMutationResult.Rejected -> state.copy(
                    busy = false,
                    message = when (result.reason) {
                        GeckoExtensionRejection.InvalidSignedXpiUri ->
                            GeckoExtensionManagerMessage.InvalidSignedXpi
                        else -> GeckoExtensionManagerMessage.ActionRejected
                    },
                )
                is GeckoExtensionMutationResult.Failed -> state.copy(
                    busy = false,
                    message = GeckoExtensionManagerMessage.ActionFailed,
                )
            }
        }
    }

    private fun isCurrentRegularState(generation: Long): Boolean =
        visible && stateGeneration == generation && !state.managementContext.isPrivate

    private suspend fun requestPermission(
        request: GeckoExtensionPermissionRequest,
    ): GeckoExtensionPermissionDecision {
        if (!visible || permissionDecision != null) {
            return GeckoExtensionPermissionDecision.Denied
        }
        val deferred = CompletableDeferred<GeckoExtensionPermissionDecision>()
        permissionDecision = deferred
        state = state.copy(permissionRequest = request)
        return try {
            deferred.await()
        } finally {
            if (permissionDecision === deferred) {
                permissionDecision = null
                state = state.copy(permissionRequest = null)
            }
        }
    }

    private fun denyPendingPermission() {
        permissionDecision?.complete(GeckoExtensionPermissionDecision.Denied)
        permissionDecision = null
    }

    companion object {
        fun create(
            context: Context,
            scope: CoroutineScope,
            onPageRuntimeChanged: () -> Unit = {},
            onOpenOptionsPage: (GeckoExtensionOptionsTarget) -> Boolean = { false },
        ): GeckoExtensionManagerCoordinator =
            GeckoExtensionManagerCoordinator(
                runtime = GeckoRuntimeOwner.getOrCreate(context.applicationContext).extensions,
                scope = scope,
                onPageRuntimeChanged = onPageRuntimeChanged,
                onOpenOptionsPage = onOpenOptionsPage,
            )
    }
}
