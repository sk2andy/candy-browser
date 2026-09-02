package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.AddressBarActionLayoutRules
import dev.sk2andy.materialbrowser.data.AddressBarActionSide
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal data class AddressBarActionEditorTarget(
    val side: AddressBarActionSide,
    val index: Int,
)

internal data class AddressBarActionEditorRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal data class AddressBarActionEditorPoint(
    val x: Float,
    val y: Float,
)

private val AddressBarActionEditorRect.center: AddressBarActionEditorPoint
    get() = AddressBarActionEditorPoint(
        x = (left + right) / 2f,
        y = (top + bottom) / 2f,
    )

internal data class AddressBarActionEditorSlot(
    val target: AddressBarActionEditorTarget,
    val bounds: AddressBarActionEditorRect,
    val indicatorCenter: AddressBarActionEditorPoint = bounds.center,
)

internal data class AddressBarActionDragResistance(
    val visibleX: Float,
    val visibleY: Float,
    val breakawayProgress: Float,
    val breakawayReached: Boolean,
)

internal sealed interface AddressBarActionDropDecision {
    data class Accepted(
        val layout: AddressBarActionLayout,
        val changed: Boolean,
    ) : AddressBarActionDropDecision

    data class Rejected(
        val reason: AddressBarActionDropRejection,
    ) : AddressBarActionDropDecision
}

internal enum class AddressBarActionDropRejection {
    InvalidLayout,
    InvalidTarget,
    Full,
}

internal object AddressBarActionEditorRules {
    const val MAX_ACTIONS = AddressBarActionLayoutRules.MAX_CONFIGURABLE_ACTIONS
    const val DEFAULT_RESISTANCE_FACTOR = 0.14f

    fun insertionSlotBounds(
        actionBounds: AddressBarActionEditorRect,
        insertBefore: Boolean,
        isRtl: Boolean,
    ): AddressBarActionEditorRect {
        val centerX = (actionBounds.left + actionBounds.right) / 2f
        val useLeftHalf = insertBefore != isRtl
        return if (useLeftHalf) {
            actionBounds.copy(right = centerX)
        } else {
            actionBounds.copy(left = centerX)
        }
    }

