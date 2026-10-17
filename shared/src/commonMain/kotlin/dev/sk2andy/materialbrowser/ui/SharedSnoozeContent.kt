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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.SnoozePreset
import dev.sk2andy.materialbrowser.data.SnoozedTab
import dev.sk2andy.materialbrowser.shared.ui.CandyActionButton
import dev.sk2andy.materialbrowser.shared.ui.CandyAlertDialog
import dev.sk2andy.materialbrowser.shared.ui.CandyPageHeader
import dev.sk2andy.materialbrowser.shared.ui.PlatformProfileEmoji

data class SnoozeScreenStrings(
    val title: String = "Tab schlummern lassen",
    val newTabTitle: String = "Neuer Tab",
    val privateUnavailable: String = "Private Tabs können nicht gespeichert werden.",
    val laterToday: String = "Später heute",
    val tomorrow: String = "Morgen",
    val nextWeek: String = "Nächste Woche",
    val custom: String = "Zeitpunkt wählen",
    val cancel: String = "Abbrechen",
    val back: String = "Zurück",
    val managementTitle: String = "Schlummernde Tabs",
    val emptyTitle: String = "Keine schlummernden Tabs",
    val emptyBody: String = "Schlummernde Tabs erscheinen hier bis zu ihrer Rückkehr.",
    val reschedule: String = "Verschieben",
    val openNow: String = "Jetzt öffnen",
    val delete: String = "Löschen",
)

@Composable
fun SharedSnoozeTabDialog(
    tab: BrowserTab?,
    onPreset: (SnoozePreset) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
    strings: SnoozeScreenStrings = SnoozeScreenStrings(),
) {
    if (tab == null) return
    val enabled = !tab.isIncognito
    CandyAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(strings.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    snoozeDisplayTitle(tab, strings.newTabTitle),
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag(SnoozeTestTags.Dialog)) {
                if (!enabled) Text(strings.privateUnavailable, color = MaterialTheme.colorScheme.error)
                Row(
                    modifier = Modifier.fillMaxWidth().testTag(SnoozeTestTags.PresetGroup),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                        Triple(SnoozePreset.LaterToday, strings.laterToday, SnoozeTestTags.LaterToday),
                        Triple(SnoozePreset.Tomorrow, strings.tomorrow, SnoozeTestTags.Tomorrow),
                        Triple(SnoozePreset.NextWeek, strings.nextWeek, SnoozeTestTags.NextWeek),
                    ).forEach { (preset, label, tag) ->
                        SnoozePresetButton(label, enabled, tag, { onPreset(preset) }, Modifier.weight(1f))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                TextButton(
                    onClick = onCustom,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag(SnoozeTestTags.Custom),
                ) { Text(strings.custom) }
            }
        },
        confirmButton = { CandyActionButton(onClick = onDismiss) { Text(strings.cancel) } },
        dismissButton = {},
    )
}

@Composable
fun SharedSnoozedTabsContent(
    snoozedTabs: List<SnoozedTab>,
    profiles: List<BrowserProfile>,
    wakeLabel: @Composable (SnoozedTab) -> String,
    onBack: () -> Unit,
    onReschedule: (String) -> Unit,
    onOpenNow: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    strings: SnoozeScreenStrings = SnoozeScreenStrings(),
    header: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().testTag(SnoozeTestTags.Management)) {
        if (header != null) header() else CandyPageHeader(strings.managementTitle, strings.back, onBack)
        if (snoozedTabs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(strings.emptyTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(strings.emptyBody, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = if (header == null) 20.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(snoozedTabs, key = { it.tab.id }) { item ->
                    SnoozedTabCard(
                        snoozed = item,
                        profileEmoji = profiles.firstOrNull { it.id == item.tab.profileId }?.emoji.orEmpty(),
                        wakeLabel = wakeLabel(item),
                        strings = strings,
                        onReschedule = { onReschedule(item.tab.id) },
                        onOpenNow = { onOpenNow(item.tab.id) },
                        onDelete = { onDelete(item.tab.id) },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun SnoozePresetButton(
    text: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp).testTag(tag),
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
            Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun snoozeDisplayTitle(tab: BrowserTab, newTabTitle: String): String =
    if (tab.url == BLANK_URL || tab.title.isBlank()) newTabTitle else tab.title

@Composable
private fun SnoozedTabCard(
    snoozed: SnoozedTab,
    profileEmoji: String,
    wakeLabel: String,
    strings: SnoozeScreenStrings,
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
                    PlatformProfileEmoji(
                        emoji = profileEmoji,
                        fontSize = LocalTextStyle.current.fontSize,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                }
                Text(
                    snoozeDisplayTitle(snoozed.tab, strings.newTabTitle),
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
                wakeLabel,
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
                    Text(strings.reschedule)
                }
                TextButton(onClick = onOpenNow) {
                    Text(strings.openNow)
                }
                TextButton(onClick = onDelete) {
                    Text(
                        strings.delete,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

object SnoozeTestTags {
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
    fun overviewTitle(tabId: String) = "overview_title:$tabId"
    fun overviewClose(tabId: String) = "overview_close:$tabId"
    fun card(tabId: String) = "snoozed_tab:$tabId"
}
