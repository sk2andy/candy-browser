package dev.sk2andy.materialbrowser.ui

import android.view.ViewGroup
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.sk2andy.materialbrowser.R

internal object GuestBubbleTestTags {
    const val Screen = "guest_bubble_screen"
    const val Address = "guest_bubble_address"
    const val End = "guest_bubble_end"
    const val Browser = "guest_bubble_browser"
}

@Composable
internal fun GuestBubbleScreen(
    webView: View?,
    address: String,
    loading: Boolean,
    canGoBack: Boolean,
    startupFailed: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onEnd: () -> Unit,
    onRetry: () -> Unit,
) {
    var input by remember(address) { mutableStateOf(address) }
    val focus = LocalFocusManager.current
    BackHandler(onBack = { if (canGoBack) onBack() else onEnd() })
    Surface(modifier = Modifier.fillMaxSize().testTag(GuestBubbleTestTags.Screen)) {
        Column(modifier = Modifier.safeDrawingPadding().imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.guest_bubble_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEnd, modifier = Modifier.testTag(GuestBubbleTestTags.End)) {
                    Text(stringResource(R.string.guest_bubble_end))
                }
            }
            if (webView == null) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (startupFailed) {
                        Text(stringResource(R.string.guest_bubble_start_failed))
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.guest_bubble_retry))
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = {
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView
                        },
                        modifier = Modifier.fillMaxSize().testTag(GuestBubbleTestTags.Browser),
                    )
                    if (address.isEmpty()) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("👻", style = MaterialTheme.typography.displayLarge)
                                Text(stringResource(R.string.guest_bubble_welcome), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.guest_bubble_private_session))
                            }
                        }
                    }
                }
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, enabled = canGoBack) {
                    Text(stringResource(R.string.guest_bubble_back))
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = webView != null,
                    modifier = Modifier.weight(1f).testTag(GuestBubbleTestTags.Address),
                    label = { Text(stringResource(R.string.guest_bubble_address)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        onNavigate(input)
                        focus.clearFocus()
                    }),
                )
            }
        }
    }
}
