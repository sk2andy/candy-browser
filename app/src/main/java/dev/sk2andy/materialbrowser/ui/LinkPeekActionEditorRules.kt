package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.data.LinkPeekAction
import dev.sk2andy.materialbrowser.data.LinkPeekActionLayout
import dev.sk2andy.materialbrowser.data.LinkPeekActionLayoutRules
import kotlin.math.hypot

internal sealed interface LinkPeekActionEditorTarget {
    data class ActionSlot(val actionIndex: Int) : LinkPeekActionEditorTarget

    data object FixedPlus : LinkPeekActionEditorTarget

    data object Palette : LinkPeekActionEditorTarget
}

internal data class LinkPeekActionEditorRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal data class LinkPeekActionEditorSlot(
    val target: LinkPeekActionEditorTarget,
    val bounds: LinkPeekActionEditorRect,
)

internal data class LinkPeekActionEditorDragResistance(
    val visibleX: Float,
    val visibleY: Float,
    val breakawayProgress: Float,
    val breakawayReached: Boolean,
)

internal sealed interface LinkPeekActionEditorDropDecision {
    data class Accepted(
        val layout: LinkPeekActionLayout,
        val changed: Boolean,
    ) : LinkPeekActionEditorDropDecision

    data class Rejected(
        val reason: LinkPeekActionEditorDropRejection,
    ) : LinkPeekActionEditorDropDecision
}

internal enum class LinkPeekActionEditorDropRejection {
    InvalidLayout,
    InvalidTarget,
    Occupied,
    FixedPlus,
}

internal object LinkPeekActionEditorRules {
    const val DEFAULT_RESISTANCE_FACTOR = 0.14f

    fun actionSlots(
        actionBounds: List<LinkPeekActionEditorRect>,
        fixedPlusBounds: LinkPeekActionEditorRect?,
    ): List<LinkPeekActionEditorSlot> {
        if (
            actionBounds.size != LinkPeekActionLayoutRules.CONFIGURABLE_ACTION_COUNT ||
            actionBounds.any { bounds -> !bounds.isValid() } ||
            fixedPlusBounds?.isValid() != true
        ) {
            return emptyList()
        }
        return buildList(LinkPeekActionLayoutRules.TOOLBAR_SLOT_COUNT) {
            actionBounds.forEachIndexed { actionIndex, bounds ->
                add(
                    LinkPeekActionEditorSlot(
                        target = LinkPeekActionEditorTarget.ActionSlot(actionIndex),
                        bounds = bounds,
                    ),
                )
            }
            add(
                LinkPeekActionEditorSlot(
                    target = LinkPeekActionEditorTarget.FixedPlus,
                    bounds = fixedPlusBounds,
                ),
            )
        }.sortedBy { slot -> slot.target.visualSlotIndex() }
    }

    fun dragResistance(
        dragX: Float,
        dragY: Float,
        breakawayDistancePx: Float,
        resistanceFactor: Float = DEFAULT_RESISTANCE_FACTOR,
    ): LinkPeekActionEditorDragResistance {
        val safeX = dragX.takeIf(Float::isFinite) ?: 0f
        val safeY = dragY.takeIf(Float::isFinite) ?: 0f
        if (!breakawayDistancePx.isFinite() || breakawayDistancePx <= 0f) {
            return LinkPeekActionEditorDragResistance(
                visibleX = safeX,
                visibleY = safeY,
                breakawayProgress = 0f,
                breakawayReached = false,
            )
        }

        val distance = hypot(safeX, safeY)
        val progress = (distance / breakawayDistancePx).coerceIn(0f, 1f)
        val reached = distance >= breakawayDistancePx
        val safeResistance = resistanceFactor
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: DEFAULT_RESISTANCE_FACTOR
        return LinkPeekActionEditorDragResistance(
            visibleX = if (reached) safeX else safeX * safeResistance,
            visibleY = if (reached) safeY else safeY * safeResistance,
            breakawayProgress = progress,
            breakawayReached = reached,
        )
    }

    fun availableDropSlots(
        layout: LinkPeekActionLayout,
        slots: List<LinkPeekActionEditorSlot>,
    ): List<LinkPeekActionEditorSlot> {
        val normalized = LinkPeekActionLayoutRules.normalize(layout)
        return slots.filter { slot ->
            val actionIndex = (slot.target as? LinkPeekActionEditorTarget.ActionSlot)
                ?.actionIndex
                ?: return@filter false
            normalized.actions.getOrNull(actionIndex) == null
        }
    }

