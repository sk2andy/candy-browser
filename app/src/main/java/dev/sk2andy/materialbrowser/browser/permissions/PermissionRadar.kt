package dev.sk2andy.materialbrowser.browser.permissions

import java.net.IDN
import java.net.URI

enum class SitePermission {
    Camera,
    Microphone,
    Location,
    MidiSysex,
    ProtectedMedia,
}

enum class SitePermissionDecision {
    Ask,
    Allow,
    Block,
}

enum class SitePermissionActivity {
    Idle,
    Pending,
    Active,
}

data class PermissionSiteKey(
    val profileId: String,
    val origin: String,
)

data class PermissionRadarEntry(
    val permission: SitePermission,
    val decision: SitePermissionDecision,
    val allowedForSession: Boolean,
    val activity: SitePermissionActivity,
)

data class PermissionRadarSnapshot(
    val site: PermissionSiteKey?,
    val isPrivate: Boolean,
    val knownOrigins: List<String>,
    val entries: List<PermissionRadarEntry>,
) {
    companion object {
        val Empty = PermissionRadarSnapshot(
            site = null,
            isPrivate = false,
            knownOrigins = emptyList(),
            entries = SitePermission.entries.map { permission ->
                PermissionRadarEntry(
                    permission = permission,
                    decision = SitePermissionDecision.Ask,
                    allowedForSession = false,
                    activity = SitePermissionActivity.Idle,
                )
            },
        )
    }
}

enum class PermissionPromptChoice {
    AllowOnce,
    AllowAlways,
    Block,
}

data class PermissionPrompt(
    val id: Long,
    val tabId: String,
    val site: PermissionSiteKey,
    val permissions: Set<SitePermission>,
    val isPrivate: Boolean,
)

data class PermissionRequestIdentity(
    val tabId: String,
    val profileId: String,
    val origin: String,
    val navigationGeneration: Int,
    val isPrivate: Boolean,
)

data class PermissionRequestState(
    val tabId: String,
    val profileId: String,
    val topLevelOrigin: String?,
    val navigationGeneration: Int?,
    val isPrivate: Boolean,
    val isSelected: Boolean,
    val isActivityResumed: Boolean,
    val tabExists: Boolean,
)

object PermissionOrigin {
    fun normalize(value: String?): String? {
        val candidate = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (candidate.any { it.code <= 0x20 || it.code == 0x7f }) return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (!uri.isAbsolute || uri.isOpaque || uri.rawUserInfo != null) return null
        val host = uri.host
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.trimEnd('.')
            ?.takeIf(String::isNotBlank)
            ?: return null
        val asciiHost = runCatching {
            if (':' in host) host.lowercase() else IDN.toASCII(host).lowercase()
        }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        val port = runCatching { uri.port }.getOrNull() ?: return null
        if (port !in -1..65535) return null
        val normalizedPort = port.takeIf { it >= 0 }?.takeUnless {
            (scheme == "https" && it == 443) || (scheme == "http" && it == 80)
        }
        val displayedHost = if (':' in asciiHost) "[$asciiHost]" else asciiHost
        return buildString {
            append(scheme)
            append("://")
            append(displayedHost)
            normalizedPort?.let { append(":$it") }
        }
    }

    fun isPotentiallyTrustworthy(origin: String): Boolean {
        val normalized = normalize(origin) ?: return false
        val uri = URI(normalized)
        if (uri.scheme == "https") return true
        val host = uri.host
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.lowercase()
            ?: return false
        return host == "localhost" || host == "127.0.0.1" || host == "::1"
    }
}

object PermissionRequestRules {
    fun isCurrent(identity: PermissionRequestIdentity, state: PermissionRequestState): Boolean =
        state.tabExists &&
            state.isSelected &&
            state.isActivityResumed &&
            state.tabId == identity.tabId &&
            state.profileId == identity.profileId &&
            state.navigationGeneration == identity.navigationGeneration &&
            state.isPrivate == identity.isPrivate &&
            PermissionOrigin.isPotentiallyTrustworthy(identity.origin) &&
            state.topLevelOrigin?.let(PermissionOrigin::isPotentiallyTrustworthy) == true

    fun decisions(
        permissions: Set<SitePermission>,
        decisionFor: (SitePermission) -> SitePermissionDecision,
        allowedForSession: (SitePermission) -> Boolean,
    ): PermissionDecisionMatrix {
        val allowed = linkedSetOf<SitePermission>()
        val pending = linkedSetOf<SitePermission>()
        val blocked = linkedSetOf<SitePermission>()
        permissions.forEach { permission ->
            when {
                allowedForSession(permission) -> allowed += permission
                decisionFor(permission) == SitePermissionDecision.Allow -> allowed += permission
                decisionFor(permission) == SitePermissionDecision.Block -> blocked += permission
                else -> pending += permission
            }
        }
        return PermissionDecisionMatrix(allowed, pending, blocked)
    }

