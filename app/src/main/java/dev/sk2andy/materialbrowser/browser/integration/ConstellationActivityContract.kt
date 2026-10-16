package dev.sk2andy.materialbrowser.browser.integration

import android.content.Context
import android.content.Intent
import dev.sk2andy.materialbrowser.ConstellationActivity

internal object ConstellationActivityContract {
    private const val PROFILE_ID = "constellation_profile_id"
    private const val URL = "constellation_url"

    fun launchIntent(context: Context, profileId: String): Intent =
        Intent(context, ConstellationActivity::class.java).putExtra(PROFILE_ID, profileId)

    fun profileId(intent: Intent?): String? = intent?.getStringExtra(PROFILE_ID)
        ?.takeIf { it.isNotBlank() && it.length <= 128 }

    fun resultIntent(profileId: String, url: String): Intent = Intent()
        .putExtra(PROFILE_ID, profileId).putExtra(URL, url)

    fun navigationUrl(intent: Intent?): String? =
        intent?.getStringExtra(URL)?.let(BrowserUriPolicy::normalizeHttpUrl)
}
