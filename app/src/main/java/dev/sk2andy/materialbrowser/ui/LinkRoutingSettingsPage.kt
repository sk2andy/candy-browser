package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingRule
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingRules
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingTarget
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingTargetKind
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import java.util.UUID

@Composable
internal fun LinkRoutingSettingsPage(
    rules: List<LinkRoutingRule>,
    profiles: List<BrowserProfile>,
    capsules: List<SiteCapsule>,
    onRulesChanged: (List<LinkRoutingRule>) -> Unit,
    onBack: () -> Unit,
) {
    var editorVisible by remember { mutableStateOf(false) }
    var editedRule by remember { mutableStateOf<LinkRoutingRule?>(null) }
    val eligibleProfiles = LinkRoutingRules.eligibleProfiles(profiles)
    val eligibleProfileIds = eligibleProfiles.mapTo(hashSetOf(), BrowserProfile::id)
    val eligibleCapsules = capsules.filter { it.profileId in eligibleProfileIds }
    SettingsPage(title = stringResource(R.string.link_routing_title), onBack = onBack) {
        Text(
            stringResource(R.string.link_routing_summary),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Button(
            enabled = rules.size < LinkRoutingRules.MAX_RULES,
            onClick = {
                editedRule = null
                editorVisible = true
            },
            modifier = Modifier.fillMaxWidth().testTag(LinkRoutingTestTags.Add),
        ) {
            Text(stringResource(R.string.link_routing_add))
        }
        if (rules.isEmpty()) {
            Text(
                stringResource(R.string.link_routing_empty),
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsPreferenceGroup {
            rules.forEachIndexed { index, rule ->
                if (index > 0) SettingsPreferenceDivider()
                Surface(
                    shape = dev.sk2andy.materialbrowser.shared.ui.settings.settingsRowShape(),
                    color = dev.sk2andy.materialbrowser.shared.ui.settings.settingsRowColor(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.domain, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    linkRoutingTargetLabel(rule.target, eligibleProfiles, eligibleCapsules),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (rule.includeSubdomains) {
                                    Text(stringResource(R.string.link_routing_subdomains), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { enabled ->
                                    onRulesChanged(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                                },
                                modifier = Modifier
                                    .testTag(LinkRoutingTestTags.enabled(rule.id))
                                    .semantics { contentDescription = rule.domain },
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    val next = rules.toMutableList()
                                    next.add(index - 1, next.removeAt(index))
                                    onRulesChanged(next)
                                },
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.link_routing_move_up))
                            }
                            IconButton(
                                enabled = index < rules.lastIndex,
                                onClick = {
                                    val next = rules.toMutableList()
                                    next.add(index + 1, next.removeAt(index))
                                    onRulesChanged(next)
                                },
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.link_routing_move_down))
                            }
                            IconButton(onClick = {
                                editedRule = rule
                                editorVisible = true
                            }) {
                                Icon(Icons.Default.Edit, stringResource(R.string.link_routing_edit))
                            }
                            IconButton(onClick = { onRulesChanged(rules.filterNot { it.id == rule.id }) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.link_routing_delete))
                            }
                        }
                    }
                }
            }
        }
    }
    if (editorVisible) {
        LinkRoutingRuleDialog(
            initial = editedRule,
            profiles = eligibleProfiles,
            capsules = eligibleCapsules,
            onSave = { rule ->
                val next = if (editedRule == null) rules + rule else rules.map { if (it.id == rule.id) rule else it }
                onRulesChanged(next)
                editorVisible = false
            },
            onDismiss = { editorVisible = false },
        )
    }
}

