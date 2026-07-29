package dev.sk2andy.materialbrowser.browser

data class CandyTrailNode(
    val id: String,
    val parentId: String?,
    val url: String,
    val title: String,
    val visitedAt: Long,
)

data class CandyTrail(
    val tabId: String,
    val nodes: List<CandyTrailNode> = emptyList(),
    val currentNodeId: String? = null,
    val nextOrdinal: Long = 0L,
    val forks: List<CandyTrailFork> = emptyList(),
    val nextForkOrdinal: Long = 0L,
)

data class CandyTrailMergeResult(
    val trail: CandyTrail,
    val runtimeNodeIds: Map<String, String>,
)

object CandyTrailRules {
    const val MAX_NODES = 64
    const val MAX_URL_LENGTH = 2_048
    const val MAX_TITLE_LENGTH = 160

    fun recordNavigation(
        current: CandyTrail?,
        tabId: String,
        url: String,
        title: String,
        visitedAt: Long,
        traversalTargetId: String? = null,
        forceNewEntry: Boolean = false,
        maxNodes: Int = MAX_NODES,
    ): CandyTrail {
        val safeUrl = url.trim().take(MAX_URL_LENGTH)
        if (!isJourneyUrl(safeUrl)) return current ?: CandyTrail(tabId = tabId)
        val trail = current?.takeIf { it.tabId == tabId } ?: CandyTrail(tabId = tabId)
        val safeTitle = title.trim().take(MAX_TITLE_LENGTH)
        val target = traversalTargetId?.let { targetId ->
            trail.nodes.firstOrNull { it.id == targetId }
        }
        if (target != null) {
            return retain(
                trail.copy(
                    nodes = trail.nodes.map { node ->
                        if (node.id == target.id) {
                            node.copy(
                                url = safeUrl,
                                title = safeTitle.ifBlank { node.title },
                                visitedAt = visitedAt,
                            )
                        } else {
                            node
                        }
                    },
                    currentNodeId = target.id,
                ),
                maxNodes,
            )
        }

        val active = trail.nodes.firstOrNull { it.id == trail.currentNodeId }
        if (!forceNewEntry && active?.url == safeUrl) {
            return trail.copy(
                nodes = trail.nodes.map { node ->
                    if (node.id == active.id) {
                        node.copy(
                            title = safeTitle.ifBlank { node.title },
                            visitedAt = visitedAt,
                        )
                    } else {
                        node
                    }
                },
            )
        }

        val nodeId = "n${trail.nextOrdinal}"
        return retain(
            trail.copy(
                nodes = trail.nodes + CandyTrailNode(
                    id = nodeId,
                    parentId = active?.id,
                    url = safeUrl,
                    title = safeTitle,
                    visitedAt = visitedAt,
                ),
                currentNodeId = nodeId,
                nextOrdinal = trail.nextOrdinal + 1L,
            ),
            maxNodes,
        )
    }

    fun selectNode(trail: CandyTrail, nodeId: String, visitedAt: Long): CandyTrail? {
        if (trail.nodes.none { it.id == nodeId }) return null
        return trail.copy(
            nodes = trail.nodes.map { node ->
                if (node.id == nodeId) node.copy(visitedAt = visitedAt) else node
            },
            currentNodeId = nodeId,
        )
    }