    fun insertionSlots(
        side: AddressBarActionSide,
        actionBounds: List<AddressBarActionEditorRect>,
        emptyBounds: AddressBarActionEditorRect?,
        isRtl: Boolean,
    ): List<AddressBarActionEditorSlot> {
        if (actionBounds.isEmpty()) {
            return emptyBounds
                ?.takeIf { it.isValid() }
                ?.let { bounds ->
                    listOf(
                        AddressBarActionEditorSlot(
                            target = AddressBarActionEditorTarget(side, 0),
                            bounds = bounds,
                        ),
                    )
                }
                .orEmpty()
        }
        if (actionBounds.any { !it.isValid() }) return emptyList()

        return buildList(actionBounds.size + 1) {
            actionBounds.forEachIndexed { index, bounds ->
                if (index == 0) {
                    add(
                        AddressBarActionEditorSlot(
                            target = AddressBarActionEditorTarget(side, 0),
                            bounds = insertionSlotBounds(
                                actionBounds = bounds,
                                insertBefore = true,
                                isRtl = isRtl,
                            ),
                            indicatorCenter = AddressBarActionEditorPoint(
                                x = if (isRtl) bounds.right else bounds.left,
                                y = bounds.center.y,
                            ),
                        ),
                    )
                }

                val nextBounds = actionBounds.getOrNull(index + 1)
                if (nextBounds == null) {
                    add(
                        AddressBarActionEditorSlot(
                            target = AddressBarActionEditorTarget(side, actionBounds.size),
                            bounds = insertionSlotBounds(
                                actionBounds = bounds,
                                insertBefore = false,
                                isRtl = isRtl,
                            ),
                            indicatorCenter = AddressBarActionEditorPoint(
                                x = if (isRtl) bounds.left else bounds.right,
                                y = bounds.center.y,
                            ),
                        ),
                    )
                } else {
                    add(
                        AddressBarActionEditorSlot(
                            target = AddressBarActionEditorTarget(side, index + 1),
                            bounds = AddressBarActionEditorRect(
                                left = min(bounds.center.x, nextBounds.center.x),
                                top = min(bounds.top, nextBounds.top),
                                right = max(bounds.center.x, nextBounds.center.x),
                                bottom = max(bounds.bottom, nextBounds.bottom),
                            ),
                            indicatorCenter = AddressBarActionEditorPoint(
                                x = if (isRtl) {
                                    (bounds.left + nextBounds.right) / 2f
                                } else {
                                    (bounds.right + nextBounds.left) / 2f
                                },
                                y = (bounds.center.y + nextBounds.center.y) / 2f,
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun canonicalSlotsAfterRemoving(
        slots: List<AddressBarActionEditorSlot>,
        removedSide: AddressBarActionSide,
        removedIndex: Int,
        removedBounds: AddressBarActionEditorRect,
    ): List<AddressBarActionEditorSlot> {
        if (removedIndex < 0 || !removedBounds.isValid()) return slots
        return slots
            .map { slot ->
                if (slot.target.side == removedSide && slot.target.index > removedIndex) {
                    slot.copy(target = slot.target.copy(index = slot.target.index - 1))
                } else {
                    slot
                }
            }
            .groupBy(AddressBarActionEditorSlot::target)
            .map { (target, matchingSlots) ->
                if (matchingSlots.size == 1) {
                    matchingSlots.single()
                } else {
                    AddressBarActionEditorSlot(
                        target = target,
                        bounds = matchingSlots
                            .map(AddressBarActionEditorSlot::bounds)
                            .reduce { accumulated, next -> accumulated.union(next) },
                        indicatorCenter = removedBounds.center,
                    )
                }
            }
    }

    fun dragResistance(
        dragX: Float,
        dragY: Float,
        breakawayDistancePx: Float,
        resistanceFactor: Float = DEFAULT_RESISTANCE_FACTOR,
    ): AddressBarActionDragResistance {
        val safeX = dragX.takeIf(Float::isFinite) ?: 0f
        val safeY = dragY.takeIf(Float::isFinite) ?: 0f
        if (!breakawayDistancePx.isFinite() || breakawayDistancePx <= 0f) {
            return AddressBarActionDragResistance(
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
        return AddressBarActionDragResistance(
            visibleX = if (reached) safeX else safeX * safeResistance,
            visibleY = if (reached) safeY else safeY * safeResistance,
            breakawayProgress = progress,
            breakawayReached = reached,
        )
    }

    fun hitTarget(
        pointerX: Float,
        pointerY: Float,
        slots: List<AddressBarActionEditorSlot>,
    ): AddressBarActionEditorTarget? = targetAt(
        pointerX = pointerX,
        pointerY = pointerY,
        slots = slots,
        paddingPx = 0f,
    )

    fun snappedTarget(
        pointerX: Float,
        pointerY: Float,
        slots: List<AddressBarActionEditorSlot>,
        currentTarget: AddressBarActionEditorTarget?,
        enterPaddingPx: Float,
        retainPaddingPx: Float,
    ): AddressBarActionEditorTarget? {
        if (!pointerX.isFinite() || !pointerY.isFinite()) return null
        val safeEnterPadding = enterPaddingPx.safePadding()
        val safeRetainPadding = maxOf(retainPaddingPx.safePadding(), safeEnterPadding)
        val retainedSlot = currentTarget?.let { target ->
            slots.firstOrNull { slot ->
                slot.target == target &&
                    slot.target.isValid() &&
                    slot.bounds.contains(pointerX, pointerY, safeRetainPadding)
            }
        }
        if (retainedSlot != null) return retainedSlot.target

        return targetAt(
            pointerX = pointerX,
            pointerY = pointerY,
            slots = slots,
            paddingPx = safeEnterPadding,
        )
    }

    fun isValidLayout(layout: AddressBarActionLayout): Boolean =
        AddressBarActionLayoutRules.normalize(layout) == layout

    fun drop(
        layout: AddressBarActionLayout,
        action: AddressBarAction,
        target: AddressBarActionEditorTarget,
    ): AddressBarActionDropDecision {
        if (!isValidLayout(layout)) {
            return AddressBarActionDropDecision.Rejected(
                AddressBarActionDropRejection.InvalidLayout,
            )
        }
        if (!target.isValid()) {
            return AddressBarActionDropDecision.Rejected(
                AddressBarActionDropRejection.InvalidTarget,
            )
        }

        val wasAssigned = action in layout.beforeAddress || action in layout.afterAddress
        if (
            !wasAssigned &&
            layout.beforeAddress.size + layout.afterAddress.size >= MAX_ACTIONS
        ) {
            return AddressBarActionDropDecision.Rejected(AddressBarActionDropRejection.Full)
        }

        val updatedBefore = layout.beforeAddress.filterNot { it == action }.toMutableList()
        val updatedAfter = layout.afterAddress.filterNot { it == action }.toMutableList()
        val targetList = when (target.side) {
            AddressBarActionSide.BeforeAddress -> updatedBefore
            AddressBarActionSide.AfterAddress -> updatedAfter
        }
        if (target.index > targetList.size) {
            return AddressBarActionDropDecision.Rejected(
                AddressBarActionDropRejection.InvalidTarget,
            )
        }
        targetList.add(target.index, action)

        val updated = AddressBarActionLayout(
            beforeAddress = updatedBefore.toList(),
            afterAddress = updatedAfter.toList(),
        )
        if (!isValidLayout(updated)) {
            return AddressBarActionDropDecision.Rejected(
                AddressBarActionDropRejection.InvalidLayout,
            )
        }
        return AddressBarActionDropDecision.Accepted(
            layout = updated,
            changed = updated != layout,
        )
    }

    private fun targetAt(
        pointerX: Float,
        pointerY: Float,
        slots: List<AddressBarActionEditorSlot>,
        paddingPx: Float,
    ): AddressBarActionEditorTarget? {
        if (!pointerX.isFinite() || !pointerY.isFinite()) return null
        val safePadding = paddingPx.safePadding()
        return slots.asSequence()
            .filter { slot ->
                slot.target.isValid() &&
                    slot.bounds.contains(pointerX, pointerY, safePadding)
            }
            .minWithOrNull(
                compareBy<AddressBarActionEditorSlot> {
                    it.bounds.squaredDistanceFromCenter(pointerX, pointerY)
                }.thenBy { it.target.side.ordinal }
                    .thenBy { it.target.index },
            )
            ?.target
    }

    private fun AddressBarActionEditorTarget.isValid(): Boolean = index >= 0

    private fun AddressBarActionEditorRect.contains(
        x: Float,
        y: Float,
        paddingPx: Float,
    ): Boolean = isValid() &&
        x >= left - paddingPx &&
        x <= right + paddingPx &&
        y >= top - paddingPx &&
        y <= bottom + paddingPx

    private fun AddressBarActionEditorRect.isValid(): Boolean =
        left.isFinite() &&
            top.isFinite() &&
            right.isFinite() &&
            bottom.isFinite() &&
            right > left &&
            bottom > top

    private fun AddressBarActionEditorRect.union(
        other: AddressBarActionEditorRect,
    ): AddressBarActionEditorRect = AddressBarActionEditorRect(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom),
    )

    private fun AddressBarActionEditorRect.squaredDistanceFromCenter(
        x: Float,
        y: Float,
    ): Double {
        val deltaX = x.toDouble() - (left.toDouble() + right.toDouble()) / 2.0
        val deltaY = y.toDouble() - (top.toDouble() + bottom.toDouble()) / 2.0
        return deltaX * deltaX + deltaY * deltaY
    }

    private fun Float.safePadding(): Float =
        takeIf { it.isFinite() && it >= 0f } ?: 0f
}
