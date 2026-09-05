package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap

internal data class TabHandoff(
    val tabId: String,
    val preview: Bitmap?,
    val title: String,
    val favicon: Bitmap?,
    val isIncognito: Boolean,
    val previewTopInsetPx: Int,
)
