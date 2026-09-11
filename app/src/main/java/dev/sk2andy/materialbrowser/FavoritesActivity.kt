package dev.sk2andy.materialbrowser

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.browser.integration.FavoritesActivityContract
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowsingFavoritesRules
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FavoriteFaviconRepository
import dev.sk2andy.materialbrowser.data.FavoriteMutation
import dev.sk2andy.materialbrowser.data.FavoriteUndoRules
import dev.sk2andy.materialbrowser.ui.FavoritesScreen
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FavoritesActivity : ComponentActivity() {
    private val store by lazy { BrowserSessionStore(this) }
    private val favoriteFaviconRepository by lazy {
        FavoriteFaviconRepository.get(applicationContext)
    }
    private var favorites by mutableStateOf<List<FavoriteEntry>>(emptyList())
    private var favoriteFavicons by mutableStateOf<Map<String, Bitmap>>(emptyMap())
    private var favoriteRevision = 0L
    private var isFullImmersiveModeEnabled = false
    private var isFavoriteMutationInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        enableEdgeToEdge()
        isFullImmersiveModeEnabled = store.loadFullImmersiveModeEnabled()
        applyFullImmersiveMode(isFullImmersiveModeEnabled)
        favorites = store.loadFavorites()
        val appearanceSettings = store.loadAppearanceSettings()

        setContent {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            CandyTheme(settings = appearanceSettings) {
                FavoritesScreen(
                    favorites = favorites,
                    favicons = favoriteFavicons,
                    onDeleteFavorite = ::deleteFavorite,
                    onUndoDelete = ::undoDelete,
                    onOpenFavorite = ::openFavorite,
                    onBack = ::finishWhenIdle,
                )
            }
        }
        loadFavoriteFavicons()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullImmersiveMode(isFullImmersiveModeEnabled)
    }

    override fun onDestroy() {
        favoriteFavicons.values.distinct().forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        favoriteFavicons = emptyMap()
        super.onDestroy()
    }

    private fun loadFavoriteFavicons() {
        val entries = favorites
        lifecycleScope.launch {
            val loadedByUrl = mutableMapOf<String, Bitmap>()
            var ownershipTransferred = false
            try {
                withContext(Dispatchers.IO) {
                    favoriteFaviconRepository.prune(entries.map(FavoriteEntry::url).toSet())
                    favoriteFaviconRepository.flush()
                    loadedByUrl += favoriteFaviconRepository.loadAll(
                        entries.map(FavoriteEntry::url),
                    )
                }
                ensureActive()
                favoriteFavicons = loadedByUrl.toMap()
                ownershipTransferred = true
            } finally {
                if (!ownershipTransferred) {
                    loadedByUrl.values.distinct().forEach { bitmap ->
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun openFavorite(favorite: FavoriteEntry) {
        if (isFavoriteMutationInFlight) return
        setResult(
            Activity.RESULT_OK,
            FavoritesActivityContract.resultIntent(favorite),
        )
        finish()
    }

    private fun finishWhenIdle() {
        if (!isFavoriteMutationInFlight) finish()
    }

    private fun deleteFavorite(
        entry: FavoriteEntry,
        onComplete: (FavoriteMutation?) -> Unit,
    ) {
        if (isFavoriteMutationInFlight) {
            onComplete(null)
            return
        }
        val before = favorites
        val updated = BrowsingFavoritesRules.remove(before, entry)
        if (updated == before) {
            onComplete(null)
            return
        }
        isFavoriteMutationInFlight = true
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                store.saveFavoritesCommitted(
                    favorites = updated,
                    expectedCurrent = before,
                )
            }
            isFavoriteMutationInFlight = false
            if (!saved) {
                showSaveFailure()
                onComplete(null)
                return@launch
            }
            favorites = updated
            favoriteFaviconRepository.prune(updated.map(FavoriteEntry::url).toSet())
            onComplete(
                FavoriteMutation(
                    before = before,
                    applied = updated,
                    added = false,
                    revision = ++favoriteRevision,
                ),
            )
        }
    }

    private fun undoDelete(mutation: FavoriteMutation) {
        if (isFavoriteMutationInFlight) return
        val restored = FavoriteUndoRules.restore(
            current = favorites,
            currentRevision = favoriteRevision,
            mutation = mutation,
        ) ?: return
        isFavoriteMutationInFlight = true
        val beforeUndo = favorites
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                store.saveFavoritesCommitted(
                    favorites = restored,
                    expectedCurrent = beforeUndo,
                )
            }
            isFavoriteMutationInFlight = false
            if (!saved) {
                showSaveFailure()
                return@launch
            }
            favoriteRevision++
            favorites = restored
            favoriteFaviconRepository.prune(restored.map(FavoriteEntry::url).toSet())
            mutation.before
                .firstOrNull { entry -> mutation.applied.none { it.url == entry.url } }
                ?.let { entry ->
                    favoriteFaviconRepository.capture(entry.url, favoriteFavicons[entry.url])
                }
        }
    }

    private fun showSaveFailure() {
        Toast.makeText(this, R.string.favorites_save_failed, Toast.LENGTH_SHORT).show()
    }
}
