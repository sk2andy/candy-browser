package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

internal object AddressBarInsetRules {
    fun bottomPaddingPx(
        fullWindowHeightPx: Int,
        rootBottomInWindowPx: Int,
        imeBottomPx: Int,
        navigationBottomPx: Int,
    ): Int {
        val occupiedBottomPx = maxOf(imeBottomPx, navigationBottomPx).coerceAtLeast(0)
        if (fullWindowHeightPx <= 0 || rootBottomInWindowPx <= 0) return occupiedBottomPx

        val alreadyResizedByPx = (fullWindowHeightPx - rootBottomInWindowPx).coerceAtLeast(0)
        return (occupiedBottomPx - alreadyResizedByPx).coerceAtLeast(0)
    }
}

/**
 * Applies root-owned browser chrome insets. Call only for a bar anchored directly to the measured
 * browser root; unlike standard inset padding, this modifier intentionally does not consume insets
 * for descendants.
 */
internal fun Modifier.addressBarWindowInsetsPadding(
    fullWindowHeightPx: Int,
    rootBottomInWindowPx: Int,
    imeInsets: WindowInsets,
    navigationBarInsets: WindowInsets,
): Modifier = layout { measurable, constraints ->
    val leftPaddingPx = navigationBarInsets.getLeft(this, layoutDirection)
    val topPaddingPx = navigationBarInsets.getTop(this)
    val rightPaddingPx = navigationBarInsets.getRight(this, layoutDirection)
    val bottomPaddingPx = AddressBarInsetRules.bottomPaddingPx(
        fullWindowHeightPx = fullWindowHeightPx,
        rootBottomInWindowPx = rootBottomInWindowPx,
        imeBottomPx = imeInsets.getBottom(this),
        navigationBottomPx = navigationBarInsets.getBottom(this),
    )
    val horizontalPaddingPx = leftPaddingPx + rightPaddingPx
    val verticalPaddingPx = topPaddingPx + bottomPaddingPx
    val childConstraints = Constraints(
        minWidth = (constraints.minWidth - horizontalPaddingPx).coerceAtLeast(0),
        maxWidth = (constraints.maxWidth - horizontalPaddingPx).coerceAtLeast(0),
        minHeight = (constraints.minHeight - verticalPaddingPx).coerceAtLeast(0),
        maxHeight = (constraints.maxHeight - verticalPaddingPx).coerceAtLeast(0),
    )
    val placeable = measurable.measure(
        childConstraints,
    )
    val width = (placeable.width + horizontalPaddingPx)
        .coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = (placeable.height + verticalPaddingPx)
        .coerceIn(constraints.minHeight, constraints.maxHeight)

    layout(width, height) {
        placeable.placeRelative(leftPaddingPx, topPaddingPx)
    }
}
