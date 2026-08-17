package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R

internal data class ToppingCatalogItem(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val license: String,
    val version: String,
    val scopes: List<String>,
    val installed: Boolean,
    val enabled: Boolean,
    val updateAvailable: Boolean,
    val busy: Boolean = false,
)

internal sealed interface ToppingCatalogUiState {
    data object Loading : ToppingCatalogUiState

    data class Error(val message: String? = null) : ToppingCatalogUiState

    data class Cached(val items: List<ToppingCatalogItem>) : ToppingCatalogUiState

    data class Content(val items: List<ToppingCatalogItem>) : ToppingCatalogUiState
}

internal object ToppingCatalogTestTags {
    const val Screen = "topping_catalog_screen"
    const val Loading = "topping_catalog_loading"
    const val Error = "topping_catalog_error"
    const val Retry = "topping_catalog_retry"
    const val CachedNotice = "topping_catalog_cached_notice"
    const val List = "topping_catalog_list"
    const val Empty = "topping_catalog_empty"

    fun topping(id: String) = "topping_catalog_item_$id"
    fun toggle(id: String) = "topping_catalog_toggle_$id"
    fun update(id: String) = "topping_catalog_update_$id"
}

@Composable
internal fun ToppingCatalogScreen(
    state: ToppingCatalogUiState,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onUpdate: (id: String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(ToppingCatalogTestTags.Screen),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            ToppingCatalogHeader(onDismiss)
            when (state) {
                ToppingCatalogUiState.Loading -> ToppingCatalogLoading()
                is ToppingCatalogUiState.Error -> ToppingCatalogError(
                    message = state.message,
                    onRetry = onRetry,
                )
                is ToppingCatalogUiState.Cached -> ToppingCatalogList(
                    items = state.items,
                    cached = true,
                    onToggle = onToggle,
                    onUpdate = onUpdate,
                )
                is ToppingCatalogUiState.Content -> ToppingCatalogList(
                    items = state.items,
                    cached = false,
                    onToggle = onToggle,
                    onUpdate = onUpdate,
                )
            }
        }
    }
}

@Composable
private fun ToppingCatalogHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.topping_catalog_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.topping_catalog_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToppingCatalogLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ToppingCatalogTestTags.Loading),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.topping_catalog_loading))
        }
    }
}

@Composable
private fun ToppingCatalogError(
    message: String?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ToppingCatalogTestTags.Error),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.topping_catalog_error_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                message ?: stringResource(R.string.topping_catalog_error_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.testTag(ToppingCatalogTestTags.Retry),
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun ToppingCatalogList(
    items: List<ToppingCatalogItem>,
    cached: Boolean,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onUpdate: (id: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ToppingCatalogTestTags.List),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (cached) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ToppingCatalogTestTags.CachedNotice),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        stringResource(R.string.topping_catalog_cached_notice),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        if (items.isEmpty()) {
            item {
                ToppingCatalogEmpty()
            }
        } else {
            items(items, key = ToppingCatalogItem::id) { topping ->
                ToppingCatalogCard(
                    topping = topping,
                    onToggle = { onToggle(topping.id, it) },
                    onUpdate = { onUpdate(topping.id) },
                )
            }
        }
    }
}

@Composable
private fun ToppingCatalogEmpty() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ToppingCatalogTestTags.Empty),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.topping_catalog_empty_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.topping_catalog_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToppingCatalogCard(
    topping: ToppingCatalogItem,
    onToggle: (Boolean) -> Unit,
    onUpdate: () -> Unit,
) {
    val toggleDescription = toppingToggleDescription(topping)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ToppingCatalogTestTags.topping(topping.id)),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        topping.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        toppingStatus(topping),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Switch(
                    checked = topping.enabled,
                    onCheckedChange = onToggle,
                    enabled = !topping.busy,
                    modifier = Modifier
                        .testTag(ToppingCatalogTestTags.toggle(topping.id))
                        .semantics {
                            contentDescription = toggleDescription
                        },
                )
            }
            Text(
                topping.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.topping_catalog_scope, toppingScopeSummary(topping.scopes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.topping_catalog_author, topping.author),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.topping_catalog_version_license,
                    topping.version,
                    topping.license,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (topping.installed && topping.updateAvailable) {
                OutlinedButton(
                    onClick = onUpdate,
                    enabled = !topping.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 44.dp)
                        .testTag(ToppingCatalogTestTags.update(topping.id)),
                ) {
                    Text(stringResource(R.string.topping_catalog_update))
                }
            }
        }
    }
}

@Composable
private fun toppingStatus(topping: ToppingCatalogItem): String = when {
    topping.busy -> stringResource(R.string.topping_catalog_working)
    !topping.installed -> stringResource(R.string.topping_catalog_not_installed)
    topping.enabled -> stringResource(R.string.topping_catalog_active)
    else -> stringResource(R.string.topping_catalog_inactive)
}

@Composable
private fun toppingToggleDescription(topping: ToppingCatalogItem): String = when {
    !topping.installed -> stringResource(R.string.topping_catalog_install_description, topping.name)
    topping.enabled -> stringResource(R.string.topping_catalog_disable_description, topping.name)
    else -> stringResource(R.string.topping_catalog_enable_description, topping.name)
}

@Composable
private fun toppingScopeSummary(scopes: List<String>): String =
    scopes.takeIf { it.isNotEmpty() }
        ?.joinToString(limit = 3, truncated = " …")
        ?: stringResource(R.string.topping_catalog_scope_unknown)
