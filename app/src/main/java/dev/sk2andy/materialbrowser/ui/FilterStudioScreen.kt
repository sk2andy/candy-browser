package dev.sk2andy.materialbrowser.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.CandyDecisionAction
import dev.sk2andy.materialbrowser.blocking.CandyImportFormat
import dev.sk2andy.materialbrowser.blocking.CandyImportScope
import dev.sk2andy.materialbrowser.blocking.CandyRule
import dev.sk2andy.materialbrowser.blocking.CandyRuleAction
import dev.sk2andy.materialbrowser.blocking.CandyRuleDecision
import dev.sk2andy.materialbrowser.blocking.CandyRuleKind
import dev.sk2andy.materialbrowser.blocking.CandyRuleOrigin
import dev.sk2andy.materialbrowser.blocking.CandyRulePreview
import dev.sk2andy.materialbrowser.blocking.CandySubscriptionClient
import dev.sk2andy.materialbrowser.blocking.CandySubscriptionDiff
import dev.sk2andy.materialbrowser.blocking.CandySubscriptionResult
import dev.sk2andy.materialbrowser.blocking.CandySubscriptionRules
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object FilterStudioTestTags {
    const val Screen = "filter_studio_screen"
    const val Search = "filter_studio_search"
    const val LiveTest = "filter_studio_live_test"
    const val Add = "filter_studio_add"
    const val Body = "filter_studio_body"
    const val EmptyState = "filter_studio_empty_state"
    const val WebList = "filter_studio_web_list"
    const val ImportOpen = "filter_studio_import_open"
    const val ImportInput = "filter_studio_import_input"
    const val ImportAnalyze = "filter_studio_import_analyze"
    const val ImportScopeGlobal = "filter_studio_import_scope_global"
    const val ImportScopeCurrent = "filter_studio_import_scope_current"
    const val ImportSkippedConfirm = "filter_studio_import_skipped_confirm"
    const val ImportConfirm = "filter_studio_import_confirm"
}

private enum class StudioTypeFilter { All, Block, Allow, Css }

