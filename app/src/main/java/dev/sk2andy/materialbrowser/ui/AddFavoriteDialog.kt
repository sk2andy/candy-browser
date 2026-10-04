package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.BrowsingFavoritesRules
import dev.sk2andy.materialbrowser.data.FavoriteAddRules

@Composable
internal fun AddFavoriteDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, (Boolean) -> Unit) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    val valid = FavoriteAddRules.entry(url, title, addedAt = 0L) != null
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.action_add_favorite)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(BrowsingFavoritesRules.MAX_FOLDER_TITLE_CHARS) },
                    label = { Text(stringResource(R.string.favorites_name)) },
                    enabled = !saving,
                    singleLine = true,
                    modifier = Modifier.testTag("add_favorite_title"),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it.take(FavoriteAddRules.MAX_URL_CHARS)
                        saveFailed = false
                    },
                    label = { Text(stringResource(R.string.favorites_url)) },
                    placeholder = { Text("https://example.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = !saving,
                    singleLine = true,
                    isError = url.isNotBlank() && !valid,
                    modifier = Modifier.testTag("add_favorite_url"),
                )
                if (saveFailed) Text(stringResource(R.string.favorites_save_failed))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    saving = true
                    onAdd(url, title) { saved ->
                        saving = false
                        if (saved) onDismiss() else saveFailed = true
                    }
                },
                enabled = valid && !saving,
                modifier = Modifier.testTag("add_favorite_confirm"),
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
