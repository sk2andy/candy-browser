package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.CandyTrail

/** Presentation-only projections. The underlying trail and engine history stay unchanged. */
internal object CandyTrailPresentationRules {
    const val DETAIL_SCALE = 0.65f
    const val OVERVIEW_SCALE = 0.3f

    fun symbolScale(scale: Float): Float = scale.coerceAtLeast(OVERVIEW_SCALE)

    fun nearestTarget(
        layout: CandyTrailLayout,
        x: Float,
        y: Float,
    ): CandyTrailGraphTarget? {
        val targets = layout.positions.map {
            Triple(CandyTrailGraphTarget.Node(it.nodeId), it.x, it.y)
        } + layout.forkPositions.map {
            Triple(CandyTrailGraphTarget.Fork(it.forkId), it.x, it.y)
        }
        return targets.minByOrNull { (_, left, top) ->
            val dx = left + CandyTrailLayoutRules.NODE_WIDTH / 2f - x
            val dy = top + CandyTrailLayoutRules.NODE_HEIGHT / 2f - y
            dx * dx + dy * dy
        }?.first
    }

    fun compact(scale: Float): Boolean = scale < DETAIL_SCALE

    fun activeAncestry(trail: CandyTrail): Set<String> {
        val nodes = trail.nodes.associateBy { it.id }
        val route = linkedSetOf<String>()
        var cursor = trail.currentNodeId?.let(nodes::get) ?: return emptySet()
        while (route.add(cursor.id)) cursor = cursor.parentId?.let(nodes::get) ?: break
        return route
    }

    fun visibleTrail(
        trail: CandyTrail,
        focusRoute: Boolean,
        collapsedNodeIds: Set<String> = emptySet(),
    ): CandyTrail {
        val route = activeAncestry(trail)
        if (focusRoute && route.isNotEmpty()) {
            return trail.copy(nodes = trail.nodes.filter { it.id in route }, forks = emptyList())
        }
        if (collapsedNodeIds.isEmpty()) return trail
        val nodes = trail.nodes.associateBy { it.id }
        val visibleIds = trail.nodes.filter { node ->
            if (node.id in route) return@filter true
            val visited = mutableSetOf(node.id)
            var ancestorId = node.parentId
            while (ancestorId != null && visited.add(ancestorId)) {
                if (ancestorId in collapsedNodeIds) return@filter false
                ancestorId = nodes[ancestorId]?.parentId
            }
            true
        }.mapTo(mutableSetOf()) { it.id }
        return trail.copy(
            nodes = trail.nodes.filter { it.id in visibleIds },
            forks = trail.forks.filter { it.originNodeId in visibleIds && it.originNodeId !in collapsedNodeIds },
        )
    }

    fun hiddenCount(trail: CandyTrail, nodeId: String): Int {
        val projection = visibleTrail(trail, false, setOf(nodeId))
        return trail.nodes.size + trail.forks.size - projection.nodes.size - projection.forks.size
    }

    fun interpolate(
        from: CandyTrailLayout,
        to: CandyTrailLayout,
        progress: Float,
    ): CandyTrailLayout {
        val fraction = progress.takeIf(Float::isFinite)?.coerceIn(-0.2f, 1.2f) ?: 1f
        fun mix(start: Float, end: Float) = start + (end - start) * fraction
        val previousNodes = from.positions.associateBy { it.nodeId }
        val previousForks = from.forkPositions.associateBy { it.forkId }
        return to.copy(
            positions = to.positions.map { target ->
                val start = previousNodes[target.nodeId] ?: return@map target
                target.copy(x = mix(start.x, target.x), y = mix(start.y, target.y))
            },
            forkPositions = to.forkPositions.map { target ->
                val start = previousForks[target.forkId] ?: return@map target
                target.copy(x = mix(start.x, target.x), y = mix(start.y, target.y))
            },
            width = mix(from.width, to.width).coerceAtLeast(to.width),
            height = mix(from.height, to.height).coerceAtLeast(to.height),
        )
    }
}