private const val RULE_ITEMS_START_INDEX = 10

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterStudioScreen(
    rules: List<CandyRule>,
    profiles: List<BrowserProfile>,
    currentProfileId: String,
    currentUrl: String,
    recentDomain: String?,
    selectedRuleId: String?,
    onTest: (String) -> CandyRuleDecision?,
    onAdd: (CandyRule) -> CandyRule?,
    onUpdate: (CandyRule) -> CandyRule?,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onParseImport: (String) -> CandyRulePreview,
    onApplyImport: (CandyRulePreview) -> Int,
    onApplySubscription: (String, CandyRulePreview) -> Int,
    onExport: () -> String,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf(StudioTypeFilter.All) }
    var profileOnly by rememberSaveable { mutableStateOf(false) }
    var liveInput by rememberSaveable(recentDomain, currentUrl) {
        mutableStateOf(recentDomain.orEmpty().ifBlank { currentUrl })
    }
    var liveDecision by remember { mutableStateOf<CandyRuleDecision?>(null) }
    var addVisible by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<CandyRule?>(null) }
    var importVisible by remember { mutableStateOf(false) }
    var exportVisible by remember { mutableStateOf(false) }
    var subscriptionVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val view = LocalView.current
    val filtered = remember(rules, query, typeFilter, profileOnly, currentProfileId) {
        rules.filter { rule ->
            val matchesType = when (typeFilter) {
                StudioTypeFilter.All -> true
                StudioTypeFilter.Block -> rule.action == CandyRuleAction.Block
                StudioTypeFilter.Allow -> rule.action == CandyRuleAction.Allow
                StudioTypeFilter.Css -> rule.action == CandyRuleAction.Cosmetic
            }
            val matchesProfile = !profileOnly || rule.profileId == currentProfileId
            val needle = query.trim().lowercase()
            val matchesQuery = needle.isEmpty() || listOfNotNull(
                rule.requestHost,
                rule.firstPartyHost,
                rule.cosmeticSelector,
                rule.group,
                rule.sourceUrl,
            ).any { needle in it.lowercase() }
            matchesType && matchesProfile && matchesQuery
        }
    }
    LaunchedEffect(selectedRuleId, filtered.map(CandyRule::id)) {
        val index = filtered.indexOfFirst { it.id == selectedRuleId }
        if (index >= 0) listState.animateScrollToItem(RULE_ITEMS_START_INDEX + index)
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(FilterStudioTestTags.Screen),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
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
                        stringResource(R.string.filter_studio_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.filter_studio_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag(FilterStudioTestTags.Body),
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { addVisible = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .sizeIn(minHeight = 52.dp)
                                .testTag(FilterStudioTestTags.Add),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.filter_add_rule))
                        }
                        Text(
                            stringResource(R.string.filter_add_rule_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    StudioSectionHeader(title = stringResource(R.string.filter_rules_section))
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(FilterStudioTestTags.Search),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text(stringResource(R.string.filter_search)) },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioTypeFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = typeFilter == filter,
                                onClick = { typeFilter = filter },
                                label = { Text(filter.label()) },
                            )
                        }
                        FilterChip(
                            selected = profileOnly,
                            onClick = { profileOnly = !profileOnly },
                            label = { Text(stringResource(R.string.filter_scope_profile)) },
                        )
                    }
                }
                item {
                    StudioSectionHeader(title = stringResource(R.string.filter_test_section))
                }
                item {
                    FilterLiveTestCard(
                        liveInput = liveInput,
                        onInputChanged = { liveInput = it },
                        decision = liveDecision,
                        onTest = {
                            liveDecision = onTest(liveInput)
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        },
                    )
                }
                item {
                    StudioSectionHeader(title = stringResource(R.string.filter_tools_section))
                }
                item {
                    RuleTransferCard(
                        onImport = { importVisible = true },
                        onExport = { exportVisible = true },
                    )
                }
                item {
                    WebFilterListCard(onOpen = { subscriptionVisible = true })
                }
                item {
                    StudioSectionHeader(
                        title = stringResource(R.string.filter_rule_results_section),
                        trailing = stringResource(R.string.filter_rules_count, filtered.size),
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        StudioEmptyState(
                            title = stringResource(
                                if (rules.isEmpty()) {
                                    R.string.filter_empty_title
                                } else {
                                    R.string.filter_no_results_title
                                },
                            ),
                            body = stringResource(
                                if (rules.isEmpty()) {
                                    R.string.filter_empty_body
                                } else {
                                    R.string.filter_no_results_body
                                },
                            ),
                            actionVisible = rules.isEmpty(),
                            onAdd = { addVisible = true },
                        )
                    }
                } else {
                    items(filtered, key = CandyRule::id) { rule ->
                        FilterRuleCard(
                            rule = rule,
                            profileLabel = rule.profileId?.let { id ->
                                profiles.firstOrNull { it.id == id }?.emoji ?: id
                            } ?: stringResource(R.string.filter_scope_global),
                            selected = rule.id == selectedRuleId,
                            onToggle = { onToggle(rule.id, it) },
                            onEdit = { editingRule = rule },
                            onDelete = { onDelete(rule.id) },
                        )
                    }
                }
            }
        }
    }

    if (addVisible) {
        AddFilterRuleDialog(
            initialRule = null,
            profiles = profiles,
            currentProfileId = currentProfileId,
            onDismiss = { addVisible = false },
            onAdd = { rule ->
                if (onAdd(rule) != null) {
                    addVisible = false
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    true
                } else {
                    false
                }
            },
        )
    }
    editingRule?.let { rule ->
        AddFilterRuleDialog(
            initialRule = rule,
            profiles = profiles,
            currentProfileId = currentProfileId,
            onDismiss = { editingRule = null },
            onAdd = { updated ->
                if (onUpdate(updated) != null) {
                    editingRule = null
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    true
                } else {
                    false
                }
            },
        )
    }
    if (importVisible) {
        FilterImportDialog(
            profiles = profiles,
            currentProfileId = currentProfileId,
            onDismiss = { importVisible = false },
            onParse = onParseImport,
            onApply = {
                val applied = onApplyImport(it)
                if (applied > 0) {
                    importVisible = false
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    true
                } else {
                    false
                }
            },
        )
    }
    if (exportVisible) {
        TextPreviewDialog(
            title = stringResource(R.string.filter_export),
            text = onExport(),
            onDismiss = { exportVisible = false },
        )
    }
    if (subscriptionVisible) {
        FilterSubscriptionDialog(
            existingRules = rules,
            onDismiss = { subscriptionVisible = false },
            onApply = { source, preview ->
                onApplySubscription(source, preview)
                subscriptionVisible = false
            },
        )
    }
}

