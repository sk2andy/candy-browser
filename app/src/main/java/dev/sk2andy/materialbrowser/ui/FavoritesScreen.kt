package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.data.BrowsingFavoritesRules
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FavoriteFolder
import dev.sk2andy.materialbrowser.data.FavoriteFolderIcon
import dev.sk2andy.materialbrowser.data.FavoriteLibrary
import dev.sk2andy.materialbrowser.data.FavoriteLibraryEntry
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
    library: FavoriteLibrary? = null,
    folderIcons: Map<String, Bitmap> = emptyMap(),
    onRenameEntry: (FavoriteLibraryEntry, String) -> Unit = { _, _ -> },
    onCreateFolder: (String?, String) -> Unit = { _, _ -> },
    onMoveEntry: (FavoriteLibraryEntry, String?) -> Unit = { _, _ -> },
    onReorderEntry: (FavoriteLibraryEntry, Int) -> Unit = { _, _ -> },
    onFolderIconChange: (FavoriteFolder, FavoriteFolderIcon?) -> Unit = { _, _ -> },
    onUploadFolderIcon: (FavoriteFolder) -> Unit = {},
) {
    val source = library ?: FavoriteLibrary(favorites)
    var query by rememberSaveable { mutableStateOf("") }
    var currentFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val currentFolder = currentFolderId?.let { BrowsingFavoritesRules.folder(source, it) }
    val parentId = currentFolder?.id
    val siblings = BrowsingFavoritesRules.children(source, parentId)
    val visibleEntries = remember(source, parentId, query) {
        if (query.isBlank()) siblings else source.entries.filter { entry ->
            when (entry) {
                is FavoriteEntry -> BrowsingFavoritesRules.visibleEntries(listOf(entry), query).isNotEmpty()
                is FavoriteFolder -> entry.title.contains(query.trim(), ignoreCase = true)
            }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var snackbarJob by remember { mutableStateOf<Job?>(null) }
    var renameTarget by remember { mutableStateOf<FavoriteLibraryEntry?>(null) }
    var creatingFolder by rememberSaveable { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<FavoriteLibraryEntry?>(null) }
    var iconTarget by remember { mutableStateOf<FavoriteFolder?>(null) }
    val removedMessage = stringResource(R.string.favorite_removed_confirmation)
    val undoLabel = stringResource(R.string.action_undo)
    val navigateBack = {
        if (currentFolder != null) {
            currentFolderId = currentFolder.parentFolderId
            query = ""
        } else onBack()
    }
    BackHandler(onBack = navigateBack)

    if (creatingFolder || renameTarget != null) {
        FavoriteNameDialog(
            initialName = renameTarget?.entryTitle().orEmpty(),
            creating = creatingFolder,
            onDismiss = { creatingFolder = false; renameTarget = null },
            onConfirm = { name ->
                renameTarget?.let { onRenameEntry(it, name) } ?: onCreateFolder(parentId, name)
                creatingFolder = false
                renameTarget = null
            },
        )
    }
    moveTarget?.let { target ->
        FavoriteMoveDialog(
            library = source,
            target = target,
            onDismiss = { moveTarget = null },
            onMove = { destination -> onMoveEntry(target, destination); moveTarget = null },
        )
    }
    iconTarget?.let { folder ->
        FavoriteIconDialog(
            folder = folder,
            onDismiss = { iconTarget = null },
            onConfirm = { icon -> onFolderIconChange(folder, icon); iconTarget = null },
            onUpload = { onUploadFolderIcon(folder); iconTarget = null },
        )
    }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var destinationId by remember { mutableStateOf<String?>(null) }
    val currentReorder by rememberUpdatedState(onReorderEntry)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentFolder?.title ?: stringResource(R.string.favorites_title)) },
                actions = {
                    IconButton(onClick = { creatingFolder = true }, modifier = Modifier.testTag("favorites_create_folder")) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.favorites_create_folder))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(contentPadding).navigationBarsPadding()
                .testTag(FavoritesScreenTestTags.List),
        ) {
            item(key = "search") {
                LibrarySearchBar(
                    query = query,
                    placeholder = stringResource(R.string.favorites_search),
                    clearContentDescription = stringResource(R.string.favorites_clear_search),
                    testTag = FavoritesScreenTestTags.SearchField,
                    onQueryChange = { query = it.take(BrowsingFavoritesRules.MAX_QUERY_CHARS) },
                )
            }
            if (currentFolder != null && query.isBlank()) {
                item(key = "parent") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.favorites_parent)) },
                        leadingContent = { Icon(painterResource(R.drawable.ic_folder_arrow_up), null) },
                        modifier = Modifier.clickable(role = Role.Button, onClick = navigateBack)
                            .testTag("favorites_parent"),
                    )
                }
            }
            if (visibleEntries.isEmpty()) {
                item(key = "empty") {
                    FavoritesEmptyState(searching = query.isNotBlank(), insideFolder = currentFolder != null)
                }
            }
            itemsIndexed(visibleEntries, key = { _, entry -> entry.id }) { index, entry ->
                val dragging = draggedId == entry.id
                val lift by animateFloatAsState(if (dragging) 1.025f else 1f, spring(), label = "favorite lift")
                val rowModifier = Modifier.animateItem().zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f; scaleX = lift; scaleY = lift }
                    .pointerInput(entry.id, query, siblings.map { it.id }) {
                        if (query.isNotBlank()) return@pointerInput
                        var startCenter = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedId = entry.id
                                destinationId = entry.id
                                dragOffset = 0f
                                val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.id }
                                startCenter = (row?.offset ?: 0) + (row?.size ?: 0) / 2f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragCancel = { draggedId = null; dragOffset = 0f },
                            onDragEnd = {
                                val destination = siblings.indexOfFirst { it.id == destinationId }
                                if (destination >= 0 && destination != index) currentReorder(entry, destination)
                                draggedId = null
                                dragOffset = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val center = startCenter + dragOffset
                                val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    center >= it.offset && center <= it.offset + it.size && siblings.any { entry -> entry.id == it.key }
                                }?.key as? String
                                if (target != null && destinationId != target) {
                                    destinationId = target
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                        )
                    }
                FavoriteLibraryRow(
                    entry = entry,
                    library = source,
                    favicons = favicons,
                    folderIcon = folderIcons[entry.id],
                    modifier = rowModifier,
                    canMoveEarlier = query.isBlank() && index > 0,
                    canMoveLater = query.isBlank() && index < siblings.lastIndex,
                    onMoveEarlier = { onReorderEntry(entry, index - 1) },
                    onMoveLater = { onReorderEntry(entry, index + 1) },
                    onRename = { renameTarget = entry },
                    onMove = { moveTarget = entry },
                    onIcon = { iconTarget = entry as? FavoriteFolder },
                    onOpen = {
                        when (entry) {
                            is FavoriteEntry -> onOpenFavorite(entry)
                            is FavoriteFolder -> { currentFolderId = entry.id; query = "" }
                        }
                    },
                    onDelete = {
                        if (entry is FavoriteEntry) onDeleteFavorite(entry) { mutation ->
                            if (mutation != null) {
                                snackbarJob?.cancel()
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarJob = coroutineScope.launch {
                                    if (snackbarHostState.showSnackbar(removedMessage, undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
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

private fun FavoriteLibraryEntry.entryTitle(): String = when (this) {
    is FavoriteEntry -> title
    is FavoriteFolder -> title
}

@Composable
private fun FavoriteNameDialog(initialName: String, creating: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (creating) R.string.favorites_create_folder else R.string.favorites_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(BrowsingFavoritesRules.MAX_FOLDER_TITLE_CHARS) },
                label = { Text(stringResource(R.string.favorites_name)) },
                singleLine = true,
                modifier = Modifier.testTag("favorites_name"),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun FavoriteMoveDialog(library: FavoriteLibrary, target: FavoriteLibraryEntry, onDismiss: () -> Unit, onMove: (String?) -> Unit) {
    var destination by rememberSaveable(target.id) { mutableStateOf(target.parentFolderId) }
    val folder = destination?.let { BrowsingFavoritesRules.folder(library, it) }
    val eligible = BrowsingFavoritesRules.children(library, destination).filterIsInstance<FavoriteFolder>().filter {
        it.id != target.id && BrowsingFavoritesRules.move(library, target.id, it.id) != library
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.favorites_move)) },
        text = {
            Column {
                Text(folder?.title ?: stringResource(R.string.favorites_title), style = MaterialTheme.typography.titleMedium)
                LazyColumn {
                    if (folder != null) {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.favorites_parent)) },
                                leadingContent = { Icon(painterResource(R.drawable.ic_folder_arrow_up), null) },
                                modifier = Modifier.clickable(role = Role.Button) { onMove(folder.parentFolderId) }
                                    .testTag("favorites_move_parent"),
                            )
                        }
                    }
                    itemsIndexed(eligible, key = { _, item -> item.id }) { _, item ->
                        ListItem(
                            headlineContent = { Text(item.title) },
                            leadingContent = { Icon(painterResource(R.drawable.ic_folder), null) },
                            modifier = Modifier.clickable { destination = item.id }.testTag("favorites_destination:${item.id}"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMove(destination) }, enabled = destination != target.parentFolderId) {
                Text(stringResource(R.string.favorites_move_here))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun FavoriteIconDialog(folder: FavoriteFolder, onDismiss: () -> Unit, onConfirm: (FavoriteFolderIcon?) -> Unit, onUpload: () -> Unit) {
    var emoji by rememberSaveable(folder.id) { mutableStateOf((folder.icon as? FavoriteFolderIcon.Emoji)?.value.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.favorites_folder_icon)) },
        text = {
            Column {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(BrowsingFavoritesRules.MAX_FOLDER_EMOJI_CHARS) },
                    label = { Text(stringResource(R.string.favorites_folder_emoji)) },
                    singleLine = true,
                )
                TextButton(onClick = onUpload) { Text(stringResource(R.string.favorites_upload_icon)) }
                TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.favorites_reset_icon)) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(FavoriteFolderIcon.Emoji(emoji.trim())) }, enabled = emoji.isNotBlank()) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun FavoriteLibraryRow(
    entry: FavoriteLibraryEntry,
    library: FavoriteLibrary,
    favicons: Map<String, Bitmap>,
    folderIcon: Bitmap?,
    modifier: Modifier,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onIcon: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(entry.entryTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        supportingContent = if (entry is FavoriteEntry) ({ Text(AddressResolver.displayText(entry.url), maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
        leadingContent = {
            when (entry) {
                is FavoriteEntry -> FavoriteFavicon(entry, favicons[entry.url])
                is FavoriteFolder -> FavoriteFolderThumbnail(entry, library, favicons, folderIcon)
            }
        },
        trailingContent = {
            Row {
                if (entry is FavoriteEntry) {
                    IconButton(onClick = onDelete, modifier = Modifier.testTag(FavoritesScreenTestTags.delete(entry.url))) {
                        Icon(Icons.Default.Delete, stringResource(R.string.favorites_delete, entry.title))
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("favorites_actions:${entry.id}")) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.favorites_actions, entry.entryTitle()))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.favorites_rename)) }, onClick = { menuOpen = false; onRename() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.favorites_move)) }, onClick = { menuOpen = false; onMove() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.favorites_move_up)) }, enabled = canMoveEarlier, onClick = { menuOpen = false; onMoveEarlier() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.favorites_move_down)) }, enabled = canMoveLater, onClick = { menuOpen = false; onMoveLater() })
                        if (entry is FavoriteFolder) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.favorites_folder_icon)) }, onClick = { menuOpen = false; onIcon() })
                        }
                    }
                }
            }
        },
        modifier = modifier.clickable(role = Role.Button, onClick = onOpen).testTag(
            if (entry is FavoriteEntry) FavoritesScreenTestTags.favorite(entry.url) else "favorites_folder:${entry.id}",
        ),
    )
}

