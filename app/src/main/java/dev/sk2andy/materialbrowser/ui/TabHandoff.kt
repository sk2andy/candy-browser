package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab

internal data class TabHandoff(
    val tab: BrowserTab,
    val preview: Bitmap?,
    val favicon: Bitmap?,
    val previewTopInsetPx: Int,
    val isRestoring: Boolean = false,
    val visualIdentity: Any = Any(),
) {
    val tabId: String
        get() = tab.id
}

@Composable
internal fun rememberTabHandoffAlpha(handoff: TabHandoff?): Animatable<Float, AnimationVector1D> =
    remember(handoff?.visualIdentity) { Animatable(1f) }

internal object TabHandoffRules {
    const val SURFACE_FALLBACK_DELAY_MILLIS = 2_000L

    fun isRestoring(tab: BrowserTab, sessionResident: Boolean): Boolean =
        !sessionResident && !tab.isIncognito && tab.url != BLANK_URL

    fun revealDurationMillis(handoff: TabHandoff): Int = if (handoff.isRestoring) 250 else 110

    fun previewSaturation(handoff: TabHandoff, alpha: Float): Float =
        if (handoff.isRestoring) (1f - alpha).coerceIn(0f, 1f) else 1f

    fun shouldRevealLiveContent(
        handoff: TabHandoff,
        tabOverviewVisible: Boolean,
        liveFrameTabId: String?,
    ): Boolean = !tabOverviewVisible &&
        (handoff.tab.url == BLANK_URL || liveFrameTabId == handoff.tabId)
}