@Composable
private fun StudioSectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        trailing?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudioEmptyState(
    title: String,
    body: String,
    actionVisible: Boolean,
    onAdd: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FilterStudioTestTags.EmptyState),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionVisible) {
                OutlinedButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.filter_add_first_rule))
                }
            }
        }
    }
}

@Composable
private fun FilterLiveTestCard(
    liveInput: String,
    onInputChanged: (String) -> Unit,
    decision: CandyRuleDecision?,
    onTest: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FilterStudioTestTags.LiveTest),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.filter_test_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            OutlinedTextField(
                value = liveInput,
                onValueChange = onInputChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.filter_test_request)) },
                singleLine = true,
            )
            Button(
                onClick = onTest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.filter_test_action))
            }
            AnimatedContent(
                targetState = decision,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
                label = "Filter live decision",
            ) { currentDecision ->
                Text(
                    text = when (currentDecision?.action) {
                        CandyDecisionAction.Block -> stringResource(
                            R.string.filter_test_blocked_by,
                            currentDecision.rule.group,
                        )
                        CandyDecisionAction.Allow -> stringResource(
                            R.string.filter_test_allowed_by,
                            currentDecision.rule.group,
                        )
                        null -> stringResource(R.string.filter_test_no_match)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RuleTransferCard(onImport: () -> Unit, onExport: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.filter_transfer_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.filter_transfer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(FilterStudioTestTags.ImportOpen),
            ) {
                Text(stringResource(R.string.filter_import))
            }
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.filter_export))
            }
        }
    }
}

@Composable
private fun WebFilterListCard(onOpen: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FilterStudioTestTags.WebList),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.filter_subscription),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.filter_web_list_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.filter_web_list_action))
            }
        }
    }
}

