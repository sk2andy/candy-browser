package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.ui.CandySheetDragIndicator

/** The same visual handle as Candy's browser menu, with the sheet's accessible target. */
@Composable
internal fun FeedSheetDragHandle(description: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CandySheetDragIndicator()
    }
}
