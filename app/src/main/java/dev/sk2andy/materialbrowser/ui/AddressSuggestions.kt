@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.browser.commands.AddressSuggestionItem
import dev.sk2andy.materialbrowser.browser.commands.BrowserCommandKind
import dev.sk2andy.materialbrowser.browser.commands.CommandSuggestion
import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.recall.RecallMatch
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens
import eightbitlab.com.blurview.BlurTarget

@Composable
internal fun WebContentContextSheet(
    target: WebContentTarget?,
    onOpenLinkInBackground: () -> Unit,
    onDownloadImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (target == null) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                stringResource(R.string.content_actions_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            if (target.canOpenLinkInBackground) {
                TextButton(
                    onClick = onOpenLinkInBackground,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_open_link_background_tab))
                }
            }
            if (target.canDownloadImage) {
                TextButton(
                    onClick = onDownloadImage,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_download_image))
                }
            }
        }
    }
}

@Composable
internal fun AddressSuggestions(
    suggestions: List<AddressSuggestionItem>,
    highlightedIndex: Int,
    onHighlight: (Int) -> Unit,
    onSelect: (AddressSuggestionItem) -> Unit,
    onFill: (AddressSuggestionItem) -> Unit,
    rootHeightPx: Float,
    bottomBarTopPx: FloatState,
    blurTarget: BlurTarget? = null,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    val listState = rememberLazyListState()
    LaunchedEffect(highlightedIndex, suggestions.map(AddressSuggestionItem::stableId)) {
        if (highlightedIndex in suggestions.indices) {
            listState.scrollToItem(highlightedIndex)
        }
    }
    val density = LocalDensity.current
    val currentBottomBarTopPx = bottomBarTopPx.floatValue
    val bottomPadding = AddressEditorLayoutRules.suggestionBottomPaddingDp(
        rootHeightPx = rootHeightPx,
        bottomBarTopPx = currentBottomBarTopPx,
        density = density.density,
    ).dp
    val maxHeight = AddressEditorLayoutRules.suggestionMaxHeightDp(
        bottomBarTopPx = currentBottomBarTopPx,
        topInsetPx = WindowInsets.statusBars.getTop(density).toFloat(),
        density = density.density,
    ).dp
    val chromeTokens = browserChromeSurfaceTokens().copy(
        containerColor = browserChromeColor(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            frostedAlpha = 0.9f,
        ),
        tonalElevation = 12.dp,
        shadowElevation = 12.dp,
    )
    BrowserChromeSurface(
        blurTarget = blurTarget,
        tokens = chromeTokens,
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = bottomPadding)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .heightIn(max = maxHeight),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            itemsIndexed(
                items = suggestions,
                key = { _, suggestion -> suggestion.stableId },
            ) { index, suggestion ->
                Column {
                    if (
                        suggestion is AddressSuggestionItem.Recall &&
                        suggestions.getOrNull(index - 1) !is AddressSuggestionItem.Recall
                    ) {
                        Text(
                            text = stringResource(R.string.recall_from_history),
                            modifier = Modifier.padding(
                                start = 18.dp,
                                end = 18.dp,
                                top = 8.dp,
                                bottom = 4.dp,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    when (suggestion) {
                    is AddressSuggestionItem.Navigation -> NavigationSuggestionRow(
                        suggestion = suggestion.suggestion,
                        highlighted = index == highlightedIndex,
                        onHighlight = { onHighlight(index) },
                        onClick = { onSelect(suggestion) },
                        onFill = { onFill(suggestion) },
                    )
                    is AddressSuggestionItem.Command -> CommandSuggestionRow(
                        suggestion = suggestion.suggestion,
                        highlighted = index == highlightedIndex,
                        onHighlight = { onHighlight(index) },
                        onClick = { onSelect(suggestion) },
                    )
                    is AddressSuggestionItem.Search -> SearchSuggestionRow(
                        query = suggestion.query,
                        highlighted = index == highlightedIndex,
                        onHighlight = { onHighlight(index) },
                        onClick = { onSelect(suggestion) },
                        onFill = { onFill(suggestion) },
                    )
                    is AddressSuggestionItem.Recall -> RecallSuggestionRow(
                        match = suggestion.match,
                        highlighted = index == highlightedIndex,
                        onHighlight = { onHighlight(index) },
                        onClick = { onSelect(suggestion) },
                        onFill = { onFill(suggestion) },
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecallSuggestionRow(
    match: RecallMatch,
    highlighted: Boolean,
    onHighlight: () -> Unit,
    onClick: () -> Unit,
    onFill: () -> Unit,
) {
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .fillMaxWidth()
            .testTag(AddressSuggestionTestTags.recallRow(match.url))
            .clip(RoundedCornerShape(16.dp))
            .semantics { selected = highlighted }
            .clickable(role = Role.Button) {
                onHighlight()
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_history),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    match.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Text(
                    match.excerpt,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
            IconButton(onClick = onFill, modifier = Modifier.size(40.dp)) {
                Icon(
                    painterResource(R.drawable.ic_north_east),
                    contentDescription = stringResource(
                        R.string.cd_fill_address_suggestion,
                        match.url,
                    ),
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
                )
            }
        }
    }
}

@Composable
private fun NavigationSuggestionRow(
    suggestion: AddressSuggestion,
    highlighted: Boolean,
    onHighlight: () -> Unit,
    onClick: () -> Unit,
    onFill: () -> Unit,
) {
    val switchesToOpenTab = suggestion.openTabId != null
    val containerColor = when {
        highlighted -> MaterialTheme.colorScheme.tertiaryContainer
        switchesToOpenTab -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        highlighted -> MaterialTheme.colorScheme.onTertiaryContainer
        switchesToOpenTab -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .semantics { selected = highlighted }
            .clickable(
                role = Role.Button,
                onClick = {
                    onHighlight()
                    onClick()
                },
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (switchesToOpenTab) R.drawable.ic_switch_to_tab else R.drawable.ic_history,
                ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    suggestion.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Text(
                    AddressResolver.displayText(suggestion.url),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.76f),
                )
            }
            if (switchesToOpenTab) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_switch_to_tab),
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onFill, modifier = Modifier.size(40.dp)) {
                Icon(
                    painterResource(R.drawable.ic_north_east),
                    contentDescription = stringResource(
                        R.string.cd_fill_address_suggestion,
                        suggestion.url,
                    ),
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
                )
            }
        }
    }
}

@Composable
internal fun SearchSuggestionRow(
    query: String,
    highlighted: Boolean,
    onHighlight: () -> Unit,
    onClick: () -> Unit,
    onFill: () -> Unit,
) {
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .fillMaxWidth()
            .testTag(AddressSuggestionTestTags.searchRow(query))
            .clip(RoundedCornerShape(16.dp))
            .semantics { selected = highlighted }
            .clickable(
                role = Role.Button,
                onClick = {
                    onHighlight()
                    onClick()
                },
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                query,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
            IconButton(
                onClick = onFill,
                modifier = Modifier
                    .size(40.dp)
                    .testTag(AddressSuggestionTestTags.fillSearch(query)),
            ) {
                Icon(
                    painterResource(R.drawable.ic_north_east),
                    contentDescription = stringResource(
                        R.string.cd_fill_address_suggestion,
                        query,
                    ),
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
                )
            }
        }
    }
}

@Composable
private fun CommandSuggestionRow(
    suggestion: CommandSuggestion,
    highlighted: Boolean,
    onHighlight: () -> Unit,
    onClick: () -> Unit,
) {
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { selected = highlighted }
            .clickable(role = Role.Button) {
                onHighlight()
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(14.dp),
                color = contentColor.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CommandIcon(suggestion.command.kind, contentColor)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    suggestion.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Text(
                    suggestion.effect,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
            suggestion.command.targetProfileLabel?.let { profile ->
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = contentColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = stringResource(R.string.command_target_profile, profile),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandIcon(kind: BrowserCommandKind, tint: Color) {
    val modifier = Modifier.size(22.dp)
    when (kind) {
        BrowserCommandKind.ClearCacheAndReload,
        BrowserCommandKind.Reload,
        -> Icon(Icons.Default.Refresh, contentDescription = null, modifier = modifier, tint = tint)
        BrowserCommandKind.ClearCookiesAndReload -> Icon(
            painterResource(R.drawable.ic_delete_outline),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
        BrowserCommandKind.StopLoading ->
            Icon(Icons.Default.Close, contentDescription = null, modifier = modifier, tint = tint)
        BrowserCommandKind.PinTab,
        BrowserCommandKind.UnpinTab,
        -> Icon(
            painterResource(R.drawable.ic_push_pin),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
        BrowserCommandKind.CloseDuplicateTabs -> Icon(
            painterResource(R.drawable.ic_content_copy),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
        BrowserCommandKind.MoveTabToProfile,
        BrowserCommandKind.SwitchProfile,
        -> Icon(
            painterResource(R.drawable.ic_switch_to_tab),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
        BrowserCommandKind.NewRegularTab ->
            Icon(Icons.Default.Add, contentDescription = null, modifier = modifier, tint = tint)
        BrowserCommandKind.NewIncognitoTab -> Icon(
            painterResource(R.drawable.ic_incognito_outline),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
        BrowserCommandKind.OpenSettings -> Icon(
            painterResource(R.drawable.ic_settings),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
    }
}

