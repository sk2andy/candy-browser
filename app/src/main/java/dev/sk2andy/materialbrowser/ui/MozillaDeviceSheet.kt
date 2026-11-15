package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.sync.MozillaSyncDevice
import dev.sk2andy.materialbrowser.sync.MozillaSyncTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MozillaDeviceSheet(
    device: MozillaSyncDevice,
    canSendCurrentTab: Boolean,
    onOpen: (MozillaSyncTab) -> Unit,
    onSend: ((Boolean) -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    var sending by remember(device.id) { mutableStateOf(false) }
    var sendResult by remember(device.id) { mutableStateOf<Boolean?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().testTag("mozilla_device_tabs"),
        ) {
            item {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(device.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.mozilla_sync_device_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (device.canReceiveTabs) {
                        TextButton(
                            onClick = {
                                sending = true
                                sendResult = null
                                onSend { success ->
                                    sending = false
                                    sendResult = success
                                }
                            },
                            enabled = canSendCurrentTab && !sending,
                            modifier = Modifier.testTag("mozilla_device_send_tab"),
                        ) {
                            Text(stringResource(R.string.mozilla_sync_send_tab))
                        }
                    }
                    sendResult?.let { success ->
                        Text(
                            stringResource(
                                if (success) R.string.mozilla_sync_send_success
                                else R.string.mozilla_sync_send_failed,
                            ),
                            color = if (success) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = onRefresh, modifier = Modifier.testTag("mozilla_device_refresh")) {
                        Text(stringResource(R.string.sync_action_refresh))
                    }
                    if (device.tabs.isEmpty()) Text(stringResource(R.string.mozilla_sync_no_tabs))
                }
            }
            itemsIndexed(device.tabs) { index, tab ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(onClickLabel = stringResource(R.string.mozilla_sync_open_tab)) { onOpen(tab) }
                        .testTag("mozilla_device_tab_$index")
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tab.title.ifBlank { tab.url },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            tab.url,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(painterResource(R.drawable.ic_symbol_open_in_new), contentDescription = null)
                }
            }
        }
    }
}
