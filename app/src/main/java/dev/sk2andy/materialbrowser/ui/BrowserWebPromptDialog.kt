package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserWebPrompt
import dev.sk2andy.materialbrowser.browser.BrowserWebPromptChoice
import dev.sk2andy.materialbrowser.browser.BrowserWebPromptKind
import dev.sk2andy.materialbrowser.browser.BrowserWebPromptRules

@Composable
internal fun BrowserWebPromptDialog(
    prompt: BrowserWebPrompt,
    onConfirm: (String?) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember(prompt.id) { mutableStateOf(prompt.defaultValue.orEmpty()) }
    var selectedChoices by remember(prompt.id) {
        mutableStateOf(prompt.choices.filter(BrowserWebPromptChoice::selected).map(BrowserWebPromptChoice::id).toSet())
    }
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag(BrowserWebPromptTestTags.Dialog),
        title = {
            Text(
                prompt.title ?: stringResource(
                    when (prompt.kind) {
                        BrowserWebPromptKind.BeforeUnload -> R.string.web_prompt_leave_title
                        BrowserWebPromptKind.Repost -> R.string.web_prompt_repost_title
                        else -> R.string.web_prompt_title
                    },
                ),
            )
        },
        text = {
            Column {
                val message = prompt.message ?: when (prompt.kind) {
                    BrowserWebPromptKind.BeforeUnload ->
                        stringResource(R.string.web_prompt_leave_message)
                    BrowserWebPromptKind.Repost -> stringResource(R.string.web_prompt_repost_message)
                    else -> null
                }
                message?.let { Text(it) }
                if (prompt.kind in setOf(BrowserWebPromptKind.Text, BrowserWebPromptKind.Color, BrowserWebPromptKind.DateTime)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { updated ->
                            value = updated.take(BrowserWebPromptRules.MAX_INPUT_LENGTH)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(BrowserWebPromptTestTags.Input),
                        singleLine = true,
                    )
                }
                if (prompt.kind == BrowserWebPromptKind.Choice) {
                    prompt.choices.filterNot(BrowserWebPromptChoice::separator).forEach { choice ->
                        val selected = choice.id in selectedChoices
                        TextButton(
                            enabled = !choice.disabled,
                            onClick = {
                                selectedChoices = if (prompt.allowMultiple) {
                                    if (selected) selectedChoices - choice.id else selectedChoices + choice.id
                                } else setOf(choice.id)
                            },
                        ) {
                            if (prompt.allowMultiple) Checkbox(checked = selected, onCheckedChange = null)
                            else RadioButton(selected = selected, onClick = null)
                            Text(choice.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        when (prompt.kind) {
                            BrowserWebPromptKind.Text,
                            BrowserWebPromptKind.Color,
                            BrowserWebPromptKind.DateTime,
                            -> value
                            BrowserWebPromptKind.Choice -> selectedChoices.joinToString("\u001F")
                            else -> null
                        },
                    )
                },
                modifier = Modifier.testTag(BrowserWebPromptTestTags.Confirm),
            ) {
                Text(
                    stringResource(
                        when (prompt.kind) {
                            BrowserWebPromptKind.BeforeUnload -> R.string.web_prompt_leave
                            BrowserWebPromptKind.Repost -> R.string.web_prompt_resend
                            else -> R.string.web_prompt_ok
                        },
                    ),
                )
            }
        },
        dismissButton = if (prompt.kind == BrowserWebPromptKind.Alert) {
            null
        } else {
            {
                TextButton(onClick = onCancel) {
                    Text(
                        stringResource(
                            if (prompt.kind == BrowserWebPromptKind.BeforeUnload) {
                                R.string.web_prompt_stay
                            } else {
                                R.string.action_cancel
                            },
                        ),
                    )
                }
            }
        },
    )
}

internal object BrowserWebPromptTestTags {
    const val Dialog = "browser_web_prompt_dialog"
    const val Input = "browser_web_prompt_input"
    const val Confirm = "browser_web_prompt_confirm"
}