@Composable
private fun FilterRuleCard(
    rule: CandyRule,
    profileLabel: String,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(stiffness = 620f),
        label = "Filter rule highlight",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(22.dp),
        color = color,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rule.displayTarget(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${rule.action.label()} · ${rule.scopeLabel()} · ${rule.group}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.filter_rule_origin_profile,
                        rule.origin.label(),
                        profileLabel,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.filter_hits, rule.hitCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                rule.sourceUrl?.let { source ->
                    Text(
                        source,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (rule.updatedAtMillis > 0L) {
                        Text(
                            stringResource(
                                R.string.filter_updated,
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(rule.updatedAtMillis)),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            val switchDescription = stringResource(
                if (rule.active) {
                    R.string.filter_rule_disable
                } else {
                    R.string.filter_rule_enable
                },
                rule.displayTarget(),
            )
            Switch(
                checked = rule.active,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = switchDescription },
            )
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    stringResource(R.string.filter_edit_rule_target, rule.displayTarget()),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    stringResource(R.string.filter_delete_rule_target, rule.displayTarget()),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFilterRuleDialog(
    initialRule: CandyRule?,
    profiles: List<BrowserProfile>,
    currentProfileId: String,
    onDismiss: () -> Unit,
    onAdd: (CandyRule) -> Boolean,
) {
    var action by remember(initialRule) {
        mutableStateOf(initialRule?.action ?: CandyRuleAction.Block)
    }
    var kind by remember(initialRule) {
        mutableStateOf(initialRule?.kind ?: CandyRuleKind.RequestHost)
    }
    var requestHost by remember(initialRule) { mutableStateOf(initialRule?.requestHost.orEmpty()) }
    var firstPartyHost by remember(initialRule) {
        mutableStateOf(initialRule?.firstPartyHost.orEmpty())
    }
    var selector by remember(initialRule) {
        mutableStateOf(initialRule?.cosmeticSelector.orEmpty())
    }
    var profileId by remember(initialRule) { mutableStateOf(initialRule?.profileId) }
    var invalid by remember { mutableStateOf(false) }
    val personalGroup = stringResource(R.string.filter_group_personal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialRule == null) R.string.filter_add_rule else R.string.filter_edit_rule,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(CandyRuleAction.Block, CandyRuleAction.Allow, CandyRuleAction.Cosmetic)
                        .forEach { value ->
                            FilterChip(
                                selected = action == value,
                                onClick = {
                                    action = value
                                    if (value == CandyRuleAction.Cosmetic) {
                                        kind = CandyRuleKind.CosmeticCss
                                    } else if (kind == CandyRuleKind.CosmeticCss) {
                                        kind = CandyRuleKind.RequestHost
                                    }
                                },
                                label = { Text(value.label()) },
                            )
                        }
                }
                if (invalid) {
                    Text(
                        stringResource(R.string.filter_invalid_rule),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (action != CandyRuleAction.Cosmetic) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(CandyRuleKind.RequestHost, CandyRuleKind.HostPair).forEach { value ->
                            FilterChip(
                                selected = kind == value,
                                onClick = { kind = value },
                                label = {
                                    Text(
                                        stringResource(
                                            if (value == CandyRuleKind.RequestHost) {
                                                R.string.filter_type_host
                                            } else {
                                                R.string.filter_type_pair
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = requestHost,
                        onValueChange = { requestHost = it },
                        label = { Text(stringResource(R.string.filter_request_host)) },
                        singleLine = true,
                    )
                }
                if (kind != CandyRuleKind.RequestHost) {
                    OutlinedTextField(
                        value = firstPartyHost,
                        onValueChange = { firstPartyHost = it },
                        label = { Text(stringResource(R.string.filter_first_party_host)) },
                        singleLine = true,
                    )
                }
                if (kind == CandyRuleKind.CosmeticCss) {
                    OutlinedTextField(
                        value = selector,
                        onValueChange = { selector = it },
                        label = { Text(stringResource(R.string.filter_css_selector)) },
                    )
                }
                Text(stringResource(R.string.filter_scope), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = profileId == null,
                        onClick = { profileId = null },
                        label = { Text(stringResource(R.string.filter_scope_global)) },
                    )
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = profileId == profile.id,
                            onClick = { profileId = profile.id },
                            label = { Text(profile.emoji) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    invalid = !onAdd(
                        (initialRule ?: CandyRule.new(
                            action = action,
                            kind = kind,
                            group = personalGroup,
                            origin = CandyRuleOrigin.User,
                        )).copy(
                            action = action,
                            kind = kind,
                            requestHost = requestHost.takeIf(String::isNotBlank),
                            firstPartyHost = firstPartyHost.takeIf(String::isNotBlank),
                            cosmeticSelector = selector.takeIf(String::isNotBlank),
                            profileId = profileId?.takeIf { id -> profiles.any { it.id == id } }
                                ?: profileId?.takeIf { it == currentProfileId },
                            updatedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                },
            ) {
                Text(
                    stringResource(
                        if (initialRule == null) R.string.action_add else R.string.action_done,
                    ),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterImportDialog(
    profiles: List<BrowserProfile>,
    currentProfileId: String,
    onDismiss: () -> Unit,
    onParse: (String) -> CandyRulePreview,
    onApply: (CandyRulePreview) -> Boolean,
) {
    var text by remember { mutableStateOf("") }
    var targetProfileId by remember { mutableStateOf<String?>(currentProfileId) }
    var skippedConfirmed by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<CandyRulePreview?>(null) }
    var loading by remember { mutableStateOf(false) }
    var applyFailed by remember { mutableStateOf(false) }
    var parseJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val scopedPreview = remember(preview, targetProfileId) {
        preview?.let { CandyImportScope.apply(it, targetProfileId) }
    }
    val canApply = scopedPreview?.let {
        it.isApplicable && (it.skippedCount == 0 || skippedConfirmed)
    } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_import_preview)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.filter_import_supported_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        parseJob?.cancel()
                        text = it
                        preview = null
                        skippedConfirmed = false
                        loading = false
                        applyFailed = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FilterStudioTestTags.ImportInput),
                    minLines = 8,
                    label = { Text(stringResource(R.string.filter_import_paste)) },
                )
                OutlinedButton(
                    onClick = {
                        val input = text
                        parseJob?.cancel()
                        loading = true
                        applyFailed = false
                        parseJob = scope.launch {
                            val parsed = withContext(Dispatchers.Default) { onParse(input) }
                            if (text == input) {
                                preview = parsed
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FilterStudioTestTags.ImportAnalyze),
                    enabled = text.isNotBlank() && !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.filter_analyzing_import))
                    } else {
                        Text(stringResource(R.string.filter_analyze_import))
                    }
                }
                scopedPreview?.let { currentPreview ->
                    Text(
                        stringResource(
                            R.string.filter_import_format,
                            currentPreview.format.label(),
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(
                            R.string.filter_preview_summary,
                            currentPreview.rules.size,
                            currentPreview.skippedCount,
                            currentPreview.errors.size,
                        ),
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        color = if (currentPreview.isApplicable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        stringResource(R.string.filter_import_scope),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = targetProfileId == null,
                            onClick = { targetProfileId = null },
                            modifier = Modifier.testTag(FilterStudioTestTags.ImportScopeGlobal),
                            label = { Text(stringResource(R.string.filter_scope_global)) },
                        )
                        profiles.forEach { profile ->
                            val profileDescription = stringResource(
                                R.string.filter_profile_description,
                                profile.emoji,
                            )
                            FilterChip(
                                selected = targetProfileId == profile.id,
                                onClick = { targetProfileId = profile.id },
                                modifier = Modifier.testTag(
                                    if (profile.id == currentProfileId) {
                                        FilterStudioTestTags.ImportScopeCurrent
                                    } else {
                                        "filter_studio_import_scope_${profile.id}"
                                    },
                                ).semantics {
                                    contentDescription = profileDescription
                                },
                                label = { Text(profile.emoji) },
                            )
                        }
                    }
                    currentPreview.errors.take(3).forEach { error ->
                        Text(
                            stringResource(
                                R.string.filter_error_line,
                                error.line,
                                filterErrorLabel(error.message),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    currentPreview.skipped.take(3).forEach { skipped ->
                        Text(
                            stringResource(
                                R.string.filter_skipped_line,
                                skipped.line,
                                filterErrorLabel(skipped.message),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (currentPreview.skippedCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = skippedConfirmed,
                                    role = Role.Checkbox,
                                    onValueChange = { skippedConfirmed = it },
                                )
                                .testTag(FilterStudioTestTags.ImportSkippedConfirm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = skippedConfirmed,
                                onCheckedChange = null,
                            )
                            Text(
                                stringResource(
                                    R.string.filter_confirm_skipped,
                                    currentPreview.skippedCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    currentPreview.rules.take(4).forEach { rule ->
                        Text(
                            "• ${rule.action.label()} · ${rule.scopeLabel()} · ${rule.displayTarget()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (currentPreview.rules.size > 4) {
                        Text(
                            stringResource(
                                R.string.filter_more_rules,
                                currentPreview.rules.size - 4,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (applyFailed) {
                        Text(
                            stringResource(R.string.filter_import_apply_failed),
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Assertive
                            },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    applyFailed = scopedPreview?.let(onApply) == false
                },
                modifier = Modifier.testTag(FilterStudioTestTags.ImportConfirm),
                enabled = canApply,
            ) {
                Text(stringResource(R.string.filter_apply_preview))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun FilterSubscriptionDialog(
    existingRules: List<CandyRule>,
    onDismiss: () -> Unit,
    onApply: (String, CandyRulePreview) -> Unit,
) {
    var source by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CandySubscriptionResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val preview = (result as? CandySubscriptionResult.Preview)?.preview
    val previous = existingRules.filter { it.sourceUrl == source }
    val diff = preview?.let { CandySubscriptionRules.diff(previous, it.rules) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_subscription)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.filter_subscription_warning))
                OutlinedTextField(
                    value = source,
                    onValueChange = {
                        source = it
                        result = null
                    },
                    label = { Text(stringResource(R.string.filter_source_url)) },
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = {
                        loading = true
                        scope.launch {
                            result = withContext(Dispatchers.IO) { CandySubscriptionClient.fetch(source) }
                            loading = false
                        }
                    },
                    enabled = !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.filter_fetching_preview),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else {
                        Text(stringResource(R.string.filter_fetch_preview))
                    }
                }
                diff?.let { SubscriptionDiffText(it) }
                (result as? CandySubscriptionResult.Error)?.let { error ->
                    Text(filterErrorLabel(error.reason), color = MaterialTheme.colorScheme.error)
                }
                preview?.errors?.take(3)?.forEach { error ->
                    Text(
                        stringResource(
                            R.string.filter_error_line,
                            error.line,
                            filterErrorLabel(error.message),
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { preview?.let { onApply(source, it) } },
                enabled = preview?.isApplicable == true,
            ) { Text(stringResource(R.string.filter_confirm_update)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SubscriptionDiffText(diff: CandySubscriptionDiff) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(
                R.string.filter_diff_summary,
                diff.added.size,
                diff.removed.size,
                diff.unchanged.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        diff.added.take(4).forEach { rule ->
            Text("+ ${rule.displayTarget()}", style = MaterialTheme.typography.bodySmall)
        }
        diff.removed.take(4).forEach { rule ->
            Text("− ${rule.displayTarget()}", style = MaterialTheme.typography.bodySmall)
        }
        val hidden = (diff.added.size - 4).coerceAtLeast(0) +
            (diff.removed.size - 4).coerceAtLeast(0)
        if (hidden > 0) {
            Text(
                stringResource(R.string.filter_more_changes, hidden),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextPreviewDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 10,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
private fun StudioTypeFilter.label(): String = stringResource(
    when (this) {
        StudioTypeFilter.All -> R.string.filter_all
        StudioTypeFilter.Block -> R.string.filter_block
        StudioTypeFilter.Allow -> R.string.filter_allow
        StudioTypeFilter.Css -> R.string.filter_css
    },
)

@Composable
private fun CandyImportFormat.label(): String = stringResource(
    when (this) {
        CandyImportFormat.CandyRulesV1 -> R.string.filter_import_format_candy
        CandyImportFormat.AdblockCompatible -> R.string.filter_import_format_adblock
    },
)

@Composable
private fun CandyRuleAction.label(): String = stringResource(
    when (this) {
        CandyRuleAction.Block -> R.string.filter_block
        CandyRuleAction.Allow -> R.string.filter_allow
        CandyRuleAction.Cosmetic -> R.string.filter_css
    },
)

@Composable
private fun CandyRule.scopeLabel(): String = if (profileId == null) {
    stringResource(R.string.filter_scope_global)
} else {
    stringResource(R.string.filter_scope_profile)
}

@Composable
private fun CandyRuleOrigin.label(): String = stringResource(
    when (this) {
        CandyRuleOrigin.User -> R.string.filter_origin_user
        CandyRuleOrigin.PrivacyXRay -> R.string.filter_origin_xray
        CandyRuleOrigin.Import -> R.string.filter_origin_import
        CandyRuleOrigin.Subscription -> R.string.filter_origin_subscription
    },
)

@Composable
private fun filterErrorLabel(code: String): String = stringResource(
    when {
        code == "https-required" || code == "invalid-url" -> R.string.filter_error_https
        code == "network" || code.startsWith("http-") -> R.string.filter_error_network
        code in setOf("size-limit", "line-limit", "rule-limit") -> R.string.filter_error_limit
        code == "subscription-css-forbidden" -> R.string.filter_error_subscription_css
        code == "missing-header" -> R.string.filter_error_header
        code == "no-supported-rules" -> R.string.filter_error_no_supported_rules
        code == "unsupported-adblock" -> R.string.filter_error_adblock_unsupported
        else -> R.string.filter_error_invalid
    },
)

private fun CandyRule.displayTarget(): String = when (kind) {
    CandyRuleKind.RequestHost -> requestHost.orEmpty()
    CandyRuleKind.HostPair -> "${firstPartyHost.orEmpty()} → ${requestHost.orEmpty()}"
    CandyRuleKind.CosmeticCss -> "${firstPartyHost.orEmpty()} · ${cosmeticSelector.orEmpty()}"
}
