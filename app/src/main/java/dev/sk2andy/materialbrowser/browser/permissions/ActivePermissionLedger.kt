package dev.sk2andy.materialbrowser.browser.permissions

import java.util.IdentityHashMap

internal data class ActivePermissionGrant(
    val tabId: String,
    val site: PermissionSiteKey,
    val permissions: Set<SitePermission>,
)

internal class ActivePermissionLedger {
    private val grants = IdentityHashMap<Any, ActivePermissionGrant>()

    fun record(token: Any, grant: ActivePermissionGrant) {
        grants[token] = grant
    }

    fun permissions(tabId: String, site: PermissionSiteKey): Set<SitePermission> =
        grants.values
            .asSequence()
            .filter { grant -> grant.tabId == tabId && grant.site == site }
            .flatMap { grant -> grant.permissions.asSequence() }
            .toSet()

    fun has(tabId: String, site: PermissionSiteKey, permission: SitePermission): Boolean =
        grants.values.any { grant ->
            grant.tabId == tabId && grant.site == site && permission in grant.permissions
        }

    fun hasSite(tabId: String, site: PermissionSiteKey): Boolean =
        grants.values.any { grant -> grant.tabId == tabId && grant.site == site }

    fun hasTab(tabId: String): Boolean = grants.values.any { grant -> grant.tabId == tabId }

    fun drop(token: Any): Boolean = grants.remove(token) != null

    fun dropTab(tabId: String): Boolean {
        val tokens = grants.entries
            .asSequence()
            .filter { (_, grant) -> grant.tabId == tabId }
            .map { (token, _) -> token }
            .toList()
        tokens.forEach(grants::remove)
        return tokens.isNotEmpty()
    }

    fun clear() = grants.clear()
}
