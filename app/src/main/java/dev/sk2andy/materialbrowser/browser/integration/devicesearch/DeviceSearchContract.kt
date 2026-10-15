package dev.sk2andy.materialbrowser.browser.integration.devicesearch

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.sk2andy.materialbrowser.DeviceSearchResultActivity

object DeviceSearchContract {
    const val ACTION_OPEN = "dev.sk2andy.materialbrowser.action.OPEN_DEVICE_SEARCH_ITEM"
    const val EXTRA_ITEM_ID = "dev.sk2andy.materialbrowser.extra.DEVICE_SEARCH_ITEM_ID"
    const val SCHEME = "candy-device-search"

    fun openIntent(context: Context, itemId: String): Intent =
        Intent(context, DeviceSearchResultActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.Builder().scheme(SCHEME).authority("item").appendPath(itemId).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun itemId(intent: Intent): String? {
        val uri = intent.data ?: return null
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != SCHEME || uri.encodedAuthority != "item" ||
            uri.pathSegments.size != 1 || uri.query != null || uri.fragment != null
        ) return null
        return uri.pathSegments.single().takeIf(DeviceSearchRules::validId)
    }
}
