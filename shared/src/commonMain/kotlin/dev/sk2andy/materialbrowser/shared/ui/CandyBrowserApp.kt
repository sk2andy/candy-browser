package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PushPin
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuAction
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuItem
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuItemKind
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuLabelKey
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuSection
import dev.sk2andy.materialbrowser.shared.browser.BrowserTabOverviewLayoutRules

data class BrowserViewportTab(
    val id: String,
    val title: String,
    val address: String,
    val isSelected: Boolean,
    val isFavorite: Boolean,
    val isPinned: Boolean,
)

enum class CandyTabOverviewMode {
    Hero,
    Grid,
    List,
}

data class BrowserViewportSnapshot(
    val address: String,
    val pageTitle: String,
    val tabCountLabel: String,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val isLoading: Boolean,
    val menuItems: List<BrowserFeatureMenuItem>,
    val tabs: List<BrowserViewportTab>,
    val isTabOverviewVisible: Boolean,
    val tabOverviewMode: CandyTabOverviewMode,
    val addressFocusRequest: Long,
)

enum class CandyBrowserUiAction {
    Navigate,
    Back,
    Forward,
    Reload,
    Stop,
    NewTab,
    ShowTabs,
}

interface BrowserViewportActionSink {
    fun addressChanged(value: String)

    fun perform(action: CandyBrowserUiAction)

    fun performMenu(action: BrowserFeatureMenuAction)

    fun addressDragged(
        horizontal: Double,
        vertical: Double,
        velocityX: Double,
        viewportWidth: Double,
        isAddressEditing: Boolean,
    )

    fun selectTab(tabId: String)

    fun closeTab(tabId: String)

    fun hideTabOverview()

    fun changeTabOverviewMode(mode: CandyTabOverviewMode)
}

/**
 * Strangler seam between the shared browser chrome and a platform engine view.
 * The viewport stays native; all chrome anatomy and its action routing live here.
 */
@Composable
fun CandyBrowserApp(
    snapshot: BrowserViewportSnapshot,
    actionSink: BrowserViewportActionSink,
    browserViewport: @Composable () -> Unit,
    tabPreview: @Composable (BrowserViewportTab, Modifier) -> Unit = { tab, modifier ->
        CandyTabPreviewFallback(tab = tab, modifier = modifier)
    },
) {
    MaterialTheme(colorScheme = CandyLightColorScheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (snapshot.isTabOverviewVisible) {
                    CandyTabOverview(
                        snapshot = snapshot,
                        actionSink = actionSink,
                        tabPreview = tabPreview,
                    )
                } else if (snapshot.address.isBlank()) {
                    CandyBlankTab()
                } else {
                    browserViewport()
                }
            }
            CandyBrowserChrome(
                snapshot = snapshot,
                actionSink = actionSink,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun CandyBlankTab() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC8D4FB),
                        Color(0xFFD9E1FF),
                        Color(0xFFE5E8FA),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(54.dp))
                .background(Color(0xFF53689A)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(62.dp)) {
                val stroke = Stroke(width = size.minDimension * 0.24f, cap = StrokeCap.Round)
                drawArc(
                    color = Color(0xFFFF2F78),
                    startAngle = 205f,
                    sweepAngle = 86f,
                    useCenter = false,
                    style = stroke,
                )
                drawArc(
                    color = Color(0xFFFF2F78),
                    startAngle = 325f,
                    sweepAngle = 86f,
                    useCenter = false,
                    style = stroke,
                )
                drawArc(
                    color = Color(0xFF7457D7),
                    startAngle = 85f,
                    sweepAngle = 86f,
                    useCenter = false,
                    style = stroke,
                )
            }
        }
    }
}

private val CandyLightColorScheme = lightColorScheme(
    primary = Color(0xFF7652C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF25104F),
    secondary = Color(0xFFCC315F),
    secondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFF5269A3),
    surface = Color(0xFFD9E1FF),
    surfaceContainer = Color(0xFFF0F1FA),
    surfaceContainerHigh = Color(0xFFE7E8F2),
    onSurface = Color(0xFF1A1B21),
    onSurfaceVariant = Color(0xFF5C6070),
    outline = Color(0xFF777B8C),
    outlineVariant = Color(0xFFC7C9D5),
)

