package dev.sk2andy.materialbrowser.browser

/** Shared engine-history reconciliation for Android WebView, Gecko and iOS WKWebView. */

data class CandyTrailHistoryEntry(
    val url: String,
    val nodeId: String?,
)

data class CandyTrailHistoryBinding(
    val entries: List<CandyTrailHistoryEntry> = emptyList(),
    val currentIndex: Int = -1,
)

data class CandyTrailHistorySnapshot(
    val urls: List<String>,
    val currentIndex: Int,
    val isReload: Boolean = false,
)

data class CandyTrailReconcileResult(
    val trail: CandyTrail,
    val binding: CandyTrailHistoryBinding,
)

object CandyTrailHistoryReconciler {
    fun refineCurrentTitle(
        trail: CandyTrail,
        url: String,
        title: String,
        visitedAt: Long,
    ): CandyTrail {
        if (title.isBlank()) return trail
        val current = trail.nodes.firstOrNull { node -> node.id == trail.currentNodeId }
            ?.takeIf { node -> node.url == url }
            ?: return trail
        return CandyTrailRules.updateCurrentPage(
            trail = trail,
            url = current.url,
            title = title,
            visitedAt = visitedAt,
        )
    }

    fun reconcile(
        trail: CandyTrail?,
        tabId: String,
        previous: CandyTrailHistoryBinding,
        snapshot: CandyTrailHistorySnapshot,
        title: String,
        visitedAt: Long,
        pendingTargetNodeId: String? = null,
    ): CandyTrailReconcileResult {
        if (snapshot.currentIndex !in snapshot.urls.indices) {
            return CandyTrailReconcileResult(
                trail = trail ?: CandyTrail(tabId),
                binding = CandyTrailHistoryBinding(),
            )
        }
        val entries = snapshot.urls.mapIndexed { index, url ->
            val oldEntry = previous.entries.getOrNull(index)
            CandyTrailHistoryEntry(
                url = url,
                nodeId = oldEntry?.nodeId?.takeIf { oldEntry.url == url },
            )
        }.toMutableList()
        val currentUrl = snapshot.urls[snapshot.currentIndex]
        val currentTrail = trail ?: CandyTrail(tabId)
        val restoredCurrentId = currentTrail.currentNodeId?.takeIf { nodeId ->
            previous.entries.isEmpty() &&
                currentTrail.nodes.firstOrNull { it.id == nodeId }?.url == currentUrl
        }
        val carriedCurrentTarget = when {
            previous.currentIndex < 0 -> null
            snapshot.currentIndex < previous.currentIndex -> entries[snapshot.currentIndex].nodeId
            snapshot.currentIndex == previous.currentIndex -> entries[snapshot.currentIndex].nodeId
            else -> null
        }
        val traversalTarget = pendingTargetNodeId
            ?.takeIf { target ->
                currentTrail.nodes.any { it.id == target && it.url == currentUrl }
            }
            ?: carriedCurrentTarget
            ?: restoredCurrentId
            ?: currentTrail.currentNodeId.takeIf {
                snapshot.isReload && currentTrail.nodes.firstOrNull { node -> node.id == it }?.url == currentUrl
            }
        val updatedTrail = CandyTrailRules.recordNavigation(
            current = currentTrail,
            tabId = tabId,
            url = currentUrl,
            title = title,
            visitedAt = visitedAt,
            traversalTargetId = traversalTarget,
            forceNewEntry =
                traversalTarget == null &&
                    snapshot.currentIndex > previous.currentIndex &&
                    !snapshot.isReload,
        )
        entries[snapshot.currentIndex] = entries[snapshot.currentIndex].copy(
            nodeId = updatedTrail.currentNodeId,
        )
        return CandyTrailReconcileResult(
            trail = updatedTrail,
            binding = CandyTrailHistoryBinding(entries, snapshot.currentIndex),
        )
    }

    fun indexOfNode(binding: CandyTrailHistoryBinding, nodeId: String): Int? =
        binding.entries.indexOfFirst { it.nodeId == nodeId }.takeIf { it >= 0 }

    fun remapNodeIds(
        binding: CandyTrailHistoryBinding,
        nodeIds: Map<String, String>,
    ): CandyTrailHistoryBinding = binding.copy(
        entries = binding.entries.map { entry ->
            entry.copy(nodeId = entry.nodeId?.let(nodeIds::get))
        },
    )

    fun retainNodeIds(
        binding: CandyTrailHistoryBinding,
        retainedNodeIds: Set<String>,
    ): CandyTrailHistoryBinding = binding.copy(
        entries = binding.entries.map { entry ->
            entry.copy(nodeId = entry.nodeId?.takeIf(retainedNodeIds::contains))
        },
    )
}
