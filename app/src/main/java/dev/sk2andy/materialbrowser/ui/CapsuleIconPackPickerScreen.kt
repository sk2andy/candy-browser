@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.capsule.CapsuleIconPack
import dev.sk2andy.materialbrowser.capsule.CapsuleIconPackEntry
import dev.sk2andy.materialbrowser.capsule.CapsuleIconPackRules

@Composable
internal fun CapsuleIconPackPickerScreen(
    packs: List<CapsuleIconPack>,
    selectedPackageName: String?,
    entries: List<CapsuleIconPackEntry>,
    loading: Boolean,
    choosing: Boolean,
    errorMessage: String?,
    onSelectPack: (CapsuleIconPack) -> Unit,
    onSelectEntry: (CapsuleIconPackEntry) -> Unit,
    loadIcon: suspend (CapsuleIconPackEntry) -> Bitmap?,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable(selectedPackageName) { mutableStateOf("") }
    val visibleEntries = remember(entries, query) {
        CapsuleIconPackRules.visibleEntries(entries, query)
    }
    BackHandler(enabled = choosing) {}
    Scaffold(
        modifier = Modifier.testTag(CapsuleIconPackPickerTestTags.Screen),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capsule_icon_pack_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss, enabled = !choosing) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .navigationBarsPadding(),
        ) {
            if (packs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    packs.forEach { pack ->
                        FilterChip(
                            selected = pack.packageName == selectedPackageName,
                            onClick = { onSelectPack(pack) },
                            enabled = !choosing,
                            label = {
                                Text(pack.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            modifier = Modifier.testTag(
                                CapsuleIconPackPickerTestTags.pack(pack.packageName),
                            ),
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(MAX_QUERY_LENGTH) },
                    enabled = !choosing,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.capsule_icon_pack_search)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(CapsuleIconPackPickerTestTags.Search),
                )
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            when {
                loading -> LoadingIconPackState()
                packs.isEmpty() -> EmptyIconPackState(R.string.capsule_icon_pack_none_installed)
                visibleEntries.isEmpty() -> EmptyIconPackState(R.string.capsule_icon_pack_no_matches)
                else -> {
                    Text(
                        text = stringResource(
                            R.string.capsule_icon_pack_result_count,
                            visibleEntries.size,
                            entries.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(MIN_TILE_SIZE),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = visibleEntries,
                            key = { entry -> "${entry.packageName}:${entry.drawableName}" },
                        ) { entry ->
                            CapsuleIconPackTile(
                                entry = entry,
                                enabled = !choosing,
                                loadIcon = loadIcon,
                                onClick = { onSelectEntry(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleIconPackTile(
    entry: CapsuleIconPackEntry,
    enabled: Boolean,
    loadIcon: suspend (CapsuleIconPackEntry) -> Bitmap?,
    onClick: () -> Unit,
) {
    val iconState by produceState<CapsuleIconPackTileState>(
        initialValue = CapsuleIconPackTileState.Loading,
        entry,
    ) {
        value = loadIcon(entry)?.let(CapsuleIconPackTileState::Loaded)
            ?: CapsuleIconPackTileState.Missing
    }
    Surface(
        onClick = onClick,
        enabled = enabled && iconState is CapsuleIconPackTileState.Loaded,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag(
            CapsuleIconPackPickerTestTags.icon(entry.drawableName),
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (val currentState = iconState) {
                    CapsuleIconPackTileState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    CapsuleIconPackTileState.Missing -> {
                        Text(
                            text = entry.label.take(1),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is CapsuleIconPackTileState.Loaded -> Image(
                        bitmap = currentState.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun LoadingIconPackState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyIconPackState(messageResource: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CapsuleIconPackPickerTestTags.Empty),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(messageResource),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

internal object CapsuleIconPackPickerTestTags {
    const val Screen = "capsule_icon_pack_picker"
    const val Search = "capsule_icon_pack_search"
    const val Empty = "capsule_icon_pack_empty"

    fun pack(packageName: String): String = "capsule_icon_pack:$packageName"

    fun icon(drawableName: String): String = "capsule_icon_pack_icon:$drawableName"
}

private const val MAX_QUERY_LENGTH = 120
private val MIN_TILE_SIZE = 88.dp

private sealed interface CapsuleIconPackTileState {
    data object Loading : CapsuleIconPackTileState

    data object Missing : CapsuleIconPackTileState

    data class Loaded(val bitmap: Bitmap) : CapsuleIconPackTileState
}
