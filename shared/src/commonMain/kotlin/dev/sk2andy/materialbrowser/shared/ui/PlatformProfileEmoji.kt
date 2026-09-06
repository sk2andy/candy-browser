package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit

@Composable
expect fun PlatformProfileEmoji(
    emoji: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
)
