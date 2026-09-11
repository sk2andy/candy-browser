package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.data.BrowsingFavoritesRules
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FavoriteMutation
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesScreen(
    favorites: List<FavoriteEntry>,
    favicons: Map<String, Bitmap> = emptyMap(),
    onDeleteFavorite: (FavoriteEntry, (FavoriteMutation?) -> Unit) -> Unit,
    onUndoDelete: (FavoriteMutation) -> Unit,
    onOpenFavorite: (FavoriteEntry) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleFavorites = remember(favorites, query) {
        BrowsingFavoritesRules.visibleEntries(favorites, query)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var snackbarJob by remember { mutableStateOf<Job?>(null) }
    val removedMessage = stringResource(R.string.favorite_removed_confirmation)
    val undoLabel = stringResource(R.string.action_undo)

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .navigationBarsPadding()
                .testTag(FavoritesScreenTestTags.List),
        ) {
            item(key = "search") {
                LibrarySearchBar(
                    query = query,
                    placeholder = stringResource(R.string.favorites_search),
                    clearContentDescription = stringResource(R.string.favorites_clear_search),
                    testTag = FavoritesScreenTestTags.SearchField,
                    onQueryChange = {
                        query = it.take(BrowsingFavoritesRules.MAX_QUERY_CHARS)
                    },
                )
            }

            if (visibleFavorites.isEmpty()) {
                item(key = "empty") {
                    FavoritesEmptyState(searching = query.isNotBlank())
                }
            } else {
                items(
                    items = visibleFavorites,
                    key = FavoriteEntry::url,
                ) { favorite ->
                    FavoriteListItem(
                        favorite = favorite,
                        favicon = favicons[favorite.url],
                        onOpen = { onOpenFavorite(favorite) },
                        onDelete = {
                            onDeleteFavorite(favorite) { mutation ->
                                if (mutation != null) {
                                    snackbarJob?.cancel()
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarJob = coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = removedMessage,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Long,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onUndoDelete(mutation)
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteListItem(
    favorite: FavoriteEntry,
    favicon: Bitmap?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = favorite.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(
                text = AddressResolver.displayText(favorite.url),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            FavoriteFavicon(favorite = favorite, favicon = favicon)
        },
        trailingContent = {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag(FavoritesScreenTestTags.delete(favorite.url)),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(
                        R.string.favorites_delete,
                        favorite.title,
                    ),
                )
            }
        },
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag(FavoritesScreenTestTags.favorite(favorite.url)),
    )
}

@Composable
private fun FavoriteFavicon(
    favorite: FavoriteEntry,
    favicon: Bitmap?,
) {
    if (favicon != null && !favicon.isRecycled) {
        Image(
            bitmap = favicon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            contentScale = ContentScale.Fit,
        )
        return
    }
    Surface(
        modifier = Modifier.size(32.dp),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = favoriteInitial(favorite),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun favoriteInitial(favorite: FavoriteEntry): String {
    val label = favorite.title.ifBlank { AddressResolver.displayText(favorite.url) }.trim()
    if (label.isEmpty()) return ""
    return String(Character.toChars(label.codePointAt(0))).uppercase()
}

@Composable
private fun FavoritesEmptyState(searching: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_symbol_favorite),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    if (searching) R.string.favorites_no_matches else R.string.favorites_empty,
                ),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal object FavoritesScreenTestTags {
    const val List = "favorites_list"
    const val SearchField = "favorites_search_field"

    fun favorite(url: String): String = "favorite:$url"

    fun delete(url: String): String = "favorite_delete:$url"
}
