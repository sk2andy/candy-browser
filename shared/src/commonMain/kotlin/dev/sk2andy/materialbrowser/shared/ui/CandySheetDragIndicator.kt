package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared visual indicator; the owning sheet supplies gestures and accessibility actions. */
@Composable
fun CandySheetDragIndicator() {
    Box(
        Modifier.size(36.dp, 4.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
    )
}
