package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import kotlin.math.sin

internal enum class TabStackMotionPhase {
    Collapsing,
    Expanding,
}

internal data class TabStackCardTransform(
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val rotationZ: Float,
    val alpha: Float,
)

internal data class TabStackOverviewMotion(
    val stackId: String,
    val phase: TabStackMotionPhase,
    val memberIds: List<String>,
    val anchorTabId: String,
    val boundsByTabId: Map<String, Rect>,
)

internal fun Modifier.tabStackOverviewMotion(
    tabId: String,
    motion: TabStackOverviewMotion?,
    progress: () -> Float,
): Modifier {
    if (motion == null || tabId !in motion.memberIds) return this
    val sourceBounds = motion.boundsByTabId[tabId]
    val anchorBounds = motion.boundsByTabId[motion.anchorTabId]
    val deltaX = if (sourceBounds != null && anchorBounds != null) {
        anchorBounds.center.x - sourceBounds.center.x
    } else {
        0f
    }
    val deltaY = if (sourceBounds != null && anchorBounds != null) {
        anchorBounds.center.y - sourceBounds.center.y
    } else {
        0f
    }
    val memberIndex = motion.memberIds.indexOf(tabId).coerceAtLeast(0)
    return graphicsLayer {
        val transform = TabStackMotionRules.overviewTransform(
            phase = motion.phase,
            progress = progress(),
            deltaX = deltaX,
            deltaY = deltaY,
            memberIndex = memberIndex,
            isAnchor = tabId == motion.anchorTabId,
        )
        translationX = transform.translationX
        translationY = transform.translationY
        scaleX = transform.scale
        scaleY = transform.scale
        rotationZ = transform.rotationZ
        alpha = transform.alpha
    }
}

internal object TabStackMotionRules {
    const val COLLAPSE_DURATION_MILLIS = 280
    const val EXPAND_DURATION_MILLIS = 340
    const val FOLDER_ENTER_DURATION_MILLIS = 320
    const val FOLDER_EXIT_DURATION_MILLIS = 180
    const val ITEM_STAGGER_MILLIS = 34
    const val HERO_MOTION_CARD_LIMIT = 6
    private const val HERO_EDGE_CARD_OVERHANG_FRACTION = 0.18f

    fun heroMotionMemberIds(
        memberIds: List<String>,
        anchorTabId: String,
        limit: Int = HERO_MOTION_CARD_LIMIT,
    ): List<String> {
        if (limit <= 0) return emptyList()
        val anchorIndex = memberIds.indexOf(anchorTabId).takeIf { it >= 0 } ?: return emptyList()
        return memberIds
            .withIndex()
            .filterNot { (_, tabId) -> tabId == anchorTabId }
            .sortedBy { (index) -> (index - anchorIndex).absoluteValue }
            .take(limit)
            .map { (_, tabId) -> tabId }
    }

    fun heroEdgeCenterX(
        viewportLeft: Float,
        viewportRight: Float,
        cardWidth: Float,
        startsBeforeAnchor: Boolean,
    ): Float {
        if (
            !viewportLeft.isFinite() ||
            !viewportRight.isFinite() ||
            !cardWidth.isFinite() ||
            viewportRight < viewportLeft
        ) {
            return 0f
        }
        val overhang = cardWidth.coerceAtLeast(0f) * HERO_EDGE_CARD_OVERHANG_FRACTION
        return if (startsBeforeAnchor) viewportLeft - overhang else viewportRight + overhang
    }

    fun overviewZIndex(isMotionMember: Boolean, isAnchor: Boolean): Float = when {
        !isMotionMember -> 0f
        isAnchor -> 2f
        else -> 1f
    }

    fun overviewTransform(
        phase: TabStackMotionPhase,
        progress: Float,
        deltaX: Float,
        deltaY: Float,
        memberIndex: Int,
        isAnchor: Boolean,
    ): TabStackCardTransform {
        val boundedProgress = when {
            progress.isNaN() || progress == Float.NEGATIVE_INFINITY -> 0f
            progress == Float.POSITIVE_INFINITY -> 1f
            else -> progress.coerceIn(0f, 1f)
        }
        val safeDeltaX = deltaX.takeIf(Float::isFinite) ?: 0f
        val safeDeltaY = deltaY.takeIf(Float::isFinite) ?: 0f
        if (isAnchor) {
            val pulse = sin(boundedProgress * Math.PI).toFloat() * 0.025f
            return TabStackCardTransform(
                translationX = 0f,
                translationY = 0f,
                scale = 1f - pulse,
                rotationZ = 0f,
                alpha = 1f,
            )
        }
        val direction = if (memberIndex % 2 == 0) -1f else 1f
        return when (phase) {
            TabStackMotionPhase.Collapsing -> TabStackCardTransform(
                translationX = safeDeltaX * boundedProgress,
                translationY = safeDeltaY * boundedProgress,
                scale = 1f - 0.09f * boundedProgress,
                rotationZ = direction * 3.5f * boundedProgress,
                alpha = 1f - ((boundedProgress - 0.58f) / 0.42f).coerceIn(0f, 1f),
            )
            TabStackMotionPhase.Expanding -> {
                val foldedProgress = 1f - boundedProgress
                TabStackCardTransform(
                    translationX = safeDeltaX * foldedProgress,
                    translationY = safeDeltaY * foldedProgress,
                    scale = 0.91f + 0.09f * boundedProgress,
                    rotationZ = direction * 3.5f * foldedProgress,
                    alpha = ((boundedProgress + 0.04f) / 0.64f).coerceIn(0f, 1f),
                )
            }
        }
    }

    fun folderCoverflowTransform(pageOffset: Float): TabStackCardTransform {
        val boundedOffset = if (pageOffset.isFinite()) {
            pageOffset.coerceIn(-1.5f, 1.5f)
        } else {
            0f
        }
        val distance = boundedOffset.absoluteValue
        return TabStackCardTransform(
            translationX = 0f,
            translationY = distance * 18f,
            scale = 1f - distance.coerceAtMost(1f) * 0.1f,
            rotationZ = boundedOffset * -5.5f,
            alpha = 1f - distance.coerceAtMost(1f) * 0.34f,
        )
    }
}
