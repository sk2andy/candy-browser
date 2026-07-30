package dev.sk2andy.materialbrowser.browser

enum class CandyTrailForkLifecycle {
    Open,
    Closed,
}

data class CandyTrailFork(
    val id: String,
    val originTabId: String,
    val originNodeId: String,
    val destinationTabId: String?,
    val profileId: String,
    val isIncognito: Boolean,
    val url: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lifecycle: CandyTrailForkLifecycle,
)

data class CandyTrailForkTab(
    val id: String,
    val profileId: String,
    val isIncognito: Boolean,
)

object CandyTrailForkMigrationRules {
    const val LEGACY_FORMAT_VERSION = 1
    const val CURRENT_FORMAT_VERSION = 2
    const val OPEN_WIRE_VALUE = "open"
    const val CLOSED_WIRE_VALUE = "closed"

    fun supportsVersion(version: Int): Boolean =
        version in LEGACY_FORMAT_VERSION..CURRENT_FORMAT_VERSION

    fun lifecycleFromWire(value: String?, destinationTabId: String?): CandyTrailForkLifecycle =
        if (value == OPEN_WIRE_VALUE && !destinationTabId.isNullOrBlank()) {
            CandyTrailForkLifecycle.Open
        } else {
            CandyTrailForkLifecycle.Closed
        }

    fun lifecycleWireValue(lifecycle: CandyTrailForkLifecycle): String =
        if (lifecycle == CandyTrailForkLifecycle.Open) OPEN_WIRE_VALUE else CLOSED_WIRE_VALUE
}

object CandyTrailForkRules {
    const val MAX_FORKS = 32

    fun canCreateFork(openTabCount: Int, maxTabs: Int): Boolean =
        openTabCount >= 0 && maxTabs > 0 && openTabCount < maxTabs

    fun create(
        trail: CandyTrail,
        originTab: CandyTrailForkTab,
        originNodeId: String,
        destinationTab: CandyTrailForkTab,
        createdAt: Long,
        maxForks: Int = MAX_FORKS,
    ): CandyTrail? {
        val origin = trail.nodes.firstOrNull { it.id == originNodeId } ?: return null
        if (
            trail.tabId != originTab.id ||
            originTab.id == destinationTab.id ||
            originTab.profileId.isBlank() ||
            originTab.profileId != destinationTab.profileId ||
            originTab.isIncognito != destinationTab.isIncognito
        ) {
            return null
        }
        val fork = CandyTrailFork(
            id = "f${trail.nextForkOrdinal}",
            originTabId = originTab.id,
            originNodeId = origin.id,
            destinationTabId = destinationTab.id,
            profileId = originTab.profileId,
            isIncognito = originTab.isIncognito,
            url = origin.url,
            title = origin.title,
            createdAt = createdAt.coerceAtLeast(0L),
            updatedAt = createdAt.coerceAtLeast(0L),
            lifecycle = CandyTrailForkLifecycle.Open,
        )
        return normalized(
            trail.copy(
                forks = trail.forks + fork,
                nextForkOrdinal = trail.nextForkOrdinal + 1L,
            ),
            maxForks,
        )
    }

    fun reopen(
        trail: CandyTrail,
        forkId: String,
        originTab: CandyTrailForkTab,
        destinationTab: CandyTrailForkTab,
        reopenedAt: Long,
    ): CandyTrail? {
        val fork = trail.forks.firstOrNull { it.id == forkId } ?: return null
        if (
            trail.tabId != originTab.id ||
            fork.originTabId != originTab.id ||
            fork.originNodeId !in trail.nodes.map(CandyTrailNode::id) ||
            destinationTab.id == originTab.id ||
            originTab.profileId != destinationTab.profileId ||
            originTab.isIncognito != destinationTab.isIncognito
        ) {
            return null
        }
        return normalized(
            trail.copy(
                forks = trail.forks.map { candidate ->
                    if (candidate.id == forkId) {
                        candidate.copy(
                            destinationTabId = destinationTab.id,
                            profileId = originTab.profileId,
                            isIncognito = originTab.isIncognito,
                            updatedAt = reopenedAt.coerceAtLeast(candidate.createdAt),
                            lifecycle = CandyTrailForkLifecycle.Open,
                        )
                    } else {
                        candidate
                    }
                },
            ),
        )
    }

