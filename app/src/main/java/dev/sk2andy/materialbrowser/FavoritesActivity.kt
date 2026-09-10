package dev.sk2andy.materialbrowser

import android.app.Activity
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
import dev.sk2andy.materialbrowser.data.FavoriteMutation
import dev.sk2andy.materialbrowser.data.FavoriteUndoRules
import dev.sk2andy.materialbrowser.ui.FavoritesScreen
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FavoritesActivity : ComponentActivity() {
    private val store by lazy { BrowserSessionStore(this) }
    private var favorites by mutableStateOf<List<FavoriteEntry>>(emptyList())
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
                    onDeleteFavorite = ::deleteFavorite,
                    onUndoDelete = ::undoDelete,
                    onOpenFavorite = ::openFavorite,
                    onBack = ::finishWhenIdle,
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullImmersiveMode(isFullImmersiveModeEnabled)
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
                store.saveFavoritesCommitted(updated)
            }
            isFavoriteMutationInFlight = false
            if (!saved) {
                showSaveFailure()
                onComplete(null)
                return@launch
            }
            favorites = updated
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
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                store.saveFavoritesCommitted(restored)
            }
            isFavoriteMutationInFlight = false
            if (!saved) {
                showSaveFailure()
                return@launch
            }
            favoriteRevision++
            favorites = restored
        }
    }

    private fun showSaveFailure() {
        Toast.makeText(this, R.string.favorites_save_failed, Toast.LENGTH_SHORT).show()
    }
}