@Composable
private fun CandyTabOverview(
    snapshot: BrowserViewportSnapshot,
    actionSink: BrowserViewportActionSink,
    tabPreview: @Composable (BrowserViewportTab, Modifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(top = 48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tabs", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                OverviewModeButton(Icons.Filled.ViewCarousel, "Hero", CandyTabOverviewMode.Hero, snapshot, actionSink)
                OverviewModeButton(Icons.Filled.ViewModule, "Raster", CandyTabOverviewMode.Grid, snapshot, actionSink)
                OverviewModeButton(Icons.Filled.ViewAgenda, "Liste", CandyTabOverviewMode.List, snapshot, actionSink)
                IconButton(onClick = actionSink::hideTabOverview) {
                    Icon(Icons.Filled.Close, contentDescription = "Tabs schließen")
                }
            }
        }
        when (snapshot.tabOverviewMode) {
            CandyTabOverviewMode.Hero -> HeroTabs(snapshot.tabs, actionSink, tabPreview)
            CandyTabOverviewMode.Grid -> GridTabs(snapshot.tabs, actionSink, tabPreview)
            CandyTabOverviewMode.List -> ListTabs(snapshot.tabs, actionSink, tabPreview)
        }
    }
}

@Composable
private fun OverviewModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    mode: CandyTabOverviewMode,
    snapshot: BrowserViewportSnapshot,
    actionSink: BrowserViewportActionSink,
) {
    IconButton(onClick = { actionSink.changeTabOverviewMode(mode) }) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (snapshot.tabOverviewMode == mode) MaterialTheme.colorScheme.primary else Color(0xFF555555),
        )
    }
}

@Composable
private fun HeroTabs(
    tabs: List<BrowserViewportTab>,
    actionSink: BrowserViewportActionSink,
    tabPreview: @Composable (BrowserViewportTab, Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = BrowserTabOverviewLayoutRules.heroCard(
            viewportWidth = maxWidth.value,
            viewportHeight = maxHeight.value,
        )
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(tabs, key = BrowserViewportTab::id) { tab ->
                TabCard(
                    tab = tab,
                    actionSink = actionSink,
                    tabPreview = tabPreview,
                    compact = false,
                    modifier = Modifier
                        .width(layout.width.dp)
                        .height((layout.width / layout.aspectRatio).dp),
                )
            }
        }
    }
}

@Composable
private fun GridTabs(
    tabs: List<BrowserViewportTab>,
    actionSink: BrowserViewportActionSink,
    tabPreview: @Composable (BrowserViewportTab, Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = BrowserTabOverviewLayoutRules.grid(
            viewportWidth = maxWidth.value,
            viewportHeight = maxHeight.value,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(layout.columnCount),
            modifier = Modifier.fillMaxSize().padding(layout.contentPadding.dp),
            horizontalArrangement = Arrangement.spacedBy(layout.itemSpacing.dp),
            verticalArrangement = Arrangement.spacedBy(layout.itemSpacing.dp),
        ) {
            items(tabs, key = BrowserViewportTab::id) { tab ->
                TabCard(
                    tab = tab,
                    actionSink = actionSink,
                    tabPreview = tabPreview,
                    compact = false,
                    modifier = Modifier.height(220.dp),
                )
            }
        }
    }
}

@Composable
private fun ListTabs(
    tabs: List<BrowserViewportTab>,
    actionSink: BrowserViewportActionSink,
    tabPreview: @Composable (BrowserViewportTab, Modifier) -> Unit,
) {
    val layout = BrowserTabOverviewLayoutRules.list()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = layout.horizontalPadding.dp),
        verticalArrangement = Arrangement.spacedBy(layout.itemSpacing.dp),
    ) {
        items(tabs, key = BrowserViewportTab::id) { tab ->
            TabCard(
                tab = tab,
                actionSink = actionSink,
                tabPreview = tabPreview,
                compact = true,
                modifier = Modifier.fillMaxWidth().height(layout.rowHeight.dp),
            )
        }
    }
}

