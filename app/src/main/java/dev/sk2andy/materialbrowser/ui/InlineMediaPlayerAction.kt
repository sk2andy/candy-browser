package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal object InlineMediaPlayerTestTags {
    const val Action = "inline_media_player_action"
}

@Composable
internal fun BrowserInlineMediaPlayerAction(
    controller: BrowserController,
    chromeAllowsAction: Boolean,
    modifier: Modifier = Modifier,
) {
    InlineMediaPlayerAction(
        visible = chromeAllowsAction && controller.canOpenInlineMediaPlayer,
        onClick = controller::openInlineMediaPlayer,
        modifier = modifier,
    )
}

@Composable
internal fun InlineMediaPlayerAction(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .statusBarsPadding()
            .padding(16.dp),
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.testTag(InlineMediaPlayerTestTags.Action),
            shape = MaterialTheme.shapes.extraLarge,
            color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.action_open_candy_player),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
