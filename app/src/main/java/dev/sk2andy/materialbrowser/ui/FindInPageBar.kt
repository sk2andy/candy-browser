package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceRole
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens
import eightbitlab.com.blurview.BlurTarget

@Composable
internal fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchText: String,
    isCounting: Boolean,
    canNavigate: Boolean,
    focusNonce: Int,
    autoFocus: Boolean,
    placeholder: String,
    queryContentDescription: String,
    countingContentDescription: String,
    previousMatchContentDescription: String,
    nextMatchContentDescription: String,
    closeContentDescription: String,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    blurTarget: BlurTarget? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val chromeTokens = browserChromeSurfaceTokens(BrowserChromeSurfaceRole.AddressBar)
    LaunchedEffect(autoFocus, focusNonce) {
        if (autoFocus) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    BrowserChromeSurface(
        blurTarget = blurTarget,
        tokens = chromeTokens,
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .testTag(FindInPageBarTestTags.Bar),
        shape = MaterialTheme.shapes.extraLarge,
        blurCornerRadius = chromeTokens.largeCornerRadius,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = queryContentDescription }
                    .testTag(FindInPageBarTestTags.Query),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (canNavigate) onNextMatch() },
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Box(
                modifier = Modifier.widthIn(min = 52.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isCounting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .progressSemantics()
                            .semantics {
                                contentDescription = countingContentDescription
                                liveRegion = LiveRegionMode.Polite
                            }
                            .testTag(FindInPageBarTestTags.Progress),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = matchText,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(FindInPageBarTestTags.MatchCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            IconButton(
                onClick = onPreviousMatch,
                enabled = canNavigate,
                modifier = Modifier.testTag(FindInPageBarTestTags.Previous),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = previousMatchContentDescription,
                )
            }
            IconButton(
                onClick = onNextMatch,
                enabled = canNavigate,
                modifier = Modifier.testTag(FindInPageBarTestTags.Next),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = nextMatchContentDescription,
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag(FindInPageBarTestTags.Close),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = closeContentDescription,
                )
            }
        }
    }
}

internal object FindInPageBarTestTags {
    const val Bar = "find_in_page_bar"
    const val Query = "find_in_page_query"
    const val Progress = "find_in_page_progress"
    const val MatchCount = "find_in_page_match_count"
    const val Previous = "find_in_page_previous"
    const val Next = "find_in_page_next"
    const val Close = "find_in_page_close"
}
