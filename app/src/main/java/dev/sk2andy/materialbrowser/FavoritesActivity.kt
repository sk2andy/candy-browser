package dev.sk2andy.materialbrowser

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import dev.sk2andy.materialbrowser.data.FavoriteFolder
import dev.sk2andy.materialbrowser.data.FavoriteFolderIcon
import dev.sk2andy.materialbrowser.data.FavoriteFolderIconStore
import dev.sk2andy.materialbrowser.data.FavoriteLibrary
import dev.sk2andy.materialbrowser.data.FavoriteMutation
import dev.sk2andy.materialbrowser.data.FavoriteUndoRules
import dev.sk2andy.materialbrowser.ui.FavoritesScreen
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FavoritesActivity : ComponentActivity() {
    private val store by lazy { BrowserSessionStore(this) }
    private val favoriteFaviconRepository by lazy { FavoriteFaviconRepository.get(applicationContext) }
    private val folderIconStore by lazy { FavoriteFolderIconStore(applicationContext) }
    private var favoriteLibrary by mutableStateOf(FavoriteLibrary())
    private var favoriteFavicons by mutableStateOf<Map<String, Bitmap>>(emptyMap())
    private var folderIcons by mutableStateOf<Map<String, Bitmap>>(emptyMap())
    // Retain replaced images until the composition is disposed; rendered frames may still use them.
    private val retiredIcons = mutableListOf<Bitmap>()
    private var favoriteRevision = 0L
    private var undoLibrary: FavoriteLibrary? = null
    private var isFullImmersiveModeEnabled = false
    private var isFavoriteMutationInFlight = false
    private var pendingIconFolderId: String? = null
    private val chooseFolderIcon = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val folderId = pendingIconFolderId
        pendingIconFolderId = null
        if (uri != null && folderId != null) importFolderIcon(folderId, uri)
    }

    private fun importFolderIcon(folderId: String, uri: Uri) {
        if (isFavoriteMutationInFlight) return
        val before = favoriteLibrary
        if (before.folders.none { it.id == folderId }) return
        isFavoriteMutationInFlight = true
        lifecycleScope.launch {
            var previousBitmap: Bitmap? = null
            var importedBitmap: Bitmap? = null
            var committed = false
            var ownershipTransferred = false
            try {
                withContext(Dispatchers.IO) {
                    previousBitmap = folderIconStore.load(folderId)
                    importedBitmap = folderIconStore.import(folderId, uri)
                }
                val bitmap = importedBitmap
                if (bitmap == null) {
                    Toast.makeText(this@FavoritesActivity, R.string.favorites_icon_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val updated = withFolderIcon(before, folderId, FavoriteFolderIcon.Custom)
                withContext(Dispatchers.IO) { committed = store.saveFavoriteLibraryCommitted(updated, before) }
                if (!committed) {
                    showSaveFailure()
                    return@launch
                }
                folderIcons[folderId]?.let(retiredIcons::add)
                folderIcons = folderIcons + (folderId to bitmap)
                ownershipTransferred = true
                favoriteLibrary = updated
                favoriteRevision++
                undoLibrary = null
            } finally {
                if (importedBitmap != null && !committed) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        val previous = previousBitmap
                        if (previous == null) folderIconStore.delete(folderId)
                        else folderIconStore.save(folderId, previous)
                    }
                }
                previousBitmap?.let { if (!it.isRecycled) it.recycle() }
                if (!ownershipTransferred) importedBitmap?.let { if (!it.isRecycled) it.recycle() }
                isFavoriteMutationInFlight = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        pendingIconFolderId = savedInstanceState?.getString(STATE_ICON_FOLDER)
        enableEdgeToEdge()
        isFullImmersiveModeEnabled = store.loadFullImmersiveModeEnabled()
        applyFullImmersiveMode(isFullImmersiveModeEnabled)
        favoriteLibrary = store.loadFavoriteLibrary()
        val appearanceSettings = store.loadAppearanceSettings()
        setContent {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            CandyTheme(settings = appearanceSettings) {
                FavoritesScreen(
                    favorites = favoriteLibrary.favorites,
                    library = favoriteLibrary,
                    favicons = favoriteFavicons,
                    folderIcons = folderIcons,
                    onDeleteFavorite = ::deleteFavorite,
                    onUndoDelete = ::undoDelete,
                    onOpenFavorite = ::openFavorite,
                    onBack = ::finishWhenIdle,
                    onRenameEntry = { entry, title -> mutateLibrary { BrowsingFavoritesRules.rename(it, entry.id, title) } },
                    onCreateFolder = { parentId, title ->
                        mutateLibrary { library ->
                            BrowsingFavoritesRules.addFolder(library, FavoriteFolder(UUID.randomUUID().toString(), title, parentId))
                        }
                    },
                    onMoveEntry = { entry, parentId -> mutateLibrary { BrowsingFavoritesRules.move(it, entry.id, parentId) } },
                    onReorderEntry = { entry, index -> mutateLibrary { BrowsingFavoritesRules.reorder(it, entry.id, index) } },
                    onFolderIconChange = { folder, icon -> mutateLibrary { withFolderIcon(it, folder.id, icon) } },
                    onUploadFolderIcon = { folder ->
                        if (!isFavoriteMutationInFlight) {
                            pendingIconFolderId = folder.id
                            chooseFolderIcon.launch("image/*")
                        }
                    },
                )
            }
        }
        loadIcons()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_ICON_FOLDER, pendingIconFolderId)
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullImmersiveMode(isFullImmersiveModeEnabled)
    }

    override fun onDestroy() {
        (favoriteFavicons.values + folderIcons.values + retiredIcons).distinct().forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        favoriteFavicons = emptyMap()
        folderIcons = emptyMap()
        retiredIcons.clear()
        super.onDestroy()
    }

    private fun loadIcons() {
        val snapshot = favoriteLibrary
        lifecycleScope.launch {
            val loadedFavicons = mutableMapOf<String, Bitmap>()
            val loadedFolders = mutableMapOf<String, Bitmap>()
            var ownershipTransferred = false
            try {
                withContext(Dispatchers.IO) {
                    favoriteFaviconRepository.prune(snapshot.favorites.map(FavoriteEntry::url).toSet())
                    favoriteFaviconRepository.flush()
                    loadedFavicons += favoriteFaviconRepository.loadAll(snapshot.favorites.map(FavoriteEntry::url))
                    val customIds = snapshot.folders.filter { it.icon == FavoriteFolderIcon.Custom }.map(FavoriteFolder::id)
                    loadedFolders += folderIconStore.loadAll(customIds)
                }
                ensureActive()
                favoriteFavicons = loadedFavicons.toMap()
                // The picker or icon editor may finish while the initial cache load is running.
                val currentCustomIds = favoriteLibrary.folders
                    .filter { it.icon == FavoriteFolderIcon.Custom }
                    .map(FavoriteFolder::id)
                    .toSet()
                loadedFolders.filterKeys { it !in currentCustomIds || it in folderIcons }
                    .values.forEach(retiredIcons::add)
                folderIcons.filterKeys { it !in currentCustomIds }.values.forEach(retiredIcons::add)
                folderIcons = loadedFolders.filterKeys(currentCustomIds::contains) +
                    folderIcons.filterKeys(currentCustomIds::contains)
                ownershipTransferred = true
            } finally {
                if (!ownershipTransferred) {
                    (loadedFavicons.values + loadedFolders.values).distinct().forEach { bitmap ->
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun mutateLibrary(transform: (FavoriteLibrary) -> FavoriteLibrary) {
        if (isFavoriteMutationInFlight) return
        val before = favoriteLibrary
        val updated = transform(before)
        if (updated == before) return
        commitLibrary(before, updated) { saved ->
            if (saved) {
                favoriteRevision++
                undoLibrary = null
            }
        }
    }

    private fun commitLibrary(before: FavoriteLibrary, updated: FavoriteLibrary, onComplete: (Boolean) -> Unit) {
        isFavoriteMutationInFlight = true
        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.saveFavoriteLibraryCommitted(updated, before) }
                if (saved) {
                    favoriteLibrary = updated
                    val retainedIconIds = updated.folders.filter { it.icon == FavoriteFolderIcon.Custom }.map(FavoriteFolder::id).toSet()
                    withContext(Dispatchers.IO) { folderIconStore.prune(retainedIconIds) }
                    folderIcons.filterKeys { it !in retainedIconIds }.values.forEach(retiredIcons::add)
                    folderIcons = folderIcons.filterKeys(retainedIconIds::contains)
                } else showSaveFailure()
                onComplete(saved)
            } finally {
                isFavoriteMutationInFlight = false
            }
        }
    }

    private fun openFavorite(favorite: FavoriteEntry) {
        if (isFavoriteMutationInFlight) return
        setResult(Activity.RESULT_OK, FavoritesActivityContract.resultIntent(favorite))
        finish()
    }

    private fun finishWhenIdle() {
        if (!isFavoriteMutationInFlight) finish()
    }

    private fun deleteFavorite(entry: FavoriteEntry, onComplete: (FavoriteMutation?) -> Unit) {
        if (isFavoriteMutationInFlight) {
            onComplete(null)
            return
        }
        val before = favoriteLibrary
        val updated = before.copy(entries = before.entries.filterNot { it.id == entry.id })
        if (updated == before) {
            onComplete(null)
            return
        }
        commitLibrary(before, updated) { saved ->
            if (!saved) {
                onComplete(null)
            } else {
                undoLibrary = before
                favoriteFaviconRepository.prune(updated.favorites.map(FavoriteEntry::url).toSet())
                onComplete(FavoriteMutation(before.favorites, updated.favorites, added = false, revision = ++favoriteRevision))
            }
        }
    }

    private fun undoDelete(mutation: FavoriteMutation) {
        if (isFavoriteMutationInFlight) return
        val restored = undoLibrary ?: return
        FavoriteUndoRules.restore(favoriteLibrary.favorites, favoriteRevision, mutation) ?: return
        commitLibrary(favoriteLibrary, restored) { saved ->
            if (saved) {
                favoriteRevision++
                undoLibrary = null
                mutation.before.firstOrNull { entry -> mutation.applied.none { it.id == entry.id } }?.let { entry ->
                    favoriteFaviconRepository.capture(entry.url, favoriteFavicons[entry.url])
                }
            }
        }
    }

    private fun withFolderIcon(library: FavoriteLibrary, folderId: String, icon: FavoriteFolderIcon?): FavoriteLibrary =
        BrowsingFavoritesRules.normalizeLibrary(
            library.copy(entries = library.entries.map { entry ->
                if (entry is FavoriteFolder && entry.id == folderId) entry.copy(icon = icon) else entry
            }),
        )

    private fun showSaveFailure() {
        Toast.makeText(this, R.string.favorites_save_failed, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val STATE_ICON_FOLDER = "favorite_icon_folder"
    }
}