@Composable
private fun FavoriteFolderThumbnail(folder: FavoriteFolder, library: FavoriteLibrary, favicons: Map<String, Bitmap>, customIcon: Bitmap?) {
    Surface(Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            when {
                folder.icon is FavoriteFolderIcon.Emoji -> Text(folder.icon.value, style = MaterialTheme.typography.titleMedium)
                folder.icon == FavoriteFolderIcon.Custom && customIcon != null && !customIcon.isRecycled ->
                    Image(customIcon.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> {
                    val children = newTabFolderPreviewFavorites(library, folder.id)
                    if (children.isEmpty()) Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(22.dp))
                    else Column(Modifier.padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        children.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                row.forEach { child ->
                                    val bitmap = favicons[child.url]
                                    Box(Modifier.size(14.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                        if (bitmap != null && !bitmap.isRecycled) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize())
                                        else Text(child.entryTitle().take(1), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
            modifier = Modifier.size(32.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        return
    }
    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
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

internal fun favoriteInitial(favorite: FavoriteEntry): String {
    val label = favorite.title.ifBlank { AddressResolver.displayText(favorite.url) }.trim()
    if (label.isEmpty()) return ""
    return String(Character.toChars(label.codePointAt(0))).uppercase()
}

@Composable
private fun FavoritesEmptyState(searching: Boolean, insideFolder: Boolean = false) {
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
                    when {
                        searching -> R.string.favorites_no_matches
                        insideFolder -> R.string.favorites_folder_empty
                        else -> R.string.favorites_empty
                    },
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