    fun snappedTarget(
        pointerX: Float,
        pointerY: Float,
        slots: List<LinkPeekActionEditorSlot>,
        currentTarget: LinkPeekActionEditorTarget?,
        enterPaddingPx: Float,
        retainPaddingPx: Float,
    ): LinkPeekActionEditorTarget? {
        if (!pointerX.isFinite() || !pointerY.isFinite()) return null
        val safeEnterPadding = enterPaddingPx.safePadding()
        val safeRetainPadding = maxOf(retainPaddingPx.safePadding(), safeEnterPadding)
        val retained = currentTarget?.let { target ->
            slots.firstOrNull { slot ->
                slot.target == target &&
                    slot.target.isValid() &&
                    slot.bounds.contains(pointerX, pointerY, safeRetainPadding)
            }
        }
        if (retained != null) return retained.target

        return slots.asSequence()
            .filter { slot ->
                slot.target.isValid() &&
                    slot.bounds.contains(pointerX, pointerY, safeEnterPadding)
            }
            .minWithOrNull(
                compareBy<LinkPeekActionEditorSlot> { slot ->
                    slot.bounds.squaredDistanceFromCenter(pointerX, pointerY)
                }.thenBy { slot -> slot.target.visualSlotIndex() },
            )
            ?.target
    }

    fun isValidLayout(layout: LinkPeekActionLayout): Boolean =
        LinkPeekActionLayoutRules.normalize(layout) == layout

    fun drop(
        layout: LinkPeekActionLayout,
        action: LinkPeekAction,
        target: LinkPeekActionEditorTarget,
    ): LinkPeekActionEditorDropDecision {
        if (!isValidLayout(layout)) {
            return LinkPeekActionEditorDropDecision.Rejected(
                LinkPeekActionEditorDropRejection.InvalidLayout,
            )
        }
        return when (target) {
            LinkPeekActionEditorTarget.FixedPlus -> LinkPeekActionEditorDropDecision.Rejected(
                LinkPeekActionEditorDropRejection.FixedPlus,
            )

            LinkPeekActionEditorTarget.Palette -> {
                val updated = LinkPeekActionLayoutRules.remove(layout, action)
                LinkPeekActionEditorDropDecision.Accepted(
                    layout = updated,
                    changed = updated != layout,
                )
            }

            is LinkPeekActionEditorTarget.ActionSlot -> {
                if (target.actionIndex !in layout.actions.indices) {
                    LinkPeekActionEditorDropDecision.Rejected(
                        LinkPeekActionEditorDropRejection.InvalidTarget,
                    )
                } else if (layout.actions[target.actionIndex] != null) {
                    LinkPeekActionEditorDropDecision.Rejected(
                        LinkPeekActionEditorDropRejection.Occupied,
                    )
                } else {
                    val updated = LinkPeekActionLayoutRules.place(
                        layout = layout,
                        action = action,
                        targetActionIndex = target.actionIndex,
                    )
                    LinkPeekActionEditorDropDecision.Accepted(
                        layout = updated,
                        changed = updated != layout,
                    )
                }
            }
        }
    }

    private fun LinkPeekActionEditorTarget.isValid(): Boolean = when (this) {
        LinkPeekActionEditorTarget.FixedPlus -> true
        LinkPeekActionEditorTarget.Palette -> false
        is LinkPeekActionEditorTarget.ActionSlot ->
            actionIndex in 0 until LinkPeekActionLayoutRules.CONFIGURABLE_ACTION_COUNT
    }

    private fun LinkPeekActionEditorTarget.visualSlotIndex(): Int = when (this) {
        LinkPeekActionEditorTarget.FixedPlus -> LinkPeekActionLayoutRules.FIXED_PLUS_SLOT_INDEX
        LinkPeekActionEditorTarget.Palette -> Int.MAX_VALUE
        is LinkPeekActionEditorTarget.ActionSlot -> if (
            actionIndex < LinkPeekActionLayoutRules.FIXED_PLUS_SLOT_INDEX
        ) {
            actionIndex
        } else {
            actionIndex + 1
        }
    }

    private fun LinkPeekActionEditorRect.contains(
        x: Float,
        y: Float,
        paddingPx: Float,
    ): Boolean = isValid() &&
        x >= left - paddingPx &&
        x <= right + paddingPx &&
        y >= top - paddingPx &&
        y <= bottom + paddingPx

    private fun LinkPeekActionEditorRect.isValid(): Boolean =
        left.isFinite() &&
            top.isFinite() &&
            right.isFinite() &&
            bottom.isFinite() &&
            right > left &&
            bottom > top

    private fun LinkPeekActionEditorRect.squaredDistanceFromCenter(
        x: Float,
        y: Float,
    ): Double {
        val deltaX = x.toDouble() - (left.toDouble() + right.toDouble()) / 2.0
        val deltaY = y.toDouble() - (top.toDouble() + bottom.toDouble()) / 2.0
        return deltaX * deltaX + deltaY * deltaY
    }

    private fun Float.safePadding(): Float =
        takeIf { value -> value.isFinite() && value >= 0f } ?: 0f
}
