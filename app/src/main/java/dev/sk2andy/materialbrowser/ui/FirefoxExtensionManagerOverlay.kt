@file:OptIn(ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtension
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionChromeRules
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionManagerMessage
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionManagerState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionPermissionRequest
import kotlinx.coroutines.flow.collect

internal object FirefoxExtensionManagerTestTags {
    const val Overlay = "firefox_extension_manager"
    const val InstallUrl = "gecko_extension_install_url"
    const val PermissionPrompt = "gecko_extension_permission_prompt"

    fun optionsPage(extensionId: String): String = "gecko_extension_options:$extensionId"
}

@Composable
internal fun FirefoxExtensionManagerOverlay(
    state: GeckoExtensionManagerState,
    onInstall: (String) -> Unit,
    onSetEnabled: (GeckoExtension, Boolean) -> Unit,
    onSetPrivate: (GeckoExtension, Boolean) -> Unit,
    onUpdate: (GeckoExtension) -> Unit,
    onUninstall: (GeckoExtension) -> Unit,
    onOpenOptionsPage: (GeckoExtension) -> Unit,
    onPermissionDecision: (Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var installDialogVisible by remember { mutableStateOf(false) }
    PredictiveBackHandler(enabled = true) { events ->
        events.collect { }
        onDismiss()
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(FirefoxExtensionManagerTestTags.Overlay),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            ExtensionManagerHeader(
                canManage = state.canManage,
                onInstall = { installDialogVisible = true },
                onDismiss = onDismiss,
            )
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (state.managementContext.isPrivate) {
                ExtensionManagerMessage(R.string.gecko_extension_private_management_disabled)
            }
            state.message?.let { message ->
                ExtensionManagerMessage(
                    when (message) {
                        GeckoExtensionManagerMessage.InvalidSignedXpi ->
                            R.string.gecko_extension_invalid_xpi
                        GeckoExtensionManagerMessage.InstallNetworkFailure ->
                            R.string.gecko_extension_install_network_failure
                        GeckoExtensionManagerMessage.InstallIncorrectHash ->
                            R.string.gecko_extension_install_incorrect_hash
                        GeckoExtensionManagerMessage.InstallCorruptFile ->
                            R.string.gecko_extension_install_corrupt_file
                        GeckoExtensionManagerMessage.InstallFileAccess ->
                            R.string.gecko_extension_install_file_access
                        GeckoExtensionManagerMessage.InstallUnsigned ->
                            R.string.gecko_extension_install_unsigned
                        GeckoExtensionManagerMessage.InstallUnexpectedType ->
                            R.string.gecko_extension_install_unexpected_type
                        GeckoExtensionManagerMessage.InstallUnexpectedVersion ->
                            R.string.gecko_extension_install_unexpected_version
                        GeckoExtensionManagerMessage.InstallIncorrectId ->
                            R.string.gecko_extension_install_incorrect_id
                        GeckoExtensionManagerMessage.InstallInvalidDomain ->
                            R.string.gecko_extension_install_invalid_domain
                        GeckoExtensionManagerMessage.InstallBlocklisted ->
                            R.string.gecko_extension_install_blocklisted
                        GeckoExtensionManagerMessage.InstallIncompatible ->
                            R.string.gecko_extension_install_incompatible
                        GeckoExtensionManagerMessage.InstallUnsupportedType ->
                            R.string.gecko_extension_install_unsupported_type
                        GeckoExtensionManagerMessage.InstallAdminOnly ->
                            R.string.gecko_extension_install_admin_only
                        GeckoExtensionManagerMessage.InstallSoftBlocked ->
                            R.string.gecko_extension_install_soft_blocked
                        GeckoExtensionManagerMessage.InstallCancelled ->
                            R.string.gecko_extension_install_cancelled
                        GeckoExtensionManagerMessage.InstallPostponed ->
                            R.string.gecko_extension_install_postponed
                        GeckoExtensionManagerMessage.ActionRejected ->
                            R.string.gecko_extension_action_rejected
                        GeckoExtensionManagerMessage.ActionFailed ->
                            R.string.gecko_extension_action_failed
                    },
                )
            }
            if (state.snapshot.extensions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.gecko_extensions_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.snapshot.extensions, key = GeckoExtension::id) { extension ->
                        GeckoExtensionRow(
                            extension = extension,
                            canManage = state.canManage,
                            onOpenOptionsPage = { onOpenOptionsPage(extension) },
                            onSetEnabled = { onSetEnabled(extension, it) },
                            onSetPrivate = { onSetPrivate(extension, it) },
                            onUpdate = { onUpdate(extension) },
                            onUninstall = { onUninstall(extension) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (installDialogVisible) {
        GeckoExtensionInstallDialog(
            onInstall = { url ->
                installDialogVisible = false
                onInstall(url)
            },
            onDismiss = { installDialogVisible = false },
        )
    }
    state.permissionRequest?.let { request ->
        GeckoExtensionPermissionDialog(
            request = request,
            onDecision = onPermissionDecision,
        )
    }
}

@Composable
private fun ExtensionManagerHeader(
    canManage: Boolean,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
                stringResource(R.string.gecko_extensions_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.gecko_extensions_manager_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onInstall, enabled = canManage) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.gecko_extension_install),
            )
        }
    }
}

@Composable
private fun ExtensionManagerMessage(messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun GeckoExtensionRow(
    extension: GeckoExtension,
    canManage: Boolean,
    onOpenOptionsPage: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetPrivate: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
) {
    val controlsEnabled = canManage && !extension.isBuiltIn
    val optionsAvailable = canManage &&
        GeckoExtensionChromeRules.optionsPageTarget(extension) != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    extension.name ?: extension.id,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(extension.version, extension.id).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = extension.enabled,
                onCheckedChange = onSetEnabled,
                enabled = controlsEnabled,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.gecko_extension_allow_private),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = extension.allowedInPrivateBrowsing,
                onCheckedChange = onSetPrivate,
                enabled = controlsEnabled,
            )
            IconButton(
                onClick = onOpenOptionsPage,
                enabled = optionsAvailable,
                modifier = Modifier.testTag(
                    FirefoxExtensionManagerTestTags.optionsPage(extension.id),
                ),
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(
                        R.string.gecko_extensions_options_summary,
                    ),
                )
            }
            IconButton(onClick = onUpdate, enabled = controlsEnabled) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.gecko_extension_update),
                )
            }
            IconButton(onClick = onUninstall, enabled = controlsEnabled) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.gecko_extension_uninstall),
                )
            }
        }
    }
}

