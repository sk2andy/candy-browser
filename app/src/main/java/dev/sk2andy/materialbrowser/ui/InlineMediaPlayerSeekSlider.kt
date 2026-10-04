package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.settings.settingsSearchTarget
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerSeekSettingsRules
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InlineMediaPlayerSeekSlider(
    title: String,
    seconds: Int,
    enabled: Boolean,
    onSecondsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = InlineMediaPlayerSeekSettingsRules.SupportedSeconds
    val index = options.indexOf(InlineMediaPlayerSeekSettingsRules.normalizeSeconds(seconds))
    var lastUserStop by remember(index) { mutableIntStateOf(index) }
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val valueLabel = stringResource(R.string.settings_inline_media_player_seek_seconds, options[index])
    val view = LocalView.current
    val density = LocalDensity.current
    var bubbleWidthPx by remember { mutableIntStateOf(0) }
    var bubbleHeightPx by remember { mutableIntStateOf(0) }
    val bubbleHeight = with(density) { bubbleHeightPx.toDp() }
    val colors = SliderDefaults.colors()

    Surface(
        modifier = Modifier.fillMaxWidth().settingsSearchTarget(title),
        shape = MaterialTheme.shapes.large,
        color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                )
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f),
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth().height((bubbleHeight + 56.dp).coerceAtLeast(88.dp))) {
                val bubbleWidth = with(density) { bubbleWidthPx.toDp() }
                val thumbCenter = 2.dp + (maxWidth - 4.dp) * (index.toFloat() / options.lastIndex)
                val bubbleStart = (thumbCenter - bubbleWidth / 2)
                    .coerceIn(0.dp, (maxWidth - bubbleWidth).coerceAtLeast(0.dp))
                Surface(
                    modifier = Modifier
                        .offset(x = bubbleStart, y = 4.dp)
                        .onSizeChanged {
                            bubbleWidthPx = it.width
                            bubbleHeightPx = it.height
                        }
                        .alpha(if (enabled && (dragged || pressed || focused)) 1f else 0f)
                        .clearAndSetSemantics {},
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        valueLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Slider(
                    value = index.toFloat(),
                    onValueChange = { candidate ->
                        val stop = candidate.roundToInt().coerceIn(options.indices)
                        if (enabled && stop != lastUserStop) {
                            lastUserStop = stop
                            view.performTabFocusHaptic()
                            onSecondsChanged(options[stop])
                        }
                    },
                    enabled = enabled,
                    valueRange = 0f..options.lastIndex.toFloat(),
                    steps = options.size - 2,
                    interactionSource = interactionSource,
                    colors = colors,
                    modifier = modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .semantics {
                            contentDescription = title
                            stateDescription = valueLabel
                        },
                    thumb = {
                        // Keep the measured thumb width stable; the bubble has its own overlay.
                        Box(Modifier.size(width = 4.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                            SliderDefaults.Thumb(
                                interactionSource = interactionSource,
                                colors = colors,
                                enabled = enabled,
                                thumbSize = DpSize(4.dp, 44.dp),
                            )
                        }
                    },
                    track = { state ->
                        SliderDefaults.Track(
                            sliderState = state,
                            enabled = enabled,
                            colors = colors,
                            drawStopIndicator = null,
                        )
                    },
                )
            }
        }
    }
}
