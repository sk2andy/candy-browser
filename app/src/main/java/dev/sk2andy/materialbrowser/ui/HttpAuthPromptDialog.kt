package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.credentials.HttpAuthPrompt

@Composable
internal fun HttpAuthPromptDialog(
    prompt: HttpAuthPrompt,
    onSubmit: (username: String, password: String) -> Unit,
    onCancel: () -> Unit,
) {
    var username by remember(prompt.id) { mutableStateOf("") }
    var password by remember(prompt.id) { mutableStateOf("") }
    var passwordVisible by remember(prompt.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag(HttpAuthPromptTestTags.Dialog),
        title = { Text(stringResource(R.string.http_auth_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.http_auth_dialog_message, prompt.host))
                prompt.realm?.let { realm ->
                    Text(
                        stringResource(R.string.http_auth_dialog_realm, realm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!prompt.isPageSecure) {
                    Text(
                        stringResource(R.string.http_auth_dialog_insecure_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { value -> username = value.take(MAX_HTTP_AUTH_USERNAME_LENGTH) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HttpAuthPromptTestTags.Username),
                    label = { Text(stringResource(R.string.http_auth_username)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { value -> password = value.take(MAX_HTTP_AUTH_PASSWORD_LENGTH) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HttpAuthPromptTestTags.Password),
                    label = { Text(stringResource(R.string.http_auth_password)) },
                    trailingIcon = {
                        val visibilityDescription = stringResource(
                            if (passwordVisible) {
                                R.string.http_auth_hide_password
                            } else {
                                R.string.http_auth_show_password
                            },
                        )
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(
                                    if (passwordVisible) {
                                        R.drawable.ic_visibility_off
                                    } else {
                                        R.drawable.ic_visibility
                                    },
                                ),
                                contentDescription = visibilityDescription,
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(username, password) },
                modifier = Modifier.testTag(HttpAuthPromptTestTags.Submit),
            ) {
                Text(stringResource(R.string.http_auth_sign_in))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

internal object HttpAuthPromptTestTags {
    const val Dialog = "http_auth_dialog"
    const val Username = "http_auth_username"
    const val Password = "http_auth_password"
    const val Submit = "http_auth_submit"
}

private const val MAX_HTTP_AUTH_USERNAME_LENGTH = 1_024
private const val MAX_HTTP_AUTH_PASSWORD_LENGTH = 4_096