    fun closeDestination(
        trail: CandyTrail,
        destinationTabId: String,
        closedAt: Long,
    ): CandyTrail = normalized(
        trail.copy(
            forks = trail.forks.map { fork ->
                if (
                    fork.lifecycle == CandyTrailForkLifecycle.Open &&
                    fork.destinationTabId == destinationTabId
                ) {
                    fork.copy(
                        destinationTabId = null,
                        updatedAt = closedAt.coerceAtLeast(fork.createdAt),
                        lifecycle = CandyTrailForkLifecycle.Closed,
                    )
                } else {
                    fork
                }
            },
        ),
    )

    fun reconcile(
        trail: CandyTrail,
        originTab: CandyTrailForkTab,
        openTabs: Collection<CandyTrailForkTab>,
        reconciledAt: Long,
    ): CandyTrail {
        if (trail.tabId != originTab.id) return trail.copy(forks = emptyList())
        val byId = openTabs.associateBy(CandyTrailForkTab::id)
        return normalized(
            trail.copy(
                forks = trail.forks.map { fork ->
                    val destination = fork.destinationTabId?.let(byId::get)
                    val destinationValid = destination != null &&
                        destination.id != originTab.id &&
                        destination.profileId == originTab.profileId &&
                        destination.isIncognito == originTab.isIncognito
                    if (fork.lifecycle == CandyTrailForkLifecycle.Open && !destinationValid) {
                        fork.copy(
                            destinationTabId = null,
                            profileId = originTab.profileId,
                            isIncognito = originTab.isIncognito,
                            updatedAt = reconciledAt.coerceAtLeast(fork.createdAt),
                            lifecycle = CandyTrailForkLifecycle.Closed,
                        )
                    } else {
                        fork.copy(
                            profileId = originTab.profileId,
                            isIncognito = originTab.isIncognito,
                            destinationTabId = if (destinationValid) fork.destinationTabId else null,
                        )
                    }
                },
            ),
        )
    }

    fun normalized(trail: CandyTrail, maxForks: Int = MAX_FORKS): CandyTrail {
        if (maxForks <= 0 || trail.nodes.isEmpty()) {
            return trail.copy(forks = emptyList(), nextForkOrdinal = trail.nextForkOrdinal.coerceAtLeast(0L))
        }
        val nodeIds = trail.nodes.mapTo(mutableSetOf(), CandyTrailNode::id)
        val unique = linkedMapOf<String, CandyTrailFork>()
        trail.forks.forEach { fork ->
            val safeUrl = fork.url.trim().take(CandyTrailRules.MAX_URL_LENGTH)
            val lifecycle = if (
                fork.lifecycle == CandyTrailForkLifecycle.Open &&
                !fork.destinationTabId.isNullOrBlank()
            ) {
                CandyTrailForkLifecycle.Open
            } else {
                CandyTrailForkLifecycle.Closed
            }
            if (
                fork.id.isNotBlank() &&
                fork.id !in unique &&
                fork.originTabId == trail.tabId &&
                fork.originNodeId in nodeIds &&
                fork.profileId.isNotBlank() &&
                CandyTrailRules.isJourneyUrl(safeUrl)
            ) {
                unique[fork.id] = fork.copy(
                    destinationTabId = fork.destinationTabId
                        ?.takeIf { it.isNotBlank() && it != trail.tabId && lifecycle == CandyTrailForkLifecycle.Open },
                    url = safeUrl,
                    title = fork.title.trim().take(CandyTrailRules.MAX_TITLE_LENGTH),
                    createdAt = fork.createdAt.coerceAtLeast(0L),
                    updatedAt = fork.updatedAt.coerceAtLeast(fork.createdAt.coerceAtLeast(0L)),
                    lifecycle = lifecycle,
                )
            }
        }
        val retained = unique.values.sortedWith(
            compareByDescending<CandyTrailFork> { it.lifecycle == CandyTrailForkLifecycle.Open }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.id },
        ).take(maxForks).toSet()
        val ordered = trail.forks.mapNotNull { fork -> unique[fork.id] }.filter(retained::contains)
        val highestOrdinal = ordered.mapNotNull { fork ->
            fork.id.removePrefix("f").toLongOrNull()
        }.maxOrNull()?.plus(1L) ?: 0L
        return trail.copy(
            forks = ordered,
            nextForkOrdinal = maxOf(trail.nextForkOrdinal, highestOrdinal, 0L),
        )
    }
}