@Composable
private fun LinkRoutingRuleDialog(
    initial: LinkRoutingRule?,
    profiles: List<BrowserProfile>,
    capsules: List<SiteCapsule>,
    onSave: (LinkRoutingRule) -> Unit,
    onDismiss: () -> Unit,
) {
    val id = remember(initial) { initial?.id ?: UUID.randomUUID().toString() }
    var domain by remember(initial) { mutableStateOf(initial?.domain.orEmpty()) }
    var subdomains by remember(initial) { mutableStateOf(initial?.includeSubdomains ?: false) }
    var target by remember(initial) { mutableStateOf(initial?.target ?: LinkRoutingTarget(LinkRoutingTargetKind.Reader)) }
    var kindMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }
    val candidate = LinkRoutingRules.sanitize(
        LinkRoutingRule(id, domain, subdomains, target, initial?.enabled ?: true),
    )
    val availableTarget = when (target.kind) {
        LinkRoutingTargetKind.Reader -> true
        LinkRoutingTargetKind.Profile -> profiles.any { it.id == target.id }
        LinkRoutingTargetKind.Capsule -> capsules.any { it.id == target.id }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_routing_edit)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it.take(253) },
                    label = { Text(stringResource(R.string.link_routing_domain)) },
                    placeholder = { Text("github.com") },
                    singleLine = true,
                    isError = domain.isNotBlank() && candidate == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().testTag(LinkRoutingTestTags.Domain),
                )
                SettingsPreferenceDivider()
                SettingsSwitch(
                    title = stringResource(R.string.link_routing_subdomains),
                    subtitle = stringResource(R.string.link_routing_subdomains_summary),
                    checked = subdomains,
                    onCheckedChange = { subdomains = it },
                )
                Box {
                    SettingsChoice(
                        title = stringResource(R.string.link_routing_target),
                        value = linkRoutingKindLabel(target.kind),
                        expanded = kindMenu,
                        onClick = { kindMenu = true },
                        modifier = Modifier.testTag(LinkRoutingTestTags.Kind),
                    )
                    SettingsDropdown(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                        LinkRoutingTargetKind.entries.forEach { kind ->
                            SettingsDropdownItem(
                                label = linkRoutingKindLabel(kind),
                                selected = kind == target.kind,
                                onClick = {
                                    kindMenu = false
                                    target = LinkRoutingTarget(
                                        kind,
                                        when (kind) {
                                            LinkRoutingTargetKind.Profile -> profiles.firstOrNull()?.id
                                            LinkRoutingTargetKind.Capsule -> capsules.firstOrNull()?.id
                                            LinkRoutingTargetKind.Reader -> null
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
                if (target.kind != LinkRoutingTargetKind.Reader) {
                    Box {
                        SettingsChoice(
                            title = stringResource(R.string.link_routing_destination),
                            value = linkRoutingTargetLabel(target, profiles, capsules),
                            expanded = targetMenu,
                            onClick = { targetMenu = true },
                        )
                        SettingsDropdown(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                            val options = if (target.kind == LinkRoutingTargetKind.Profile) {
                                profiles.map { it.id to it.emoji }
                            } else {
                                capsules.map { it.id to it.name }
                            }
                            options.forEach { (targetId, label) ->
                                SettingsDropdownItem(
                                    label = label,
                                    selected = target.id == targetId,
                                    onClick = {
                                        target = target.copy(id = targetId)
                                        targetMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                Text(stringResource(R.string.link_routing_boundaries), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = candidate != null && availableTarget,
                onClick = { candidate?.let(onSave) },
                modifier = Modifier.testTag(LinkRoutingTestTags.Save),
            ) {
                Text(stringResource(R.string.link_routing_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun linkRoutingKindLabel(kind: LinkRoutingTargetKind): String = stringResource(
    when (kind) {
        LinkRoutingTargetKind.Profile -> R.string.link_routing_profile
        LinkRoutingTargetKind.Reader -> R.string.link_routing_reader
        LinkRoutingTargetKind.Capsule -> R.string.link_routing_capsule
    },
)

@Composable
private fun linkRoutingTargetLabel(
    target: LinkRoutingTarget,
    profiles: List<BrowserProfile>,
    capsules: List<SiteCapsule>,
): String = when (target.kind) {
    LinkRoutingTargetKind.Reader -> linkRoutingKindLabel(target.kind)
    LinkRoutingTargetKind.Profile -> profiles.firstOrNull { it.id == target.id }?.emoji
        ?: stringResource(R.string.link_routing_target_missing)
    LinkRoutingTargetKind.Capsule -> capsules.firstOrNull { it.id == target.id }?.name
        ?: stringResource(R.string.link_routing_target_missing)
}

internal object LinkRoutingTestTags {
    const val Add = "link_routing_add"
    const val Domain = "link_routing_domain"
    const val Kind = "link_routing_kind"
    const val Save = "link_routing_save"
    fun enabled(id: String): String = "link_routing_enabled_$id"
}
