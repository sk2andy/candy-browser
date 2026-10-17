package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.browser.BrowserLibraryEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserLibraryRules
import kotlinx.coroutines.launch

enum class BrowserLibraryKind { Favorites, History }

data class BrowserLibrarySnapshot(
    val kind: BrowserLibraryKind,
    val entries: List<BrowserLibraryEntry>,
)

data class BrowserLibraryStrings(
    val favorites: String = "Favoriten",
    val history: String = "Verlauf",
    val back: String = "Zurück",
    val search: String = "Suchen",
    val delete: String = "Entfernen",
    val emptyFavorites: String = "Noch keine Favoriten",
    val emptyHistory: String = "Noch kein Verlauf",
    val noResults: String = "Keine Treffer",
    val clearHistory: String = "Verlauf löschen",
    val clearHistoryMessage: String = "Besuchte Seiten dieses Profils dauerhaft entfernen?",
    val cancel: String = "Abbrechen",
    val favoriteRemoved: String = "Favorit entfernt",
    val undo: String = "Rückgängig",
)

/** Shared library presentation. Native hosts provide storage and navigation through callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserLibraryScreen(
    snapshot: BrowserLibrarySnapshot,
    onOpen: (BrowserLibraryEntry) -> Unit,
    onRemove: (BrowserLibraryEntry) -> Unit,
    onRestore: (BrowserLibraryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
    strings: BrowserLibraryStrings = BrowserLibraryStrings(),
) {
    var query by remember(snapshot.kind) { mutableStateOf("") }
    var clearConfirmationVisible by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val visibleEntries = remember(snapshot.entries, query) {
        BrowserLibraryRules.matchingEntries(snapshot.entries, query)
    }
    val isFavorites = snapshot.kind == BrowserLibraryKind.Favorites
    val effects = LocalCandyBrowserChromeEffects.current
    Scaffold(
        modifier = Modifier.testTag("browser_library"),
        topBar = {
            CandyPageHeader(
                title = if (isFavorites) strings.favorites else strings.history,
                backContentDescription = strings.back,
                onBack = onDismiss,
                actions = {
                    if (!isFavorites && snapshot.entries.isNotEmpty()) {
                        IconButton(onClick = { clearConfirmationVisible = true }) {
                            effects.icon("trash", Icons.Default.Delete, strings.clearHistory)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibrarySearchBar(
                query = query,
                placeholder = strings.search,
                clearContentDescription = strings.delete,
                testTag = "browser_library_search",
                onQueryChange = { query = it },
            )
            if (visibleEntries.isEmpty()) {
                Text(
                    text = if (query.isNotBlank()) strings.noResults else {
                        if (isFavorites) strings.emptyFavorites else strings.emptyHistory
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(
                modifier = Modifier
                    .padding(horizontal = if (effects.usesGroupedSettings) 16.dp else 0.dp)
                    .then(if (effects.usesGroupedSettings) Modifier.clip(RoundedCornerShape(12.dp)) else Modifier),
            ) {
                itemsIndexed(visibleEntries) { index, entry ->
                    ListItem(
                        headlineContent = {
                            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(entry.address, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    onRemove(entry)
                                    if (isFavorites) scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        if (snackbar.showSnackbar(
                                            message = strings.favoriteRemoved,
                                            actionLabel = strings.undo,
                                            duration = SnackbarDuration.Short,
                                        ) == SnackbarResult.ActionPerformed) onRestore(entry)
                                    }
                                },
                                modifier = Modifier.testTag("browser_library_remove:${entry.address}"),
                            ) {
                                effects.icon("trash", Icons.Default.Delete, "${strings.delete}: ${entry.title}")
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (effects.usesGroupedSettings) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier.clickable(role = Role.Button) { onOpen(entry) }
                            .testTag("browser_library_entry:${entry.address}"),
                    )
                    if (effects.usesGroupedSettings && index < visibleEntries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
    if (clearConfirmationVisible) {
        CandyAlertDialog(
            onDismissRequest = { clearConfirmationVisible = false },
            title = { Text(strings.clearHistory) },
            text = { Text(strings.clearHistoryMessage) },
            confirmButton = {
                CandyActionButton(onClick = {
                    clearConfirmationVisible = false
                    onClearHistory()
                }) { Text(strings.clearHistory) }
            },
            dismissButton = {
                CandyActionButton(onClick = { clearConfirmationVisible = false }) { Text(strings.cancel) }
            },
        )
    }
}
