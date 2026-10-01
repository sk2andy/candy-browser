package dev.sk2andy.materialbrowser.browser.integration

import android.content.IntentFilter
import android.net.Uri

internal object ExternalWebAppHandlerRules {
    fun matchesSpecificHost(filter: IntentFilter?, uri: Uri): Boolean {
        if (filter == null) return false
        return (0 until filter.countDataAuthorities()).any { index ->
            val authority = filter.getDataAuthority(index)
            authority.host.removePrefix("*").isNotBlank() && authority.match(uri) >= 0
        }
    }
}
