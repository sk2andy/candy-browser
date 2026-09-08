package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit

@Composable
actual fun PlatformProfileEmoji(
    emoji: String,
    fontSize: TextUnit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = fontSize,
            maxLines = 1,
        )
    }
}
