@file:OptIn(ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BLANK_URL
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
    onSnooze: (Long) -> Boolean,
    onDismiss: () -> Unit,
) {
    if (tab == null) return
    val zoneId = remember { ZoneId.systemDefault() }
    var customEditorVisible by remember(tab.id) { mutableStateOf(false) }
    val customInitialMillis = remember(tab.id) {
        System.currentTimeMillis() + 24 * 60 * 60 * 1_000L
    }
    val enabled = !tab.isIncognito
    val applyPreset: (SnoozePreset) -> Unit = { preset ->
        val nowMillis = System.currentTimeMillis()
        if (onSnooze(SnoozeTimeRules.wakeAtMillis(preset, nowMillis, zoneId))) onDismiss()
    }

    if (!customEditorVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag(SnoozeTestTags.Dialog),
            title = {
                Column {
                    Text(
                        stringResource(R.string.snooze_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        snoozeDisplayTitle(tab),
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (!enabled) {
                        Text(
                            stringResource(R.string.snooze_unavailable_private),
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SnoozeTestTags.PresetGroup),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SnoozePresetButton(
                            text = stringResource(R.string.snooze_later_today),
                            enabled = enabled,
                            tag = SnoozeTestTags.LaterToday,
                            onClick = { applyPreset(SnoozePreset.LaterToday) },
                            modifier = Modifier.weight(1f),
                        )
                        SnoozePresetButton(
                            text = stringResource(R.string.snooze_tomorrow),
                            enabled = enabled,
                            tag = SnoozeTestTags.Tomorrow,
                            onClick = { applyPreset(SnoozePreset.Tomorrow) },
                            modifier = Modifier.weight(1f),
                        )
                        SnoozePresetButton(
                            text = stringResource(R.string.snooze_next_week),
                            enabled = enabled,
                            tag = SnoozeTestTags.NextWeek,
                            onClick = { applyPreset(SnoozePreset.NextWeek) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    SnoozeChoice(
                        text = stringResource(R.string.snooze_custom),
                        enabled = enabled,
                        tag = SnoozeTestTags.Custom,
                        onClick = { customEditorVisible = true },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    SnoozeDateTimeDialogs(
        visible = customEditorVisible,
        initialMillis = customInitialMillis,
        onDismiss = { customEditorVisible = false },
        onConfirm = { wakeAtMillis ->
            onSnooze(wakeAtMillis).also { accepted ->
                if (accepted) {
                    customEditorVisible = false
                    onDismiss()
                }
            }
        },
    )
}

@Composable
private fun SnoozePresetButton(
    text: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(64.dp)
            .testTag(tag),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SnoozeChoice(
    text: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(tag),
        enabled = enabled,
    ) {
        Text(text)
    }
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
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(SnoozeTestTags.Management),
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
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Text(
                    stringResource(R.string.snoozed_tabs_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (snoozedTabs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.snoozed_tabs_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.snoozed_tabs_empty_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(snoozedTabs, key = { it.tab.id }) { snoozed ->
                        val profileEmoji = profiles.firstOrNull {
                            it.id == snoozed.tab.profileId
                        }?.emoji.orEmpty()
                        SnoozedTabCard(
                            snoozed = snoozed,
                            profileEmoji = profileEmoji,
                            onReschedule = { editingTab = snoozed },
                            onOpenNow = {
                                if (onOpenNow(snoozed.tab.id)) onBack()
                            },
                            onDelete = { onDelete(snoozed.tab.id) },
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
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

@Composable
private fun SnoozedTabCard(
    snoozed: SnoozedTab,
    profileEmoji: String,
    onReschedule: () -> Unit,
    onOpenNow: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SnoozeTestTags.card(snoozed.tab.id)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profileEmoji.isNotEmpty()) {
                    Text(profileEmoji, modifier = Modifier.padding(end = 10.dp))
                }
                Text(
                    snoozeDisplayTitle(snoozed.tab),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (snoozed.tab.url != BLANK_URL) {
                Text(
                    snoozed.tab.url,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(
                    R.string.snoozed_until,
                    remember(snoozed.wakeAtMillis) {
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(snoozed.wakeAtMillis))
                    },
                ),
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReschedule) {
                    Text(stringResource(R.string.action_reschedule))
                }
                TextButton(onClick = onOpenNow) {
                    Text(stringResource(R.string.action_open_now))
                }
                TextButton(onClick = onDelete) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
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
private fun snoozeDisplayTitle(tab: BrowserTab): String =
    if (tab.url == BLANK_URL || tab.title.isBlank()) {
        stringResource(R.string.new_tab_title)
    } else {
        tab.title
    }

internal object SnoozeTestTags {
    const val Dialog = "snooze_dialog"
    const val PresetGroup = "snooze_preset_group"
    const val LaterToday = "snooze_later_today"
    const val Tomorrow = "snooze_tomorrow"
    const val NextWeek = "snooze_next_week"
    const val Custom = "snooze_custom"
    const val TabActions = "tab_actions"
    const val TabActionsSnooze = "tab_actions_snooze"
    const val Management = "snoozed_tabs_management"
    fun overviewTab(tabId: String) = "overview_tab:$tabId"
    fun card(tabId: String) = "snoozed_tab:$tabId"
}
