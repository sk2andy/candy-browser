package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.reader.ReaderChromeMode
import dev.sk2andy.materialbrowser.reader.ReaderChromeRules

@Composable
internal fun ReaderPill(
    resources: ReaderStudioResources,
    sourceUrl: String,
    mode: ReaderChromeMode,
    colors: ReaderColors,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = mode == ReaderChromeMode.Expanded
    val effects = LocalCandyBrowserChromeEffects.current
    // animateContentSize clips its children; reserve shadow space outside the elevated pill.
    Box(
        modifier =
            modifier
                .then(if (effects.reducesMotion()) Modifier else Modifier.animateContentSize())
                .padding(12.dp)
    ) {
        effects.floatingSurface(
            modifier =
                Modifier.widthIn(max = 640.dp)
                    .then(if (expanded) Modifier.fillMaxWidth() else Modifier)
                    .testTag(ReaderStudioTestTags.Pill),
            shape = CircleShape,
            color = colors.card,
            contentColor = colors.content,
            tonalElevation = 3.dp,
            shadowElevation = 5.dp,
        ) {
            Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expanded) sourceUrl else ReaderChromeRules.compactAddress(sourceUrl),
                    modifier =
                        Modifier.weight(1f, fill = expanded)
                            .padding(horizontal = 16.dp)
                            .testTag(ReaderStudioTestTags.Url),
                    style =
                        if (expanded) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(48.dp).testTag(ReaderStudioTestTags.Settings),
                ) {
                    effects.icon(
                        "ellipsis",
                        Icons.Filled.MoreVert,
                        contentDescription = resources.text(ReaderStudioLabel.Settings),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    resources: ReaderStudioResources,
    colors: ReaderColors,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onLibrary: (() -> Unit)?,
    onOpenOriginal: () -> Unit,
    controls: @Composable () -> Unit,
) {
    CandyModalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.article,
        contentColor = colors.content,
        modifier = Modifier.testTag(ReaderStudioTestTags.Sheet),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                resources.text(ReaderStudioLabel.Settings),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            controls()
            CandyActionButton(
                onClick = onHide,
                modifier = Modifier.testTag(ReaderStudioTestTags.Hide),
                contentColor = colors.accent,
            ) {
                Text(resources.text(ReaderStudioLabel.HideControls))
            }
            Text(
                resources.text(ReaderStudioLabel.HiddenControlsHint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
            if (onLibrary != null) {
                CandyActionButton(
                    onClick = onLibrary,
                    modifier = Modifier.testTag(ReaderStudioTestTags.Library),
                    contentColor = colors.accent,
                ) {
                    Text(resources.text(ReaderStudioLabel.OfflineLibrary))
                }
            }
            CandyActionButton(onClick = onOpenOriginal, contentColor = colors.accent) {
                Text(resources.text(ReaderStudioLabel.Original))
            }
            CandyActionButton(
                onClick = onDismiss,
                prominent = true,
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                modifier = Modifier.fillMaxWidth().testTag(ReaderStudioTestTags.Continue),
            ) {
                Text(resources.text(ReaderStudioLabel.ContinueReading))
            }
        }
    }
}