@Composable
private fun GeckoExtensionInstallDialog(
    onInstall: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gecko_extension_install)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.gecko_extension_install_hint))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FirefoxExtensionManagerTestTags.InstallUrl),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    placeholder = { Text("https://…/addon.xpi") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onInstall(url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.gecko_extension_install_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun GeckoExtensionPermissionDialog(
    request: GeckoExtensionPermissionRequest,
    onDecision: (Boolean, Boolean) -> Unit,
) {
    var allowPrivate by remember(request.extensionId) { mutableStateOf(false) }
    val noAdditionalPermissions = stringResource(R.string.gecko_extension_no_permissions)
    val requestedAccess = remember(request, noAdditionalPermissions) {
        (request.permissions + request.origins + request.dataCollectionPermissions)
            .distinct()
            .joinToString("\n") { value -> "• $value" }
            .ifBlank { noAdditionalPermissions }
    }
    AlertDialog(
        onDismissRequest = { onDecision(false, false) },
        modifier = Modifier.testTag(FirefoxExtensionManagerTestTags.PermissionPrompt),
        title = { Text(request.extensionName ?: request.extensionId) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.gecko_extension_permission_intro))
                Text(requestedAccess, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.gecko_extension_allow_private_too),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = allowPrivate, onCheckedChange = { allowPrivate = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onDecision(true, allowPrivate) }) {
                Text(stringResource(R.string.action_allow))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onDecision(false, false) }) {
                Text(stringResource(R.string.action_deny))
            }
        },
    )
}
