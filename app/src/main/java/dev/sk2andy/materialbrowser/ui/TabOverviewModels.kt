package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.data.TabOverviewMode

internal data class TabExitHero(
    val tabId: String,
    val preview: Bitmap?,
    val startBounds: Rect,
    val isIncognito: Boolean,
    val startCornerRadius: Dp = 28.dp,
    val previewTopInsetPx: Int = 0,
    val mode: TabOverviewMode = TabOverviewMode.Hero,
)

internal data class TabReorderAnimation(
    val tabId: String,
    val targetIndex: Int,
    val indexDeltas: Map<String, Int>,
)

internal data class ActiveTabReorder(
    val tabId: String,
    val mode: TabOverviewMode,
    val orderIds: List<String>,
    val sourceIndex: Int,
    val destinationIndex: Int,
    val allowedRange: IntRange,
    val sourceBounds: Rect,
    val slotBounds: Map<String, Rect>,
    val dragOffset: Offset = Offset.Zero,
    val autoScrollOffset: Offset = Offset.Zero,
    val heroEdgeStepping: Boolean = false,
    val lifted: Boolean = false,
    val settling: Boolean = false,
)
