package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal object AddressBarFieldFocusRules {
    fun shouldDismissEditor(hasReceivedFocus: Boolean, isFocused: Boolean): Boolean =
        hasReceivedFocus && !isFocused

    fun shouldApplyFocusRequest(request: Long, address: String): Boolean =
        request > 0 && address.isBlank()
}

/** Existing expanded Candy address-field renderer, shared by Android and iOS hosts. */
@Composable
fun AddressBarFieldContent(
    editing: Boolean,
    editValue: TextFieldValue,
    onEditValueChange: (TextFieldValue) -> Unit,
    ghostCompletion: String?,
    placeholder: String,
    displayText: String,
    onSubmitAddress: (String) -> Unit,
    submissionText: (String, String?) -> String,
    modifier: Modifier = Modifier,
    editorModifier: Modifier = Modifier,
    displayTextModifier: Modifier = Modifier,
    editorTrailingContent: @Composable () -> Unit = {},
    displayTrailingContent: @Composable () -> Unit = {},
) {
    Box(modifier = modifier) {
        if (editing) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = editValue,
                    onValueChange = onEditValueChange,
                    modifier = editorModifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            onSubmitAddress(submissionText(editValue.text, ghostCompletion))
                        },
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.padding(
                                start = 8.dp,
                                top = 10.dp,
                                bottom = 10.dp,
                            ),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (editValue.text.isEmpty()) {
                                Text(
                                    placeholder,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else if (ghostCompletion != null) {
                                Row {
                                    Text(
                                        editValue.text,
                                        color = Color.Transparent,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        ghostCompletion.drop(editValue.text.length),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.58f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                            innerTextField()
                        }
                    },
                )
                editorTrailingContent()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayText,
                    modifier = displayTextModifier
                        .weight(1f)
                        .padding(
                            start = 13.dp,
                            end = 6.dp,
                            top = 15.dp,
                            bottom = 15.dp,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
                displayTrailingContent()
            }
        }
    }
}
