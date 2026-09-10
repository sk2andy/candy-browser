package dev.sk2andy.materialbrowser.browser.integration

import android.content.Context
import android.content.Intent
import dev.sk2andy.materialbrowser.FavoritesActivity
import dev.sk2andy.materialbrowser.data.FavoriteEntry

internal object FavoritesActivityContract {
    private const val EXTRA_URL = "favorite_url"

    fun launchIntent(context: Context): Intent = Intent(context, FavoritesActivity::class.java)

    fun resultIntent(entry: FavoriteEntry): Intent = Intent().apply {
        putExtra(EXTRA_URL, entry.url)
    }

    fun navigationUrlFrom(intent: Intent?): String? = intent
        ?.getStringExtra(EXTRA_URL)
        ?.takeIf(String::isNotBlank)
}
