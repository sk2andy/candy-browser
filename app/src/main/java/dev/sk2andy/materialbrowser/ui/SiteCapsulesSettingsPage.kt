package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

@Composable
internal fun SiteCapsulesSettingsPage(
    siteCapsules: List<SiteCapsule>,
    onEditCapsule: (SiteCapsule) -> Unit,
    onDeleteCapsule: (SiteCapsule) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.capsule_settings_title),
        onBack = onBack,
    ) {
        Text(
            stringResource(R.string.capsule_settings_launcher_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (siteCapsules.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Text(
                    stringResource(R.string.capsule_settings_empty),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            siteCapsules.forEach { capsule ->
                Surface(
                    onClick = { onEditCapsule(capsule) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(capsule.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                AddressResolver.displayText(capsule.startUrl),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteCapsule(capsule) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.capsule_delete_title),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
