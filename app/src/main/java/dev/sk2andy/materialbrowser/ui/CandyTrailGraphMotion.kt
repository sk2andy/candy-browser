package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import kotlin.math.max
import kotlin.math.min

internal sealed interface CandyTrailGraphTarget {
    val id: String

    data class Node(override val id: String) : CandyTrailGraphTarget

    data class Fork(override val id: String) : CandyTrailGraphTarget
}

internal data class CandyTrailGraphSnapshot(
    val nodeIds: Set<String>,
    val forkIds: Set<String>,
)

internal data class CandyTrailEdgeGeometry(
    val startX: Float,
    val startY: Float,
    val firstControlX: Float,
    val firstControlY: Float,
    val secondControlX: Float,
    val secondControlY: Float,
    val endX: Float,
    val endY: Float,
)

internal data class CandyTrailPathSegment(
    val startFraction: Float,
    val endFraction: Float,
)

internal data class CandyTrailDirectedEdge(
    val target: CandyTrailGraphTarget,
    val reversed: Boolean,
)

internal object CandyTrailGraphMotionRules {
    const val EDGE_DRAW_DURATION_MILLIS = 260
    const val PATH_PULSE_DURATION_MILLIS = 650
    const val TARGET_SPRING_DAMPING_RATIO = 0.72f
    const val TARGET_SPRING_STIFFNESS = 520f

    private const val CONTROL_DISTANCE_FRACTION = 0.52f
    private const val PULSE_LENGTH_FRACTION = 0.28f
    private const val TARGET_START_SCALE = 0.72f
    private const val ARROW_REVEAL_START = 0.82f

    fun snapshot(trail: CandyTrail): CandyTrailGraphSnapshot = CandyTrailGraphSnapshot(
        nodeIds = trail.nodes.mapTo(linkedSetOf(), CandyTrailNode::id),
        forkIds = trail.forks.mapTo(linkedSetOf()) { it.id },
    )

    fun additions(
        previous: CandyTrailGraphSnapshot,
        next: CandyTrailGraphSnapshot,
    ): Set<CandyTrailGraphTarget> = buildSet {
        next.nodeIds.filterNot { it in previous.nodeIds }
            .forEach { add(CandyTrailGraphTarget.Node(it)) }
        next.forkIds.filterNot { it in previous.forkIds }
            .forEach { add(CandyTrailGraphTarget.Fork(it)) }
    }

    fun edgeGeometry(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ): CandyTrailEdgeGeometry {
        val controlDistance = ((endX - startX) * CONTROL_DISTANCE_FRACTION).coerceAtLeast(0f)
        return CandyTrailEdgeGeometry(
            startX = startX,
            startY = startY,
            firstControlX = startX + controlDistance,
            firstControlY = startY,
            secondControlX = endX - controlDistance,
            secondControlY = endY,
            endX = endX,
            endY = endY,
        )
    }

    fun pathTargets(trail: CandyTrail, target: CandyTrailGraphTarget): List<CandyTrailGraphTarget> {
        val byId = trail.nodes.associateBy(CandyTrailNode::id)
        val leafNodeId = when (target) {
            is CandyTrailGraphTarget.Node -> target.id
            is CandyTrailGraphTarget.Fork -> trail.forks
                .firstOrNull { it.id == target.id }
                ?.originNodeId
                ?: return emptyList()
        }
        if (leafNodeId !in byId) return emptyList()

        val reversedNodeEdges = mutableListOf<CandyTrailGraphTarget.Node>()
        val visited = mutableSetOf<String>()
        var cursor = byId[leafNodeId]
        while (cursor != null && visited.add(cursor.id)) {
            val parentId = cursor.parentId ?: break
            if (parentId !in byId) break
            reversedNodeEdges += CandyTrailGraphTarget.Node(cursor.id)
            cursor = byId[parentId]
        }
        return buildList {
            addAll(reversedNodeEdges.asReversed())
            if (target is CandyTrailGraphTarget.Fork) add(target)
        }
    }

