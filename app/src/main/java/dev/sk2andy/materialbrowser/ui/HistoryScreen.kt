package dev.sk2andy.materialbrowser.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.data.BrowsingHistoryRules
import dev.sk2andy.materialbrowser.data.HistoryClearRequest
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import dev.sk2andy.materialbrowser.data.HistoryRecallRules
import dev.sk2andy.materialbrowser.recall.RecallMatch
import dev.sk2andy.materialbrowser.recall.RecallRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HistoryScreen(
    profiles: List<BrowserProfile>,
    activeProfileId: String,
    history: List<HistoryEntry>,
    recallMatches: List<RecallMatch> = emptyList(),
    recordingMode: HistoryRecordingMode,
    onRecallCriteriaChanged: (String, Set<String>) -> Unit = { _, _ -> },
    onRecordingModeChange: (HistoryRecordingMode) -> Unit,
    onDeleteEntries: (List<HistoryEntry>) -> Unit,
    onClearHistory: (HistoryClearRequest) -> Unit,
    onOpenEntry: (HistoryEntry) -> Unit,
    onBack: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val zoneId = remember { ZoneId.systemDefault() }
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val timeFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }
    val profileIds = remember(profiles) { profiles.map(BrowserProfile::id) }
    var selectedProfileIds by rememberSaveable(profiles) {
        mutableStateOf(arrayListOf(activeProfileId))
    }
    var selectedEntryKeys by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var clearConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    val selectedProfiles = selectedProfileIds.toSet()
    LaunchedEffect(query, selectedProfiles) {
        onRecallCriteriaChanged(query, selectedProfiles)
    }
    val recallSnapshot = remember(history, selectedProfileIds, query, recallMatches) {
        HistoryRecallRules.merge(history, selectedProfiles, query, recallMatches)
    }
    val visibleEntries = recallSnapshot.entries
    val clearableHistory = remember(history, recallMatches) {
        (history + recallMatches.map { match ->
            HistoryEntry(
                url = match.url,
                title = match.title,
                lastVisitedAt = match.visitedAt,
                profileId = match.profileId,
            )
        }).distinctBy(BrowsingHistoryRules::entryKey)
    }
    val sections = remember(visibleEntries, zoneId) {
        BrowsingHistoryRules.sections(visibleEntries, zoneId)
    }
    val selectedEntries = remember(visibleEntries, selectedEntryKeys) {
        val keys = selectedEntryKeys.toSet()
        visibleEntries.filter { entry -> BrowsingHistoryRules.entryKey(entry) in keys }
    }
    val today = remember { LocalDate.now(zoneId) }

    fun handleBack() {
        when {
            selectedEntries.isNotEmpty() -> selectedEntryKeys = arrayListOf()
            searchVisible -> {
                searchVisible = false
                query = ""
            }
            else -> onBack()
        }
    }
    BackHandler(onBack = ::handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedEntries.isEmpty()) {
                            stringResource(R.string.history_title)
                        } else {
                            stringResource(R.string.history_selected_count, selectedEntries.size)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = ::handleBack,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.history_back),
                        )
                    }
                },
                actions = {
                    if (selectedEntries.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onDeleteEntries(selectedEntries)
                                selectedEntryKeys = arrayListOf()
                            },
                            modifier = Modifier.testTag(HistoryScreenTestTags.DeleteSelected),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.history_delete_selected),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                searchVisible = !searchVisible
                                if (!searchVisible) query = ""
                            },
                            modifier = Modifier.testTag(HistoryScreenTestTags.Search),
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.history_search),
                            )
                        }
                        TextButton(
                            onClick = { clearConfirmationVisible = true },
                            enabled = clearableHistory.any { entry ->
                                entry.profileId in selectedProfiles
                            },
                            modifier = Modifier.testTag(HistoryScreenTestTags.Clear),
                        ) {
                            Text(stringResource(R.string.history_clear))
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .navigationBarsPadding()
                .testTag(HistoryScreenTestTags.List),
        ) {
            if (searchVisible) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(RecallRules.MAX_QUERY_CHARS) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag(HistoryScreenTestTags.SearchField),
                        singleLine = true,
                        label = { Text(stringResource(R.string.history_search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(
                                            R.string.history_clear_search,
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            item(key = "controls") {
                HistoryRecordingControls(
                    recordingMode = recordingMode,
                    onRecordingModeChange = onRecordingModeChange,
                )
            }

            if (profiles.size > 1) {
                item(key = "profiles") {
                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                        Text(
                            text = stringResource(R.string.history_profiles),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedProfileIds.size == profileIds.size,
                                onClick = {
                                    selectedProfileIds = ArrayList(profileIds)
                                    selectedEntryKeys = arrayListOf()
                                },
                                label = { Text(stringResource(R.string.history_all_profiles)) },
                                modifier = Modifier.testTag(HistoryScreenTestTags.AllProfiles),
                            )
                            profiles.forEach { profile ->
                                val selected = profile.id in selectedProfileIds
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val updated = ArrayList(selectedProfileIds)
                                        if (selected) {
                                            if (updated.size > 1) updated.remove(profile.id)
                                        } else {
                                            updated.add(profile.id)
                                        }
                                        selectedProfileIds = updated
                                        selectedEntryKeys = arrayListOf()
                                    },
                                    label = { Text(profile.emoji) },
                                    modifier = Modifier.testTag(
                                        HistoryScreenTestTags.profile(profile.id),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            if (sections.isEmpty()) {
                item(key = "empty") {
                    HistoryEmptyState(
                        searching = query.isNotBlank(),
                        modifier = Modifier.fillParentMaxHeight(0.55f),
                    )
                }
            } else {
                sections.forEach { section ->
                    item(key = "day:${section.date}") {
                        Text(
                            text = when (section.date) {
                                today -> stringResource(R.string.history_today)
                                today.minusDays(1) -> stringResource(R.string.history_yesterday)
                                else -> dateFormatter.format(section.date)
                            },
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 20.dp,
                                bottom = 8.dp,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(
                        items = section.entries,
                        key = BrowsingHistoryRules::entryKey,
                    ) { entry ->
                        val entryKey = BrowsingHistoryRules.entryKey(entry)
                        val profileEmoji = profiles.firstOrNull { profile ->
                            profile.id == entry.profileId
                        }?.emoji
                        HistoryEntryRow(
                            entry = entry,
                            time = timeFormatter.format(
                                Instant.ofEpochMilli(entry.lastVisitedAt).atZone(zoneId),
                            ),
                            profileEmoji = profileEmoji.takeIf { selectedProfiles.size > 1 },
                            excerpt = recallSnapshot.excerptsByEntryKey[entryKey],
                            selected = entryKey in selectedEntryKeys,
                            onSelectedChange = { selected ->
                                val updated = ArrayList(selectedEntryKeys)
                                if (selected) updated.add(entryKey) else updated.remove(entryKey)
                                selectedEntryKeys = updated
                            },
                            onOpen = { onOpenEntry(entry) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }

    if (clearConfirmationVisible) {
        HistoryClearDialog(
            profiles = profiles,
            initialProfileIds = selectedProfiles,
            history = clearableHistory,
            zoneId = zoneId,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            onDismiss = { clearConfirmationVisible = false },
            onConfirm = { request ->
                onClearHistory(request)
                selectedEntryKeys = arrayListOf()
                clearConfirmationVisible = false
            },
        )
    }
}

internal enum class HistoryClearDateField { Since, Until }

internal data class HistoryClearDateTimeRange(
    val since: LocalDateTime,
    val until: LocalDateTime,
)

internal object HistoryClearDialogRules {
    fun updateMoment(
        range: HistoryClearDateTimeRange,
        field: HistoryClearDateField,
        selectedMoment: LocalDateTime,
        zoneId: ZoneId,
    ): HistoryClearDateTimeRange {
        val normalizedMoment = selectedMoment.atZone(zoneId).toLocalDateTime()
        return when (field) {
            HistoryClearDateField.Since -> HistoryClearDateTimeRange(
                since = normalizedMoment,
                until = maxOf(range.until, normalizedMoment),
            )
            HistoryClearDateField.Until -> HistoryClearDateTimeRange(
                since = minOf(range.since, normalizedMoment),
                until = normalizedMoment,
            )
        }
    }

    fun clearRequest(
        range: HistoryClearDateTimeRange,
        profileIds: Set<String>,
        zoneId: ZoneId,
    ): HistoryClearRequest {
        val normalizedSince = range.since.atZone(zoneId).withEarlierOffsetAtOverlap()
        val normalizedUntil = range.until.atZone(zoneId).withLaterOffsetAtOverlap()
        return HistoryClearRequest(
            profileIds = profileIds,
            sinceInclusiveMillis = normalizedSince.toInstant().toEpochMilli(),
            untilExclusiveMillis = normalizedUntil.plusMinutes(1).toInstant().toEpochMilli(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HistoryClearDialog(
    profiles: List<BrowserProfile>,
    initialProfileIds: Set<String>,
    history: List<HistoryEntry>,
    zoneId: ZoneId,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onDismiss: () -> Unit,
    onConfirm: (HistoryClearRequest) -> Unit,
) {
    val now = remember(zoneId) {
        LocalDateTime.now(zoneId).withSecond(0).withNano(0)
    }
    val historyMoments = remember(history, zoneId) {
        history.map { entry ->
            Instant.ofEpochMilli(entry.lastVisitedAt)
                .atZone(zoneId)
                .toLocalDateTime()
                .withSecond(0)
                .withNano(0)
        }
    }
    val availableProfileIds = remember(profiles) { profiles.mapTo(linkedSetOf(), BrowserProfile::id) }
    val initialSince = historyMoments.minOrNull() ?: now
    val initialUntil = historyMoments.maxOrNull() ?: now
    var selectedProfileIdList by rememberSaveable {
        mutableStateOf(ArrayList(initialProfileIds.intersect(availableProfileIds)))
    }
    var sinceEpochDay by rememberSaveable {
        mutableStateOf(initialSince.toLocalDate().toEpochDay())
    }
    var sinceMinuteOfDay by rememberSaveable {
        mutableStateOf(initialSince.toLocalTime().toSecondOfDay() / 60)
    }
    var untilEpochDay by rememberSaveable {
        mutableStateOf(initialUntil.toLocalDate().toEpochDay())
    }
    var untilMinuteOfDay by rememberSaveable {
        mutableStateOf(initialUntil.toLocalTime().toSecondOfDay() / 60)
    }
    var editedFieldName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedProfileIds = selectedProfileIdList.toSet()
    val since = LocalDate.ofEpochDay(sinceEpochDay)
        .atStartOfDay()
        .plusMinutes(sinceMinuteOfDay.toLong())
    val until = LocalDate.ofEpochDay(untilEpochDay)
        .atStartOfDay()
        .plusMinutes(untilMinuteOfDay.toLong())
    val editedField = editedFieldName?.let(HistoryClearDateField::valueOf)
    val request = remember(selectedProfileIds, since, until, zoneId) {
        HistoryClearDialogRules.clearRequest(
            range = HistoryClearDateTimeRange(since, until),
            profileIds = selectedProfileIds,
            zoneId = zoneId,
        )
    }
    val hasMatchingEntries = remember(history, request) {
        BrowsingHistoryRules.removeRange(history, request).size < history.size
    }

    editedField?.let { field ->
        HistoryBoundaryDateTimeDialogs(
            field = field,
            initialMoment = if (field == HistoryClearDateField.Since) since else until,
            onDismiss = { editedFieldName = null },
            onConfirm = { selectedMoment ->
                val range = HistoryClearDialogRules.updateMoment(
                    range = HistoryClearDateTimeRange(since, until),
                    field = field,
                    selectedMoment = selectedMoment,
                    zoneId = zoneId,
                )
                sinceEpochDay = range.since.toLocalDate().toEpochDay()
                sinceMinuteOfDay = range.since.toLocalTime().toSecondOfDay() / 60
                untilEpochDay = range.until.toLocalDate().toEpochDay()
                untilMinuteOfDay = range.until.toLocalTime().toSecondOfDay() / 60
                editedFieldName = null
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(HistoryScreenTestTags.ClearDialog),
        title = { Text(stringResource(R.string.history_clear_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.history_clear_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.history_clear_range),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryClearDateButton(
                        label = stringResource(R.string.history_clear_since),
                        date = dateFormatter.format(since),
                        time = timeFormatter.format(since),
                        onClick = { editedFieldName = HistoryClearDateField.Since.name },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(HistoryScreenTestTags.ClearSince),
                    )
                    HistoryClearDateButton(
                        label = stringResource(R.string.history_clear_until),
                        date = dateFormatter.format(until),
                        time = timeFormatter.format(until),
                        onClick = { editedFieldName = HistoryClearDateField.Until.name },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(HistoryScreenTestTags.ClearUntil),
                    )
                }
                Text(
                    stringResource(R.string.history_profiles),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (profiles.size > 1) {
                        FilterChip(
                            selected = selectedProfileIds.size == profiles.size,
                            onClick = { selectedProfileIdList = ArrayList(availableProfileIds) },
                            label = { Text(stringResource(R.string.history_all_profiles)) },
                            modifier = Modifier.testTag(HistoryScreenTestTags.ClearAllProfiles),
                        )
                    }
                    profiles.forEach { profile ->
                        val selected = profile.id in selectedProfileIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val updated = if (selected) {
                                    if (selectedProfileIds.size > 1) {
                                        selectedProfileIds - profile.id
                                    } else {
                                        selectedProfileIds
                                    }
                                } else {
                                    selectedProfileIds + profile.id
                                }
                                selectedProfileIdList = ArrayList(updated)
                            },
                            label = { Text(profile.emoji) },
                            modifier = Modifier.testTag(
                                HistoryScreenTestTags.clearProfile(profile.id),
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(request) },
                enabled = hasMatchingEntries,
                modifier = Modifier.testTag(HistoryScreenTestTags.ClearConfirm),
            ) {
                Text(stringResource(R.string.history_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun HistoryClearDateButton(
    label: String,
    date: String,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(date, style = MaterialTheme.typography.bodyMedium)
            Text(time, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private enum class HistoryClearDateTimeStep { Date, Time }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryBoundaryDateTimeDialogs(
    field: HistoryClearDateField,
    initialMoment: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    val context = LocalContext.current
    var stepName by rememberSaveable(field) {
        mutableStateOf(HistoryClearDateTimeStep.Date.name)
    }
    var selectedDateEpochDay by rememberSaveable(field) {
        mutableStateOf(initialMoment.toLocalDate().toEpochDay())
    }
    when (HistoryClearDateTimeStep.valueOf(stepName)) {
        HistoryClearDateTimeStep.Date -> {
            val initialDateMillis = remember(initialMoment) {
                initialMoment.toLocalDate()
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }
            val state = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedMillis = state.selectedDateMillis ?: return@TextButton
                            selectedDateEpochDay = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay()
                            stepName = HistoryClearDateTimeStep.Time.name
                        },
                    ) {
                        Text(stringResource(R.string.action_done))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            ) {
                DatePicker(
                    state = state,
                    modifier = Modifier.testTag(HistoryScreenTestTags.ClearDatePicker),
                    title = {
                        Text(
                            historyClearBoundaryLabel(field),
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        )
                    },
                )
            }
        }
        HistoryClearDateTimeStep.Time -> {
            val state = rememberTimePickerState(
                initialHour = initialMoment.hour,
                initialMinute = initialMoment.minute,
                is24Hour = DateFormat.is24HourFormat(context),
            )
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(historyClearBoundaryLabel(field)) },
                text = {
                    TimePicker(
                        state = state,
                        modifier = Modifier.testTag(HistoryScreenTestTags.ClearTimePicker),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onConfirm(
                                LocalDate.ofEpochDay(selectedDateEpochDay).atTime(
                                    LocalTime.of(state.hour, state.minute),
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.action_done))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun historyClearBoundaryLabel(field: HistoryClearDateField): String = stringResource(
    if (field == HistoryClearDateField.Since) {
        R.string.history_clear_since
    } else {
        R.string.history_clear_until
    },
)

@Composable
private fun HistoryRecordingControls(
    recordingMode: HistoryRecordingMode,
    onRecordingModeChange: (HistoryRecordingMode) -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            HistoryControlRow(
                title = stringResource(R.string.history_save_title),
                summary = stringResource(R.string.history_save_summary),
                checked = recordingMode != HistoryRecordingMode.Disabled,
                enabled = true,
                onCheckedChange = { enabled ->
                    onRecordingModeChange(
                        if (enabled) HistoryRecordingMode.Enabled else HistoryRecordingMode.Disabled,
                    )
                },
                modifier = Modifier.testTag(HistoryScreenTestTags.SaveHistory),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            HistoryControlRow(
                title = stringResource(R.string.history_clear_on_exit_title),
                summary = stringResource(R.string.history_clear_on_exit_summary),
                checked = recordingMode == HistoryRecordingMode.ClearOnExit,
                enabled = recordingMode != HistoryRecordingMode.Disabled,
                onCheckedChange = { enabled ->
                    onRecordingModeChange(
                        if (enabled) {
                            HistoryRecordingMode.ClearOnExit
                        } else {
                            HistoryRecordingMode.Enabled
                        },
                    )
                },
                modifier = Modifier.testTag(HistoryScreenTestTags.ClearOnExit),
            )
        }
    }
}

@Composable
private fun HistoryControlRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun HistoryEntryRow(
    entry: HistoryEntry,
    time: String,
    profileEmoji: String?,
    excerpt: String?,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = entry.title.ifBlank { BrowserUriPolicy.displayHttpHost(entry.url) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = BrowserUriPolicy.displayHttpHost(entry.url),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                excerpt?.takeIf(String::isNotBlank)?.let { value ->
                    Text(
                        text = value,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        leadingContent = {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                profileEmoji?.let { emoji ->
                    Text(emoji, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier
            .clickable(onClick = onOpen)
            .testTag(HistoryScreenTestTags.entry(entry)),
    )
}

@Composable
private fun HistoryEmptyState(
    searching: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (searching) R.string.history_no_results else R.string.history_empty,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal object HistoryScreenTestTags {
    const val List = "history_list"
    const val Search = "history_search"
    const val SearchField = "history_search_field"
    const val Clear = "history_clear"
    const val ClearDialog = "history_clear_dialog"
    const val ClearSince = "history_clear_since"
    const val ClearUntil = "history_clear_until"
    const val ClearDatePicker = "history_clear_date_picker"
    const val ClearTimePicker = "history_clear_time_picker"
    const val ClearAllProfiles = "history_clear_all_profiles"
    const val ClearConfirm = "history_clear_confirm"
    const val DeleteSelected = "history_delete_selected"
    const val AllProfiles = "history_all_profiles"
    const val SaveHistory = "history_save"
    const val ClearOnExit = "history_clear_on_exit"

    fun profile(profileId: String): String = "history_profile:$profileId"

    fun clearProfile(profileId: String): String = "history_clear_profile:$profileId"

    fun entry(entry: HistoryEntry): String = "history_entry:${entry.profileId}:${entry.url}"
}
