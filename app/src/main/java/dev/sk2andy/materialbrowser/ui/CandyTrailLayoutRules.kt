package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailFork
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import kotlin.math.max

internal data class CandyTrailNodePosition(
    val nodeId: String,
    val x: Float,
    val y: Float,
    val depth: Int,
)

internal data class CandyTrailForkPosition(
    val forkId: String,
    val originNodeId: String,
    val x: Float,
    val y: Float,
    val depth: Int,
)

internal data class CandyTrailLayout(
    val positions: List<CandyTrailNodePosition>,
    val forkPositions: List<CandyTrailForkPosition>,
    val width: Float,
    val height: Float,
)

internal object CandyTrailLayoutRules {
    const val NODE_WIDTH = 188f
    const val NODE_HEIGHT = 112f
    private const val HORIZONTAL_GAP = 104f
    private const val VERTICAL_GAP = 34f
    private const val MARGIN = 56f

    fun layout(trail: CandyTrail): CandyTrailLayout {
        if (trail.nodes.isEmpty()) return CandyTrailLayout(emptyList(), emptyList(), 0f, 0f)
        val byId = trail.nodes.associateBy(CandyTrailNode::id)
        val children = trail.nodes.groupBy(CandyTrailNode::parentId)
        val forks = trail.forks.groupBy(CandyTrailFork::originNodeId)
        val roots = trail.nodes.filter { it.parentId == null || it.parentId !in byId }
            .sortedWith(compareBy<CandyTrailNode> { it.visitedAt }.thenBy { it.id })
        val rows = mutableMapOf<String, Float>()
        val forkRows = mutableMapOf<String, Float>()
        val depths = mutableMapOf<String, Int>()
        val forkDepths = mutableMapOf<String, Int>()
        val visiting = mutableSetOf<String>()
        var nextRow = 0f

        fun placeFork(fork: CandyTrailFork, depth: Int): Float = forkRows.getOrPut(fork.id) {
            forkDepths[fork.id] = depth
            nextRow++
        }

        fun place(node: CandyTrailNode, depth: Int): Float {
            rows[node.id]?.let { return it }
            if (!visiting.add(node.id)) return nextRow++
            depths[node.id] = depth
            val nodeChildren = children[node.id].orEmpty().map { child ->
                CandyTrailLayoutChild(
                    node = child,
                    fork = null,
                    sortAt = child.visitedAt,
                    sortId = child.id,
                )
            }
            val forkChildren = forks[node.id].orEmpty().map { fork ->
                CandyTrailLayoutChild(
                    node = null,
                    fork = fork,
                    sortAt = fork.createdAt,
                    sortId = fork.id,
                )
            }
            val graphChildren = (nodeChildren + forkChildren)
                .sortedWith(compareBy<CandyTrailLayoutChild> { it.sortAt }.thenBy { it.sortId })
            val row = if (graphChildren.isEmpty()) {
                nextRow++
            } else {
                val childRows = graphChildren.map { child ->
                    child.node?.let { place(it, depth + 1) }
                        ?: placeFork(checkNotNull(child.fork), depth + 1)
                }
                (childRows.first() + childRows.last()) / 2f
            }
            visiting.remove(node.id)
            rows[node.id] = row
            return row
        }

        roots.forEach { root -> place(root, 0) }
        trail.nodes.filterNot { it.id in rows }.forEach { orphan -> place(orphan, 0) }
        val positions = trail.nodes.map { node ->
            val depth = depths[node.id] ?: 0
            val organicOffset = ((node.id.hashCode() and 0x7fffffff) % 17 - 8).toFloat()
            CandyTrailNodePosition(
                nodeId = node.id,
                x = MARGIN + depth * (NODE_WIDTH + HORIZONTAL_GAP) + organicOffset,
                y = MARGIN + (rows[node.id] ?: 0f) * (NODE_HEIGHT + VERTICAL_GAP),
                depth = depth,
            )
        }.sortedWith(compareBy<CandyTrailNodePosition> { it.depth }.thenBy { it.y }.thenBy { it.nodeId })
        val forkPositions = trail.forks.mapNotNull { fork ->
            val depth = forkDepths[fork.id] ?: return@mapNotNull null
            val row = forkRows[fork.id] ?: return@mapNotNull null
            val organicOffset = ((fork.id.hashCode() and 0x7fffffff) % 13 - 6).toFloat()
            CandyTrailForkPosition(
                forkId = fork.id,
                originNodeId = fork.originNodeId,
                x = MARGIN + depth * (NODE_WIDTH + HORIZONTAL_GAP) + organicOffset,
                y = MARGIN + row * (NODE_HEIGHT + VERTICAL_GAP),
                depth = depth,
            )
        }.sortedWith(compareBy<CandyTrailForkPosition> { it.depth }.thenBy { it.y }.thenBy { it.forkId })
        val allX = positions.map(CandyTrailNodePosition::x) +
            forkPositions.map(CandyTrailForkPosition::x)
        val allY = positions.map(CandyTrailNodePosition::y) +
            forkPositions.map(CandyTrailForkPosition::y)
        val width = allX.maxOrNull()!! + NODE_WIDTH + MARGIN
        val height = max(
            allY.maxOrNull()!! + NODE_HEIGHT + MARGIN,
            NODE_HEIGHT + MARGIN * 2f,
        )
        return CandyTrailLayout(positions, forkPositions, width, height)
    }
}

private data class CandyTrailLayoutChild(
    val node: CandyTrailNode?,
    val fork: CandyTrailFork?,
    val sortAt: Long,
    val sortId: String,
)

internal object CandyTrailViewportRules {
    const val MIN_SCALE = 0.15f
    const val MAX_SCALE = 2.25f

    fun scale(value: Float): Float = value.coerceIn(MIN_SCALE, MAX_SCALE)

    fun pan(
        value: Float,
        viewportSize: Float,
        graphSize: Float,
        scale: Float,
        minimumVisible: Float,
    ): Float {
        val scaledGraphSize = graphSize * scale
        val minimum = minimumVisible - scaledGraphSize
        val maximum = viewportSize - minimumVisible
        return value.coerceIn(minimum.coerceAtMost(maximum), maximum.coerceAtLeast(minimum))
    }

    fun centeredPan(
        contentCenter: Float,
        viewportSize: Float,
        graphSize: Float,
        scale: Float,
        minimumVisible: Float,
    ): Float = pan(
        value = viewportSize / 2f - contentCenter * scale,
        viewportSize = viewportSize,
        graphSize = graphSize,
        scale = scale,
        minimumVisible = minimumVisible,
    )

    fun zoomedPan(
        value: Float,
        focalPoint: Float,
        oldScale: Float,
        newScale: Float,
        viewportSize: Float,
        graphSize: Float,
        minimumVisible: Float,
    ): Float {
        val scaleChange = newScale / oldScale
        return pan(
            value = focalPoint - (focalPoint - value) * scaleChange,
            viewportSize = viewportSize,
            graphSize = graphSize,
            scale = newScale,
            minimumVisible = minimumVisible,
        )
    }
}

internal object CandyTrailMotionRules {
    fun staggeredProgress(progress: Float, index: Int, count: Int): Float {
        if (count <= 1) return progress.coerceIn(0f, 1f)
        val totalDelay = 0.42f
        val delay = index.coerceIn(0, count - 1) * totalDelay / (count - 1)
        return ((progress - delay) / (1f - totalDelay)).coerceIn(0f, 1f)
    }
}

internal object CandyTrailLayerRules {
    fun isVisible(tabOverviewVisible: Boolean, candyTrailTabId: String?): Boolean =
        tabOverviewVisible || candyTrailTabId != null
}