    fun selectionPath(
        trail: CandyTrail,
        fromNodeId: String?,
        target: CandyTrailGraphTarget,
    ): List<CandyTrailDirectedEdge> {
        val nodesById = trail.nodes.associateBy(CandyTrailNode::id)
        val startId = fromNodeId?.takeIf(nodesById::containsKey) ?: return emptyList()
        val targetNodeId = when (target) {
            is CandyTrailGraphTarget.Node -> target.id
            is CandyTrailGraphTarget.Fork -> trail.forks
                .firstOrNull { it.id == target.id }
                ?.originNodeId
                ?: return emptyList()
        }
        if (targetNodeId !in nodesById) return emptyList()

        fun ancestorIds(nodeId: String): List<String> {
            val result = mutableListOf<String>()
            val visited = mutableSetOf<String>()
            var cursor = nodesById[nodeId]
            while (cursor != null && visited.add(cursor.id)) {
                result += cursor.id
                cursor = cursor.parentId?.let(nodesById::get)
            }
            return result
        }

        val startAncestors = ancestorIds(startId)
        val targetAncestors = ancestorIds(targetNodeId)
        val targetAncestorSet = targetAncestors.toSet()
        val commonAncestorId = startAncestors.firstOrNull(targetAncestorSet::contains)
            ?: return emptyList()

        return buildList {
            var cursorId = startId
            while (cursorId != commonAncestorId) {
                add(
                    CandyTrailDirectedEdge(
                        target = CandyTrailGraphTarget.Node(cursorId),
                        reversed = true,
                    ),
                )
                cursorId = nodesById[cursorId]?.parentId ?: return@buildList
            }
            targetAncestors
                .takeWhile { it != commonAncestorId }
                .asReversed()
                .forEach { nodeId ->
                    add(
                        CandyTrailDirectedEdge(
                            target = CandyTrailGraphTarget.Node(nodeId),
                            reversed = false,
                        ),
                    )
                }
            if (target is CandyTrailGraphTarget.Fork) {
                add(CandyTrailDirectedEdge(target = target, reversed = false))
            }
        }
    }

    fun directedSegment(
        segment: CandyTrailPathSegment,
        reversed: Boolean,
    ): CandyTrailPathSegment = if (reversed) {
        CandyTrailPathSegment(
            startFraction = 1f - segment.endFraction,
            endFraction = 1f - segment.startFraction,
        )
    } else {
        segment
    }

    fun revealProgress(progress: Float): Float = progress.coerceIn(0f, 1f)

    fun arrowAlpha(progress: Float): Float =
        ((revealProgress(progress) - ARROW_REVEAL_START) / (1f - ARROW_REVEAL_START))
            .coerceIn(0f, 1f)

    fun targetScale(progress: Float): Float =
        TARGET_START_SCALE + (1f - TARGET_START_SCALE) * revealProgress(progress)

    fun pulseAlpha(progress: Float): Float {
        val bounded = revealProgress(progress)
        return (4f * bounded * (1f - bounded)).coerceIn(0f, 1f)
    }

    fun pulseSegment(
        progress: Float,
        edgeStartDistance: Float,
        edgeLength: Float,
        totalLength: Float,
    ): CandyTrailPathSegment? {
        if (edgeLength <= 0f || totalLength <= 0f) return null
        val boundedProgress = revealProgress(progress)
        val pulseHead = totalLength * boundedProgress
        val pulseTail = (pulseHead - totalLength * PULSE_LENGTH_FRACTION).coerceAtLeast(0f)
        val safeEdgeStart = edgeStartDistance.coerceAtLeast(0f)
        val edgeEnd = safeEdgeStart + edgeLength
        val overlapStart = max(pulseTail, safeEdgeStart)
        val overlapEnd = min(pulseHead, edgeEnd)
        if (overlapEnd <= overlapStart) return null
        return CandyTrailPathSegment(
            startFraction = ((overlapStart - safeEdgeStart) / edgeLength).coerceIn(0f, 1f),
            endFraction = ((overlapEnd - safeEdgeStart) / edgeLength).coerceIn(0f, 1f),
        )
    }
}
