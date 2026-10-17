@file:OptIn(ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.SnoozePreset
import dev.sk2andy.materialbrowser.data.SnoozeTimeRules
import dev.sk2andy.materialbrowser.data.SnoozedTab
import java.text.DateFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date

internal suspend fun showSnoozeUndoFeedback(
    hostState: SnackbarHostState,
    message: String,
    undoLabel: String,
    onUndo: () -> Unit,
) {
    val result = hostState.showSnackbar(
        message = message,
        actionLabel = undoLabel,
        withDismissAction = true,
        duration = SnackbarDuration.Short,
    )
    if (result == SnackbarResult.ActionPerformed) onUndo()
}

@Composable
internal fun SnoozeTabDialog(
    tab: BrowserTab?,
    onSnooze: (Long, String) -> Boolean,
    onDismiss: () -> Unit,
) {
    if (tab == null) return
    val zoneId = remember { ZoneId.systemDefault() }
    var customEditorVisible by remember(tab.id) { mutableStateOf(false) }
    var purpose by remember(tab.id) { mutableStateOf(tab.snoozePurpose) }
    val customInitialMillis = remember(tab.id) {
        System.currentTimeMillis() + 24 * 60 * 60 * 1_000L
    }
    val applyPreset: (SnoozePreset) -> Unit = { preset ->
        val nowMillis = System.currentTimeMillis()
        if (onSnooze(SnoozeTimeRules.wakeAtMillis(preset, nowMillis, zoneId), purpose)) onDismiss()
    }

    if (!customEditorVisible) {
        SharedSnoozeTabDialog(
            tab = tab,
            onPreset = applyPreset,
            onCustom = { customEditorVisible = true },
            onDismiss = onDismiss,
            strings = androidSnoozeStrings(),
        )
    }

    SnoozeDateTimeDialogs(
        visible = customEditorVisible,
        initialMillis = customInitialMillis,
        onDismiss = { customEditorVisible = false },
        onConfirm = { wakeAtMillis ->
            onSnooze(wakeAtMillis, purpose).also { accepted ->
                if (accepted) {
                    customEditorVisible = false
                    onDismiss()
                }
            }
        },
    )
}

@Composable
internal fun SnoozedTabsScreen(
    snoozedTabs: List<SnoozedTab>,
    profiles: List<BrowserProfile>,
    onBack: () -> Unit,
    onReschedule: (String, Long) -> Boolean,
    onOpenNow: (String) -> Boolean,
    onDelete: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var editingTab by remember { mutableStateOf<SnoozedTab?>(null) }
    val backState = rememberNativeScreenBackState()
    NativeScreenBackHandler(state = backState, retainProgressOnBack = true, onBack = onBack)
    Surface(
        modifier = modifier
            .predictiveBackSurface(backState.progress.value, backState.swipeEdgeSign)
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        SharedSnoozedTabsContent(
            snoozedTabs = snoozedTabs,
            profiles = profiles,
            wakeLabel = { item -> stringResource(
                R.string.snoozed_until,
                remember(item.wakeAtMillis) {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(item.wakeAtMillis))
                },
            ) },
            onBack = onBack,
            onReschedule = { id -> editingTab = snoozedTabs.firstOrNull { it.tab.id == id } },
            onOpenNow = { id -> if (onOpenNow(id)) onBack() },
            onDelete = { onDelete(it) },
            modifier = Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
            strings = androidSnoozeStrings(),
            header = {
                Row(
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                    Text(
                        stringResource(R.string.snoozed_tabs_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }

    val editing = editingTab
    SnoozeDateTimeDialogs(
        visible = editing != null,
        initialMillis = editing?.wakeAtMillis ?: System.currentTimeMillis(),
        onDismiss = { editingTab = null },
        onConfirm = { wakeAtMillis ->
            val accepted = editing != null && onReschedule(editing.tab.id, wakeAtMillis)
            if (accepted) editingTab = null
            accepted
        },
    )
}

private enum class SnoozeDateTimeStep { Date, Time }

@Composable
private fun SnoozeDateTimeDialogs(
    visible: Boolean,
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Boolean,
) {
    if (!visible) return
    val zoneId = remember { ZoneId.systemDefault() }
    val initialLocal = remember(initialMillis, zoneId) {
        Instant.ofEpochMilli(initialMillis).atZone(zoneId)
    }
    var step by remember(initialMillis) { mutableStateOf(SnoozeDateTimeStep.Date) }
    var selectedDate by remember(initialMillis) { mutableStateOf(initialLocal.toLocalDate()) }
    var invalidTime by remember(initialMillis) { mutableStateOf(false) }

    when (step) {
        SnoozeDateTimeStep.Date -> {
            val initialDateMillis = remember(initialLocal) {
                initialLocal.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
            val dateState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedMillis = dateState.selectedDateMillis ?: return@TextButton
                            selectedDate = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            step = SnoozeDateTimeStep.Time
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
                    state = dateState,
                    title = {
                        Text(
                            stringResource(R.string.snooze_select_date),
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        )
                    },
                )
            }
        }
        SnoozeDateTimeStep.Time -> {
            val timeState = rememberTimePickerState(
                initialHour = initialLocal.hour,
                initialMinute = initialLocal.minute,
                is24Hour = true,
            )
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.snooze_select_time)) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TimePicker(state = timeState)
                        if (invalidTime) {
                            Text(
                                stringResource(R.string.snooze_invalid_time),
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val wakeAtMillis = SnoozeTimeRules.customWakeAtMillis(
                                selectedDate,
                                LocalTime.of(timeState.hour, timeState.minute),
                                zoneId,
                            )
                            invalidTime = wakeAtMillis <= System.currentTimeMillis() ||
                                !onConfirm(wakeAtMillis)
                        },
                    ) {
                        Text(stringResource(R.string.action_save))
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
private fun androidSnoozeStrings(): SnoozeScreenStrings = SnoozeScreenStrings(
    title = stringResource(R.string.snooze_sheet_title),
    newTabTitle = stringResource(R.string.new_tab_title),
    privateUnavailable = stringResource(R.string.snooze_unavailable_private),
    laterToday = stringResource(R.string.snooze_later_today),
    tomorrow = stringResource(R.string.snooze_tomorrow),
    nextWeek = stringResource(R.string.snooze_next_week),
    custom = stringResource(R.string.snooze_custom),
    cancel = stringResource(R.string.action_cancel),
    back = stringResource(R.string.action_back),
    managementTitle = stringResource(R.string.snoozed_tabs_title),
    emptyTitle = stringResource(R.string.snoozed_tabs_empty_title),
    emptyBody = stringResource(R.string.snoozed_tabs_empty_body),
    reschedule = stringResource(R.string.action_reschedule),
    openNow = stringResource(R.string.action_open_now),
    delete = stringResource(R.string.action_delete),
)