    fun mergeRestoredWithRuntime(
        restored: CandyTrail,
        runtime: CandyTrail,
        maxNodes: Int = MAX_NODES,
    ): CandyTrailMergeResult {
        if (restored.tabId != runtime.tabId) {
            return CandyTrailMergeResult(restored, emptyMap())
        }
        var merged = normalized(restored)
        val runtimeTrail = normalized(runtime)
        if (runtimeTrail.nodes.isEmpty()) return CandyTrailMergeResult(merged, emptyMap())
        val mappedIds = mutableMapOf<String, String>()
        val remaining = runtimeTrail.nodes.toMutableList()
        val restoredCurrent = merged.nodes.firstOrNull { it.id == merged.currentNodeId }

        while (remaining.isNotEmpty()) {
            val readyIndex = remaining.indexOfFirst { node ->
                node.parentId == null || node.parentId in mappedIds ||
                    runtimeTrail.nodes.none { it.id == node.parentId }
            }.takeIf { it >= 0 } ?: 0
            val node = remaining.removeAt(readyIndex)
            val canReuseCurrent =
                node.parentId == null && mappedIds.isEmpty() && restoredCurrent?.url == node.url
            if (canReuseCurrent) {
                val target = checkNotNull(restoredCurrent)
                mappedIds[node.id] = target.id
                merged = merged.copy(
                    nodes = merged.nodes.map { existing ->
                        if (existing.id == target.id) {
                            existing.copy(
                                title = node.title.ifBlank { existing.title },
                                visitedAt = maxOf(existing.visitedAt, node.visitedAt),
                            )
                        } else {
                            existing
                        }
                    },
                )
                continue
            }
            val newId = "n${merged.nextOrdinal}"
            val parentId = node.parentId?.let(mappedIds::get) ?: merged.currentNodeId
            mappedIds[node.id] = newId
            merged = merged.copy(
                nodes = merged.nodes + node.copy(id = newId, parentId = parentId),
                nextOrdinal = merged.nextOrdinal + 1L,
            )
        }
        val runtimeForks = runtimeTrail.forks.mapNotNull { fork ->
            val mappedOriginNodeId = mappedIds[fork.originNodeId] ?: return@mapNotNull null
            fork.copy(originNodeId = mappedOriginNodeId)
        }
        runtimeForks.forEach { runtimeFork ->
            val duplicate = merged.forks.any { restoredFork ->
                restoredFork.originNodeId == runtimeFork.originNodeId &&
                    restoredFork.destinationTabId == runtimeFork.destinationTabId &&
                    restoredFork.url == runtimeFork.url &&
                    restoredFork.lifecycle == runtimeFork.lifecycle
            }
            if (!duplicate) {
                val newId = "f${merged.nextForkOrdinal}"
                merged = merged.copy(
                    forks = merged.forks + runtimeFork.copy(id = newId),
                    nextForkOrdinal = merged.nextForkOrdinal + 1L,
                )
            }
        }
        val retained = retain(
            merged.copy(
                currentNodeId = runtimeTrail.currentNodeId?.let(mappedIds::get) ?: merged.currentNodeId,
            ),
            maxNodes,
        )
        return CandyTrailMergeResult(
            trail = retained,
            runtimeNodeIds = mappedIds.filterValues { mergedId ->
                retained.nodes.any { it.id == mergedId }
            },
        )
    }

    fun updateCurrentPage(
        trail: CandyTrail,
        url: String,
        title: String,
        visitedAt: Long,
    ): CandyTrail {
        val currentId = trail.currentNodeId ?: return trail
        val safeUrl = url.trim().take(MAX_URL_LENGTH)
        if (!isJourneyUrl(safeUrl)) return trail
        val safeTitle = title.trim().take(MAX_TITLE_LENGTH)
        return trail.copy(
            nodes = trail.nodes.map { node ->
                if (node.id == currentId) {
                    node.copy(
                        url = safeUrl,
                        title = safeTitle.ifBlank { node.title },
                        visitedAt = visitedAt,
                    )
                } else {
                    node
                }
            },
        )
    }

    fun parentNodeId(trail: CandyTrail): String? = trail.nodes
        .firstOrNull { it.id == trail.currentNodeId }
        ?.parentId

    fun forwardNodeId(trail: CandyTrail, targetUrl: String?): String? {
        val currentId = trail.currentNodeId ?: return null
        return trail.nodes.asSequence()
            .filter { it.parentId == currentId }
            .filter { targetUrl == null || it.url == targetUrl }
            .sortedWith(compareByDescending<CandyTrailNode> { it.visitedAt }.thenByDescending { it.id })
            .firstOrNull()
            ?.id
    }

