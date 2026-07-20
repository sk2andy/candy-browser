package dev.sk2andy.materialbrowser.browser.integration

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

object DefaultBrowserRole {
    fun isHeld(context: Context): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
            roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    }

    /** Returns null when the role is unavailable or already held. */
    fun createRequestIntent(context: Context): Intent? {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) return null
        if (roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
    }
}
