package dev.sk2andy.materialbrowser

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.BrowsingLibraryRules
import dev.sk2andy.materialbrowser.data.FavoriteBookmarkFileResult
import dev.sk2andy.materialbrowser.data.FavoriteBookmarkImportReader
import dev.sk2andy.materialbrowser.data.FavoriteBookmarkImportRules
import dev.sk2andy.materialbrowser.data.FavoriteBookmarkMergeResult
import dev.sk2andy.materialbrowser.data.FavoriteBookmarkParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class FavoriteBookmarksImporter(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val browserController: BrowserController,
) {
    private var isImporting = false

    fun import(uri: Uri) {
        if (isImporting) return
        isImporting = true
        lifecycleScope.launch {
            val fileResult = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.let(
                        FavoriteBookmarkImportReader::read,
                    ) ?: FavoriteBookmarkFileResult.Unreadable
                }.getOrDefault(FavoriteBookmarkFileResult.Unreadable)
            }
            when (fileResult) {
                FavoriteBookmarkFileResult.Empty -> FavoriteBookmarkImportFeedback.EmptyFile
                FavoriteBookmarkFileResult.InvalidUtf8 ->
                    FavoriteBookmarkImportFeedback.InvalidUtf8
                FavoriteBookmarkFileResult.TooLarge -> FavoriteBookmarkImportFeedback.FileTooLarge
                FavoriteBookmarkFileResult.Unreadable ->
                    FavoriteBookmarkImportFeedback.UnreadableFile
                is FavoriteBookmarkFileResult.Loaded -> {
                    importLoadedFile(fileResult)
                    return@launch
                }
            }.let(::finishImport)
        }
    }

    private suspend fun importLoadedFile(
        file: FavoriteBookmarkFileResult.Loaded,
    ) = when (val parsed = withContext(Dispatchers.Default) {
        FavoriteBookmarkImportRules.parse(
            source = file.source,
            importedAtMillis = System.currentTimeMillis(),
        )
    }) {
        FavoriteBookmarkParseResult.InvalidFormat -> {
            finishImport(FavoriteBookmarkImportFeedback.InvalidFormat)
        }
        is FavoriteBookmarkParseResult.Parsed -> {
            if (parsed.favorites.isEmpty()) {
                finishImport(FavoriteBookmarkImportFeedback.NoBookmarks)
            } else {
                browserController.importFavoriteBookmarks(parsed.favorites) { result ->
                    finishImport(
                        result?.let(FavoriteBookmarkImportFeedback::from)
                            ?: FavoriteBookmarkImportFeedback.SaveFailed,
                    )
                }
            }
        }
    }

    private fun finishImport(feedback: FavoriteBookmarkImportFeedback) {
        isImporting = false
        Toast.makeText(context, feedback.message(context), Toast.LENGTH_LONG).show()
    }
}

private sealed interface FavoriteBookmarkImportFeedback {
    data class Imported(val count: Int) : FavoriteBookmarkImportFeedback
    data class ImportedUpToLimit(val count: Int) : FavoriteBookmarkImportFeedback
    data object NoBookmarks : FavoriteBookmarkImportFeedback
    data object NoNewFavorites : FavoriteBookmarkImportFeedback
    data object LimitReached : FavoriteBookmarkImportFeedback
    data object EmptyFile : FavoriteBookmarkImportFeedback
    data object FileTooLarge : FavoriteBookmarkImportFeedback
    data object InvalidUtf8 : FavoriteBookmarkImportFeedback
    data object InvalidFormat : FavoriteBookmarkImportFeedback
    data object UnreadableFile : FavoriteBookmarkImportFeedback
    data object SaveFailed : FavoriteBookmarkImportFeedback

    companion object {
        fun from(result: FavoriteBookmarkMergeResult): FavoriteBookmarkImportFeedback = when {
            result.importedCount > 0 && result.limitReached ->
                ImportedUpToLimit(result.importedCount)
            result.importedCount > 0 -> Imported(result.importedCount)
            result.limitReached -> LimitReached
            else -> NoNewFavorites
        }
    }
}

private fun FavoriteBookmarkImportFeedback.message(context: Context): String = when (this) {
    is FavoriteBookmarkImportFeedback.Imported -> context.resources.getQuantityString(
        R.plurals.favorite_bookmark_import_success,
        count,
        count,
    )
    is FavoriteBookmarkImportFeedback.ImportedUpToLimit -> context.resources.getQuantityString(
        R.plurals.favorite_bookmark_import_partial,
        count,
        count,
        BrowsingLibraryRules.MAX_FAVORITES,
    )
    FavoriteBookmarkImportFeedback.NoBookmarks ->
        context.getString(R.string.favorite_bookmark_import_no_bookmarks)
    FavoriteBookmarkImportFeedback.NoNewFavorites ->
        context.getString(R.string.favorite_bookmark_import_no_new)
    FavoriteBookmarkImportFeedback.LimitReached -> context.getString(
        R.string.favorite_bookmark_import_limit_reached,
        BrowsingLibraryRules.MAX_FAVORITES,
    )
    FavoriteBookmarkImportFeedback.EmptyFile ->
        context.getString(R.string.favorite_bookmark_import_empty)
    FavoriteBookmarkImportFeedback.FileTooLarge -> context.getString(
        R.string.favorite_bookmark_import_too_large,
        FavoriteBookmarkImportReader.MAX_FILE_BYTES / (1_024 * 1_024),
    )
    FavoriteBookmarkImportFeedback.InvalidUtf8 ->
        context.getString(R.string.favorite_bookmark_import_invalid_utf8)
    FavoriteBookmarkImportFeedback.InvalidFormat ->
        context.getString(R.string.favorite_bookmark_import_invalid_format)
    FavoriteBookmarkImportFeedback.UnreadableFile ->
        context.getString(R.string.favorite_bookmark_import_unreadable)
    FavoriteBookmarkImportFeedback.SaveFailed ->
        context.getString(R.string.favorites_save_failed)
}
