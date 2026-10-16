package dev.sk2andy.materialbrowser.constellations

internal data class ConstellationGraphLabelAnchor(
    val id: String,
    val x: Float,
    val y: Float,
    val nodeRadius: Float,
    val labelWidth: Float = 0f,
    val labelHeight: Float = 0f,
    val selected: Boolean = false,
)

internal data class ConstellationGraphLabelPlacement(
    val id: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/** Labels yield to nodes and earlier labels; hidden labels remain available in the page list. */
internal object ConstellationGraphLabelRules {
    fun layout(
        anchors: List<ConstellationGraphLabelAnchor>,
        width: Float,
        height: Float,
        padding: Float,
        gap: Float,
    ): List<ConstellationGraphLabelPlacement> {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f ||
            !padding.isFinite() || !gap.isFinite() || padding < 0f || gap < 0f
        ) return emptyList()
        val validAnchors = anchors.filter { anchor ->
            anchor.x.isFinite() && anchor.y.isFinite() && anchor.nodeRadius.isFinite() &&
                anchor.nodeRadius >= 0f
        }
        val placements = mutableListOf<ConstellationGraphLabelPlacement>()
        validAnchors.asSequence()
            .filter { anchor ->
                anchor.x in 0f..width && anchor.y in 0f..height &&
                    anchor.labelWidth.isFinite() && anchor.labelHeight.isFinite() &&
                    anchor.labelWidth > 0f && anchor.labelHeight > 0f &&
                    anchor.labelWidth <= width - padding * 2 &&
                    anchor.labelHeight <= height - padding * 2
            }
            .sortedWith(compareByDescending<ConstellationGraphLabelAnchor> { it.selected }.thenBy { it.id })
            .forEach { anchor ->
                val placement = candidates(anchor, gap).map { (left, top) ->
                    ConstellationGraphLabelPlacement(
                        id = anchor.id,
                        left = left.coerceIn(padding, width - padding - anchor.labelWidth),
                        top = top.coerceIn(padding, height - padding - anchor.labelHeight),
                        width = anchor.labelWidth,
                        height = anchor.labelHeight,
                    )
                }.firstOrNull { candidate ->
                    placements.none { existing -> overlaps(candidate, existing, gap) } &&
                        validAnchors.none { obstacle -> intersectsNode(candidate, obstacle, gap) }
                }
                if (placement != null) placements += placement
            }
        return placements
    }

    private fun candidates(anchor: ConstellationGraphLabelAnchor, gap: Float): Sequence<Pair<Float, Float>> = sequence {
        repeat(3) { ring ->
            val distance = anchor.nodeRadius + gap + ring * (anchor.labelHeight + gap)
            val centerX = anchor.x - anchor.labelWidth / 2
            val centerY = anchor.y - anchor.labelHeight / 2
            yield(centerX to anchor.y + distance)
            yield(centerX to anchor.y - distance - anchor.labelHeight)
            yield(anchor.x + distance to centerY)
            yield(anchor.x - distance - anchor.labelWidth to centerY)
            yield(anchor.x + distance to anchor.y + distance)
            yield(anchor.x - distance - anchor.labelWidth to anchor.y + distance)
            yield(anchor.x + distance to anchor.y - distance - anchor.labelHeight)
            yield(anchor.x - distance - anchor.labelWidth to anchor.y - distance - anchor.labelHeight)
        }
    }

    private fun overlaps(
        left: ConstellationGraphLabelPlacement,
        right: ConstellationGraphLabelPlacement,
        gap: Float,
    ): Boolean = left.left < right.right + gap && left.right + gap > right.left &&
        left.top < right.bottom + gap && left.bottom + gap > right.top

    private fun intersectsNode(
        label: ConstellationGraphLabelPlacement,
        node: ConstellationGraphLabelAnchor,
        gap: Float,
    ): Boolean {
        val dx = node.x - node.x.coerceIn(label.left, label.right)
        val dy = node.y - node.y.coerceIn(label.top, label.bottom)
        val radius = node.nodeRadius + gap
        return dx * dx + dy * dy < radius * radius
    }
}