    fun afterRuntimeResult(
        allowed: Set<SitePermission>,
        runtimeGranted: (SitePermission) -> Boolean,
    ): Set<SitePermission> = allowed.filterTo(linkedSetOf()) { permission ->
        permission.runtimePermissions.isEmpty() || runtimeGranted(permission)
    }
}

data class PermissionDecisionMatrix(
    val allowed: Set<SitePermission>,
    val pending: Set<SitePermission>,
    val blocked: Set<SitePermission>,
)

val SitePermission.runtimePermissions: Set<String>
    get() = when (this) {
        SitePermission.Camera -> setOf("android.permission.CAMERA")
        SitePermission.Microphone -> setOf("android.permission.RECORD_AUDIO")
        SitePermission.Location -> setOf(
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
        )
        SitePermission.MidiSysex,
        SitePermission.ProtectedMedia,
        -> emptySet()
    }

interface PermissionDecisionPersistence {
    fun load(): Map<PermissionSiteKey, Map<SitePermission, SitePermissionDecision>>
    fun save(decisions: Map<PermissionSiteKey, Map<SitePermission, SitePermissionDecision>>)
}

class PermissionRadarRepository(
    private val persistence: PermissionDecisionPersistence,
) {
    private val persistentDecisions = persistence.load()
        .mapValuesTo(linkedMapOf()) { (_, decisions) -> decisions.toMutableMap() }
    private val privateDecisions = linkedMapOf<PermissionSiteKey, MutableMap<SitePermission, SitePermissionDecision>>()
    private val sessionAllows = linkedSetOf<PermissionSessionKey>()

    fun decision(
        site: PermissionSiteKey,
        permission: SitePermission,
        isPrivate: Boolean,
    ): SitePermissionDecision = decisions(isPrivate)[site]?.get(permission)
        ?: SitePermissionDecision.Ask

    fun isAllowedForSession(
        site: PermissionSiteKey,
        permission: SitePermission,
        isPrivate: Boolean,
    ): Boolean = PermissionSessionKey(site, permission, isPrivate) in sessionAllows

    fun setDecision(
        site: PermissionSiteKey,
        permission: SitePermission,
        decision: SitePermissionDecision,
        isPrivate: Boolean,
    ) {
        sessionAllows.remove(PermissionSessionKey(site, permission, isPrivate))
        val target = decisions(isPrivate)
        val siteDecisions = target.getOrPut(site) { linkedMapOf() }
        if (decision == SitePermissionDecision.Ask) siteDecisions.remove(permission)
        else siteDecisions[permission] = decision
        if (siteDecisions.isEmpty()) target.remove(site)
        if (!isPrivate) persist()
    }

    fun allowOnce(
        site: PermissionSiteKey,
        permissions: Set<SitePermission>,
        isPrivate: Boolean,
    ) {
        permissions.forEach { permission ->
            sessionAllows += PermissionSessionKey(site, permission, isPrivate)
        }
    }

    fun resetSite(site: PermissionSiteKey, isPrivate: Boolean) {
        decisions(isPrivate).remove(site)
        sessionAllows.removeAll { key -> key.site == site && key.isPrivate == isPrivate }
        if (!isPrivate) persist()
    }

    fun removeProfile(profileId: String) {
        persistentDecisions.keys.removeAll { it.profileId == profileId }
        privateDecisions.keys.removeAll { it.profileId == profileId }
        sessionAllows.removeAll { it.site.profileId == profileId }
        persist()
    }

    fun origins(profileId: String, isPrivate: Boolean): Set<String> = buildSet {
        decisions(isPrivate).keys.asSequence()
            .filter { it.profileId == profileId }
            .mapTo(this, PermissionSiteKey::origin)
        sessionAllows.asSequence()
            .filter { it.site.profileId == profileId && it.isPrivate == isPrivate }
            .mapTo(this) { it.site.origin }
    }

    fun clearPrivateSession() {
        privateDecisions.clear()
        sessionAllows.removeAll(PermissionSessionKey::isPrivate)
    }

    fun clearAll() {
        persistentDecisions.clear()
        privateDecisions.clear()
        sessionAllows.clear()
        persist()
    }

    private fun decisions(
        isPrivate: Boolean,
    ): MutableMap<PermissionSiteKey, MutableMap<SitePermission, SitePermissionDecision>> =
        if (isPrivate) privateDecisions else persistentDecisions

    private fun persist() {
        persistence.save(
            persistentDecisions.mapValues { (_, decisions) -> decisions.toMap() },
        )
    }
}

private data class PermissionSessionKey(
    val site: PermissionSiteKey,
    val permission: SitePermission,
    val isPrivate: Boolean,
)