    fun retain(trail: CandyTrail, maxNodes: Int = MAX_NODES): CandyTrail {
        if (maxNodes <= 0) {
            return CandyTrail(
                tabId = trail.tabId,
                nextOrdinal = trail.nextOrdinal,
                nextForkOrdinal = trail.nextForkOrdinal,
            )
        }
        val forkNormalized = CandyTrailForkRules.normalized(trail)
        if (forkNormalized.nodes.size <= maxNodes) return forkNormalized
        val retained = forkNormalized.nodes.associateByTo(linkedMapOf(), CandyTrailNode::id)

        while (retained.size > maxNodes) {
            val protectedIds = ancestorIds(retained, forkNormalized.currentNodeId)
            forkNormalized.forks.asSequence()
                .filter { it.lifecycle == CandyTrailForkLifecycle.Open }
                .forEach { fork -> protectedIds += ancestorIds(retained, fork.originNodeId) }
            val parentIds = retained.values.mapNotNullTo(mutableSetOf(), CandyTrailNode::parentId)
            val removableLeaf = retained.values.asSequence()
                .filter { it.id !in protectedIds && it.id !in parentIds }
                .minWithOrNull(compareBy<CandyTrailNode> { it.visitedAt }.thenBy { it.id })
            if (removableLeaf != null) {
                retained.remove(removableLeaf.id)
                continue
            }

            val oldestProtectedRoot = protectedIds.asSequence()
                .mapNotNull(retained::get)
                .firstOrNull { it.parentId == null || it.parentId !in retained }
                ?: break
            retained.remove(oldestProtectedRoot.id)
            retained.replaceAll { _, node ->
                if (node.parentId == oldestProtectedRoot.id) node.copy(parentId = null) else node
            }
        }

        val currentNodeId = forkNormalized.currentNodeId?.takeIf(retained::containsKey)
        return CandyTrailForkRules.normalized(
            forkNormalized.copy(nodes = retained.values.toList(), currentNodeId = currentNodeId),
        )
    }

    fun normalized(trail: CandyTrail): CandyTrail {
        val unique = linkedMapOf<String, CandyTrailNode>()
        trail.nodes.forEach { node ->
            val safeUrl = node.url.trim().take(MAX_URL_LENGTH)
            if (node.id.isNotBlank() && node.id !in unique && isJourneyUrl(safeUrl)) {
                unique[node.id] = node.copy(
                    parentId = node.parentId?.takeIf { it != node.id },
                    url = safeUrl,
                    title = node.title.trim().take(MAX_TITLE_LENGTH),
                )
            }
        }
        val ids = unique.keys
        val parentRepaired = unique.values.map { node ->
            if (node.parentId != null && node.parentId !in ids) node.copy(parentId = null) else node
        }
        val repairedById = parentRepaired.associateBy(CandyTrailNode::id)
        val repaired = parentRepaired.map { node ->
            val visited = mutableSetOf(node.id)
            var cursor = node.parentId
            var cyclic = false
            while (cursor != null) {
                if (!visited.add(cursor)) {
                    cyclic = true
                    break
                }
                cursor = repairedById[cursor]?.parentId
            }
            if (cyclic) node.copy(parentId = null) else node
        }
        val currentId = trail.currentNodeId?.takeIf(ids::contains) ?: repaired.lastOrNull()?.id
        val highestOrdinal = repaired.mapNotNull { node ->
            node.id.removePrefix("n").toLongOrNull()
        }.maxOrNull()?.plus(1L) ?: 0L
        return retain(
            trail.copy(
                nodes = repaired,
                currentNodeId = currentId,
                nextOrdinal = maxOf(trail.nextOrdinal, highestOrdinal),
                nextForkOrdinal = trail.nextForkOrdinal.coerceAtLeast(0L),
            ),
        )
    }

    fun isJourneyUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("http://", ignoreCase = true)

    private fun ancestorIds(
        nodes: Map<String, CandyTrailNode>,
        nodeId: String?,
    ): LinkedHashSet<String> {
        val ancestors = linkedSetOf<String>()
        var cursor = nodeId
        while (cursor != null && ancestors.add(cursor)) cursor = nodes[cursor]?.parentId
        return ancestors
    }
}
