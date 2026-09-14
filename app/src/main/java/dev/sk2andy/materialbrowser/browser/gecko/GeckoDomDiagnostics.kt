package dev.sk2andy.materialbrowser.browser.gecko

import androidx.annotation.UiThread
import dev.sk2andy.materialbrowser.BuildConfig
import java.util.IdentityHashMap

/** Explicit shell requests only. One bounded memory-only result; no page text, URLs or selectors. */
@UiThread
internal object GeckoDomDiagnostics {
    data class Snapshot(val status: String = "idle", val payload: String? = null, val requestId: Long = 0)

    private data class Target(
        val isPrivate: Boolean,
        var active: Boolean = false,
        var probe: (((String?) -> Unit) -> Unit)? = null,
        var cancel: (() -> Unit)? = null,
    )

    private val targets = IdentityHashMap<Any, Target>()
    private var generation = 0L
    private var nextRequestId = 0L

    @Volatile
    var snapshot = Snapshot()
        private set

    fun registerSession(owner: Any, isPrivate: Boolean) {
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) return
        targets[owner] = Target(isPrivate)
        invalidate(if (hasPrivateSession()) "private_session" else "idle")
    }

    fun bindProbe(owner: Any, cancel: () -> Unit = {}, probe: ((String?) -> Unit) -> Unit) {
        val target = targets[owner] ?: return
        target.probe = probe
        target.cancel = cancel
    }

    fun setActive(owner: Any, active: Boolean) {
        val target = targets[owner] ?: return
        if (target.active == active) return
        target.active = active
        invalidate()
    }

    fun navigationChanged(owner: Any) {
        if (targets[owner]?.active == true) invalidate()
    }

    fun unregisterSession(owner: Any) {
        targets.remove(owner)
        invalidate()
    }

    fun discard() = invalidate()

    fun request(requestId: Long = ++nextRequestId): String {
        val active = targets.values.filter { it.active && !it.isPrivate && it.probe != null }
        val rejection = GeckoDomDiagnosticsRules.requestRejection(
            enabled = BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS,
            hasPrivateSession = hasPrivateSession(),
            activeTargets = active.size,
            busy = snapshot.status == "pending",
        )
        if (rejection != null) {
            if (rejection != "busy") invalidate(rejection)
            return rejection
        }
        val requestGeneration = ++generation
        snapshot = Snapshot("pending", requestId = requestId)
        requireNotNull(active.single().probe).invoke { payload ->
            if (requestGeneration != generation || hasPrivateSession()) return@invoke
            snapshot = if (GeckoDomDiagnosticsRules.canPublish(
                    requestGeneration, generation, hasPrivateSession(), payload?.length ?: 0,
                )
            ) {
                Snapshot("ready", payload, requestId)
            } else {
                Snapshot("unavailable", requestId = requestId)
            }
        }
        return snapshot.status
    }

    private fun hasPrivateSession(): Boolean = targets.values.any { it.isPrivate }

    private fun invalidate(status: String = if (hasPrivateSession()) "private_session" else "idle") {
        generation++
        snapshot = Snapshot(status)
        targets.values.mapNotNull { it.cancel }.forEach { it() }
    }
}
