package dev.sk2andy.materialbrowser.browser.permissions

internal class PermissionResponseDelivery(
    private val grantCallback: (Set<SitePermission>) -> Unit,
    private val denyCallback: () -> Unit,
) {
    private var completed = false

    fun grant(permissions: Set<SitePermission>): Boolean = complete {
        grantCallback(permissions)
    }

    fun deny(): Boolean = complete(denyCallback)

    fun drop(): Boolean {
        if (completed) return false
        completed = true
        return true
    }

    private inline fun complete(action: () -> Unit): Boolean {
        if (completed) return false
        completed = true
        action()
        return true
    }
}
