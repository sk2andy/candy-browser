package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** A host-owned command projected into browser chrome without persisting platform UI state. */
data class BrowserCustomAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val requiresLoadedPage: Boolean = false,
)

@Composable
fun BrowserCustomActionButton(
    action: BrowserCustomAction?,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (action == null) {
        Box(modifier.size(48.dp))
    } else {
        IconButton(
            onClick = { onAction(action.id) },
            enabled = action.enabled,
            modifier = modifier.testTag("custom_workflow_action:${action.id}"),
        ) {
            Icon(action.icon, contentDescription = action.label)
        }
    }
}
