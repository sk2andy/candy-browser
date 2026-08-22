package dev.sk2andy.materialbrowser.data

import android.content.Context
import dev.sk2andy.materialbrowser.browser.permissions.PermissionDecisionPersistence
import dev.sk2andy.materialbrowser.browser.permissions.PermissionOrigin
import dev.sk2andy.materialbrowser.browser.permissions.PermissionSiteKey
import dev.sk2andy.materialbrowser.browser.permissions.SitePermission
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionDecision
import org.json.JSONArray
import org.json.JSONObject

class PermissionRadarStore(context: Context) : PermissionDecisionPersistence {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun load(): Map<PermissionSiteKey, Map<SitePermission, SitePermissionDecision>> {
        val raw = preferences.getString(KEY_DECISIONS, null) ?: return emptyMap()
        return runCatching {
            val values = JSONArray(raw)
            buildMap {
                for (index in 0 until values.length().coerceAtMost(MAX_SITES)) {
                    val item = values.getJSONObject(index)
                    val profileId = item.optString("profileId").trim()
                    val origin = PermissionOrigin.normalize(item.optString("origin"))
                    if (profileId.isEmpty() || origin == null) continue
                    val permissions = item.optJSONObject("permissions") ?: continue
                    val decisions = buildMap {
                        SitePermission.entries.forEach { permission ->
                            val decision = runCatching {
                                SitePermissionDecision.valueOf(
                                    permissions.optString(permission.name),
                                )
                            }.getOrNull()
                            if (decision != null && decision != SitePermissionDecision.Ask) {
                                put(permission, decision)
                            }
                        }
                    }
                    if (decisions.isNotEmpty()) put(PermissionSiteKey(profileId, origin), decisions)
                }
            }
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    override fun save(
        decisions: Map<PermissionSiteKey, Map<SitePermission, SitePermissionDecision>>,
    ) {
        val values = JSONArray()
        decisions.asSequence()
            .filter { (site, permissions) ->
                site.profileId.isNotBlank() &&
                    PermissionOrigin.normalize(site.origin) == site.origin &&
                    permissions.any { it.value != SitePermissionDecision.Ask }
            }
            .sortedWith(compareBy({ it.key.profileId }, { it.key.origin }))
            .take(MAX_SITES)
            .forEach { (site, permissions) ->
                val encodedPermissions = JSONObject()
                permissions.forEach { (permission, decision) ->
                    if (decision != SitePermissionDecision.Ask) {
                        encodedPermissions.put(permission.name, decision.name)
                    }
                }
                values.put(
                    JSONObject()
                        .put("profileId", site.profileId)
                        .put("origin", site.origin)
                        .put("permissions", encodedPermissions),
                )
            }
        preferences.edit().putString(KEY_DECISIONS, values.toString()).apply()
    }

    fun flush(): Boolean = preferences.edit().commit()

    internal companion object {
        const val PREFERENCES_NAME = "permission_radar_v1"
        const val KEY_DECISIONS = "decisions"
        const val MAX_SITES = 512
    }
}