@Composable
private fun TabCard(
    tab: BrowserViewportTab,
    actionSink: BrowserViewportActionSink,
    tabPreview: @Composable (BrowserViewportTab, Modifier) -> Unit,
    compact: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val cardModifier = modifier
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .then(
            if (tab.isSelected) {
                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
            } else {
                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            },
        )
        .clickable { actionSink.selectTab(tab.id) }
    if (compact) {
        Row(
            modifier = cardModifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabPreview(
                tab,
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            TabCardHeader(tab = tab, actionSink = actionSink)
        }
    } else {
        Column(modifier = cardModifier) {
            TabCardHeader(tab = tab, actionSink = actionSink)
            tabPreview(
                tab,
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun TabCardHeader(
    tab: BrowserViewportTab,
    actionSink: BrowserViewportActionSink,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tab.title.ifBlank { "Neuer Tab" },
                maxLines = 1,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                tab.address.ifBlank { "Adresse eingeben" },
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (tab.isPinned) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = "Angeheftet",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (tab.isFavorite) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Favorit",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (!tab.isPinned) {
            IconButton(onClick = { actionSink.closeTab(tab.id) }) {
                Icon(Icons.Filled.Close, contentDescription = "Tab schließen")
            }
        }
    }
}

@Composable
internal fun CandyTabPreviewFallback(
    tab: BrowserViewportTab,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.surfaceContainer,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun CandyBrowserChrome(
    snapshot: BrowserViewportSnapshot,
    actionSink: BrowserViewportActionSink,
    modifier: Modifier = Modifier,
) {
    var address by remember { mutableStateOf(snapshot.address) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isAddressEditing by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportWidth by remember { mutableStateOf(1f) }
    val addressFocusRequester = remember { FocusRequester() }
    val velocityTracker = remember { VelocityTracker() }
    LaunchedEffect(snapshot.address) {
        address = snapshot.address
    }
    LaunchedEffect(snapshot.addressFocusRequest) {
        if (snapshot.addressFocusRequest > 0) {
            addressFocusRequester.requestFocus()
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(34.dp))
                .background(Color.White.copy(alpha = 0.92f))
                .border(1.dp, Color.White.copy(alpha = 0.74f), RoundedCornerShape(34.dp)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .onSizeChanged { viewportWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(isAddressEditing, viewportWidth) {
                    detectDragGestures(
                        onDragStart = {
                            dragOffset = Offset.Zero
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            actionSink.addressDragged(
                                horizontal = dragOffset.x.toDouble(),
                                vertical = dragOffset.y.toDouble(),
                                velocityX = velocityTracker.calculateVelocity().x.toDouble(),
                                viewportWidth = viewportWidth.toDouble(),
                                isAddressEditing = isAddressEditing,
                            )
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = { dragOffset = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            dragOffset += amount
                        },
                    )
                }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .border(2.dp, Color(0xFF151515), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = { actionSink.perform(CandyBrowserUiAction.ShowTabs) }) {
                    Text(snapshot.tabCountLabel, fontWeight = FontWeight.SemiBold)
                }
            }
            BasicTextField(
                value = address,
                onValueChange = { value ->
                    address = value
                    actionSink.addressChanged(value)
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(addressFocusRequester)
                    .onFocusChanged { isAddressEditing = it.isFocused }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.52f))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = { actionSink.perform(CandyBrowserUiAction.Navigate) },
                ),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = Color(0xFF111111),
                    fontWeight = FontWeight.SemiBold,
                ),
                decorationBox = { field ->
                    Box {
                        if (address.isEmpty()) {
                            Text(
                                text = "Adresse oder Suche",
                                maxLines = 1,
                                color = Color(0xFF707070),
                            )
                        }
                        field()
                    }
                },
            )
            IconButton(
                onClick = { actionSink.perform(CandyBrowserUiAction.NewTab) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Neuer Tab")
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Mehr")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .width(360.dp)
                        .heightIn(max = 640.dp),
                ) {
                    CandyBrowserMenu(
                        pageTitle = snapshot.pageTitle,
                        items = snapshot.menuItems,
                        onAction = { action ->
                            menuExpanded = false
                            actionSink.performMenu(action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CandyBrowserMenu(
    pageTitle: String,
    items: List<BrowserFeatureMenuItem>,
    onAction: (BrowserFeatureMenuAction) -> Unit,
) {
    val groupedItems = items.groupBy(BrowserFeatureMenuItem::section)
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Browser",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = pageTitle,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            groupedItems[BrowserFeatureMenuSection.Toolbar].orEmpty().forEach { item ->
                BrowserMenuToolbarAction(
                    label = item.localizedLabel(),
                    icon = { BrowserMenuActionIcon(item.action, modifier = Modifier.size(22.dp)) },
                    enabled = item.enabled,
                    selected = item.checked == true,
                    onClick = { onAction(item.action) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        BrowserFeatureMenuSection.entries
            .filterNot { it == BrowserFeatureMenuSection.Toolbar }
            .forEach { section ->
                val sectionItems = groupedItems[section].orEmpty()
                if (sectionItems.isEmpty()) return@forEach
                Spacer(Modifier.height(12.dp))
                Text(
                    text = section.localizedTitle(),
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    sectionItems.forEachIndexed { index, item ->
                        val shape = when (index) {
                            0 -> RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 4.dp,
                            )
                            sectionItems.lastIndex -> RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 4.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp,
                            )
                            else -> RoundedCornerShape(4.dp)
                        }
                        if (item.kind == BrowserFeatureMenuItemKind.Toggle && item.checked != null) {
                            BrowserMenuToggleItem(
                                label = item.localizedLabel(),
                                supportingText = item.supportingText.orEmpty(),
                                checked = item.checked,
                                enabled = item.enabled,
                                onCheckedChange = { onAction(item.action) },
                                shape = shape,
                            )
                        } else {
                            BrowserMenuRow(
                                label = item.localizedLabel(),
                                icon = {
                                    BrowserMenuActionIcon(
                                        action = item.action,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                shape = shape,
                                enabled = item.enabled,
                                supportingText = item.supportingText,
                                onClick = { onAction(item.action) },
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun BrowserMenuActionIcon(
    action: BrowserFeatureMenuAction,
    modifier: Modifier,
) {
    val imageVector = when (action) {
        BrowserFeatureMenuAction.Back -> Icons.Filled.ArrowBack
        BrowserFeatureMenuAction.Forward -> Icons.Filled.ArrowForward
        BrowserFeatureMenuAction.Reload -> Icons.Filled.Refresh
        BrowserFeatureMenuAction.Stop -> Icons.Filled.Stop
        BrowserFeatureMenuAction.ToggleFavorite -> Icons.Filled.Star
        BrowserFeatureMenuAction.TogglePinned -> Icons.Filled.PushPin
        BrowserFeatureMenuAction.NewTab -> Icons.Filled.Add
        BrowserFeatureMenuAction.CloseTab -> Icons.Filled.Close
        BrowserFeatureMenuAction.OpenReader -> Icons.Filled.MenuBook
        BrowserFeatureMenuAction.TranslatePage -> Icons.Filled.Translate
        BrowserFeatureMenuAction.FindInPage -> Icons.Filled.Search
        BrowserFeatureMenuAction.Share -> Icons.Filled.Share
        BrowserFeatureMenuAction.OpenExternal -> Icons.Filled.OpenInNew
        BrowserFeatureMenuAction.Print -> Icons.Filled.Print
        BrowserFeatureMenuAction.OpenHistory -> Icons.Filled.History
        BrowserFeatureMenuAction.OpenSettings -> Icons.Filled.Settings
        BrowserFeatureMenuAction.Summarize -> Icons.Filled.AutoAwesome
        BrowserFeatureMenuAction.ToggleDesktopView -> Icons.Filled.DesktopWindows
        BrowserFeatureMenuAction.ToggleAlwaysBlockPopups -> Icons.Filled.Block
        else -> Icons.Filled.Language
    }
    Icon(imageVector = imageVector, contentDescription = null, modifier = modifier)
}

private fun BrowserFeatureMenuSection.localizedTitle(): String = when (this) {
    BrowserFeatureMenuSection.Toolbar -> "Werkzeugleiste"
    BrowserFeatureMenuSection.Page -> "Seite"
    BrowserFeatureMenuSection.Toppings -> "Toppings"
    BrowserFeatureMenuSection.Candy -> "Candy"
    BrowserFeatureMenuSection.Browser -> "Browser"
}

private fun BrowserFeatureMenuItem.localizedLabel(): String = dynamicLabel ?: when (labelKey) {
    BrowserFeatureMenuLabelKey.Back -> "Zurück"
    BrowserFeatureMenuLabelKey.Forward -> "Vor"
    BrowserFeatureMenuLabelKey.Reload -> "Neu laden"
    BrowserFeatureMenuLabelKey.StopLoading -> "Stopp"
    BrowserFeatureMenuLabelKey.AddFavorite -> "Favorit hinzufügen"
    BrowserFeatureMenuLabelKey.RemoveFavorite -> "Favorit entfernen"
    BrowserFeatureMenuLabelKey.PinTab -> "Tab anheften"
    BrowserFeatureMenuLabelKey.UnpinTab -> "Tab lösen"
    BrowserFeatureMenuLabelKey.Tabs -> "Tabs"
    BrowserFeatureMenuLabelKey.NewTab -> "Neuer Tab"
    BrowserFeatureMenuLabelKey.CloseTab -> "Tab schließen"
    BrowserFeatureMenuLabelKey.ParkAddressBarRight -> "Adressleiste rechts parken"
    BrowserFeatureMenuLabelKey.Reader -> "Lesemodus"
    BrowserFeatureMenuLabelKey.Translate -> "Übersetzen"
    BrowserFeatureMenuLabelKey.FindInPage -> "Auf Seite suchen"
    BrowserFeatureMenuLabelKey.Share -> "Teilen"
    BrowserFeatureMenuLabelKey.OpenExternal -> "Extern öffnen"
    BrowserFeatureMenuLabelKey.Print -> "Drucken"
    BrowserFeatureMenuLabelKey.CookieBannerRemoval -> "Cookie-Banner entfernen"
    BrowserFeatureMenuLabelKey.ForceVerticalScrolling -> "Vertikales Scrollen erzwingen"
    BrowserFeatureMenuLabelKey.ForcePageZooming -> "Seitenzoom erzwingen"
    BrowserFeatureMenuLabelKey.ForceSafeArea -> "Safe Area erzwingen"
    BrowserFeatureMenuLabelKey.AlwaysBlockPopups -> "Pop-ups immer blockieren"
    BrowserFeatureMenuLabelKey.DesktopView -> "Desktop-Ansicht"
    BrowserFeatureMenuLabelKey.MuteDomain -> "Domain stummschalten"
    BrowserFeatureMenuLabelKey.UnmuteDomain -> "Domain aktivieren"
    BrowserFeatureMenuLabelKey.CandyTrail -> "Candy Trail"
    BrowserFeatureMenuLabelKey.AddSiteCapsule -> "Site Capsule hinzufügen"
    BrowserFeatureMenuLabelKey.Summarize -> "Zusammenfassen"
    BrowserFeatureMenuLabelKey.SnoozeTab -> "Tab schlummern"
    BrowserFeatureMenuLabelKey.DockAddressBar -> "Adressleiste andocken"
    BrowserFeatureMenuLabelKey.SnoozedTabs -> "Schlummernde Tabs"
    BrowserFeatureMenuLabelKey.History -> "Verlauf"
    BrowserFeatureMenuLabelKey.Settings -> "Einstellungen"
    BrowserFeatureMenuLabelKey.FirefoxExtensions -> "Firefox-Erweiterungen"
    BrowserFeatureMenuLabelKey.ToppingsCommand -> "Topping"
}
