@file:OptIn(ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowserSession
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowserSessionState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtension
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionPermissionRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionSnapshot

internal object GeckoBrowserTestTags {
    const val Viewport = "gecko_browser_viewport"
    const val Address = "gecko_browser_address"
    const val Extensions = "gecko_browser_extensions"
    const val InstallUrl = "gecko_extension_install_url"
    const val PermissionPrompt = "gecko_extension_permission_prompt"
}

@Composable
internal fun GeckoBrowserScreen(
    session: GeckoBrowserSession,
    state: GeckoBrowserSessionState,
    address: String,
    extensions: GeckoExtensionSnapshot,
    extensionsVisible: Boolean,
    extensionBusy: Boolean,
    message: String?,
    permissionRequest: GeckoExtensionPermissionRequest?,
    onAddressChanged: (String) -> Unit,
    onNavigate: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onShowExtensions: () -> Unit,
    onHideExtensions: () -> Unit,
    onInstallExtension: (String) -> Unit,
    onSetExtensionEnabled: (GeckoExtension, Boolean) -> Unit,
    onSetExtensionPrivate: (GeckoExtension, Boolean) -> Unit,
    onUpdateExtension: (GeckoExtension) -> Unit,
    onUninstallExtension: (GeckoExtension) -> Unit,
    onPermissionDecision: (Boolean, Boolean) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            GeckoNavigationBar(
                state = state,
                address = address,
                onAddressChanged = onAddressChanged,
                onNavigate = onNavigate,
                onGoBack = onGoBack,
                onGoForward = onGoForward,
                onReloadOrStop = onReloadOrStop,
                onShowExtensions = onShowExtensions,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AndroidView(
                factory = session::createView,
                onRelease = session::releaseView,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(GeckoBrowserTestTags.Viewport),
            )
            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }

    if (extensionsVisible) {
        GeckoExtensionManager(
            extensions = extensions,
            busy = extensionBusy,
            message = message,
            onInstall = onInstallExtension,
            onSetEnabled = onSetExtensionEnabled,
            onSetPrivate = onSetExtensionPrivate,
            onUpdate = onUpdateExtension,
            onUninstall = onUninstallExtension,
            onDismiss = onHideExtensions,
        )
    }

    permissionRequest?.let { request ->
        GeckoExtensionPermissionDialog(
            request = request,
            onDecision = onPermissionDecision,
        )
    }
}

@Composable
private fun GeckoNavigationBar(
    state: GeckoBrowserSessionState,
    address: String,
    onAddressChanged: (String) -> Unit,
    onNavigate: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onShowExtensions: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 5.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onGoBack, enabled = state.canGoBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            IconButton(onClick = onGoForward, enabled = state.canGoForward) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.action_forward),
                )
            }
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChanged,
                modifier = Modifier
                    .weight(1f)
                    .testTag(GeckoBrowserTestTags.Address),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onGo = { onNavigate() },
                ),
                placeholder = { Text(stringResource(R.string.gecko_address_or_search)) },
            )
            IconButton(onClick = onReloadOrStop) {
                Icon(
                    if (state.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = stringResource(
                        if (state.isLoading) {
                            R.string.gecko_stop_loading
                        } else {
                            R.string.gecko_reload
                        },
                    ),
                )
            }
            IconButton(
                onClick = onShowExtensions,
                modifier = Modifier.testTag(GeckoBrowserTestTags.Extensions),
            ) {
                Icon(
                    androidx.compose.ui.graphics.vector.ImageVector.vectorResource(
                        R.drawable.ic_symbol_extension,
                    ),
                    contentDescription = stringResource(R.string.gecko_extensions_title),
                )
            }
        }
    }
}

@Composable
private fun GeckoExtensionManager(
    extensions: GeckoExtensionSnapshot,
    busy: Boolean,
    message: String?,
    onInstall: (String) -> Unit,
    onSetEnabled: (GeckoExtension, Boolean) -> Unit,
    onSetPrivate: (GeckoExtension, Boolean) -> Unit,
    onUpdate: (GeckoExtension) -> Unit,
    onUninstall: (GeckoExtension) -> Unit,
    onDismiss: () -> Unit,
) {
    var installDialogVisible by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
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
                IconButton(onClick = { installDialogVisible = true }, enabled = !busy) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.gecko_extension_install),
                    )
                }
            }
            message?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (extensions.extensions.isEmpty()) {
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
                    items(extensions.extensions, key = GeckoExtension::id) { extension ->
                        GeckoExtensionRow(
                            extension = extension,
                            busy = busy,
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
}

@Composable
private fun GeckoExtensionRow(
    extension: GeckoExtension,
    busy: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onSetPrivate: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
) {
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
                enabled = !busy && !extension.isBuiltIn,
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
                enabled = !busy && !extension.isBuiltIn,
            )
            IconButton(onClick = onUpdate, enabled = !busy && !extension.isBuiltIn) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.gecko_extension_update),
                )
            }
            IconButton(onClick = onUninstall, enabled = !busy && !extension.isBuiltIn) {
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
                        .testTag(GeckoBrowserTestTags.InstallUrl),
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
    val requestedAccess = remember(request) {
        (request.permissions + request.origins)
            .distinct()
            .joinToString("\n") { value -> "• $value" }
            .ifBlank { noAdditionalPermissions }
    }
    AlertDialog(
        onDismissRequest = { onDecision(false, false) },
        modifier = Modifier.testTag(GeckoBrowserTestTags.PermissionPrompt),
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
