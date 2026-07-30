package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyTrailForkRulesTest {
    @Test
    fun `tab limit rejects fork before mutation`() {
        assertTrue(CandyTrailForkRules.canCreateFork(openTabCount = 11, maxTabs = 12))
        assertTrue(!CandyTrailForkRules.canCreateFork(openTabCount = 12, maxTabs = 12))
    }

    @Test
    fun `migration accepts legacy graph and closes incomplete open fork`() {
        assertTrue(CandyTrailForkMigrationRules.supportsVersion(1))
        assertTrue(CandyTrailForkMigrationRules.supportsVersion(2))
        assertEquals(
            CandyTrailForkLifecycle.Closed,
            CandyTrailForkMigrationRules.lifecycleFromWire("open", null),
        )
        assertEquals(
            CandyTrailForkLifecycle.Open,
            CandyTrailForkMigrationRules.lifecycleFromWire("open", DESTINATION_TAB_ID),
        )
        assertEquals(
            "closed",
            CandyTrailForkMigrationRules.lifecycleWireValue(CandyTrailForkLifecycle.Closed),
        )
    }

    @Test
    fun `fork binds origin node to same profile and privacy destination`() {
        val forked = CandyTrailForkRules.create(
            trail = trail(),
            originTab = tab(ORIGIN_TAB_ID, PROFILE_ID),
            originNodeId = "n0",
            destinationTab = tab(DESTINATION_TAB_ID, PROFILE_ID),
            createdAt = 10L,
        )

        assertNotNull(forked)
        val fork = forked!!.forks.single()
        assertEquals("f0", fork.id)
        assertEquals(ORIGIN_TAB_ID, fork.originTabId)
        assertEquals("n0", fork.originNodeId)
        assertEquals(DESTINATION_TAB_ID, fork.destinationTabId)
        assertEquals(CandyTrailForkLifecycle.Open, fork.lifecycle)
        assertEquals("https://origin.example", fork.url)
        assertEquals(1L, forked.nextForkOrdinal)
    }

    @Test
    fun `fork rejects missing node cross profile and privacy mismatch`() {
        val origin = tab(ORIGIN_TAB_ID, PROFILE_ID)

        assertNull(
            CandyTrailForkRules.create(
                trail(),
                origin,
                "missing",
                tab(DESTINATION_TAB_ID, PROFILE_ID),
                1L,
            ),
        )
        assertNull(
            CandyTrailForkRules.create(
                trail(),
                origin,
                "n0",
                tab(DESTINATION_TAB_ID, "other"),
                1L,
            ),
        )
        assertNull(
            CandyTrailForkRules.create(
                trail(),
                origin,
                "n0",
                tab(DESTINATION_TAB_ID, PROFILE_ID, isIncognito = true),
                1L,
            ),
        )
    }

    @Test
    fun `closing removes destination id and reopening binds a new tab`() {
        val forked = createFork()
        val closed = CandyTrailForkRules.closeDestination(forked, DESTINATION_TAB_ID, 20L)

        assertEquals(CandyTrailForkLifecycle.Closed, closed.forks.single().lifecycle)
        assertNull(closed.forks.single().destinationTabId)

        val reopened = CandyTrailForkRules.reopen(
            trail = closed,
            forkId = "f0",
            originTab = tab(ORIGIN_TAB_ID, PROFILE_ID),
            destinationTab = tab(REOPENED_TAB_ID, PROFILE_ID),
            reopenedAt = 30L,
        )

        assertEquals(CandyTrailForkLifecycle.Open, reopened!!.forks.single().lifecycle)
        assertEquals(REOPENED_TAB_ID, reopened.forks.single().destinationTabId)
        assertEquals(30L, reopened.forks.single().updatedAt)
    }

    @Test
    fun `restore reconciliation closes missing and foreign destinations`() {
        val forked = createFork()
        val missing = CandyTrailForkRules.reconcile(
            trail = forked,
            originTab = tab(ORIGIN_TAB_ID, PROFILE_ID),
            openTabs = emptyList(),
            reconciledAt = 40L,
        )
        assertEquals(CandyTrailForkLifecycle.Closed, missing.forks.single().lifecycle)

        val foreign = CandyTrailForkRules.reconcile(
            trail = forked,
            originTab = tab(ORIGIN_TAB_ID, PROFILE_ID),
            openTabs = listOf(tab(DESTINATION_TAB_ID, "foreign")),
            reconciledAt = 50L,
        )
        assertEquals(CandyTrailForkLifecycle.Closed, foreign.forks.single().lifecycle)
        assertNull(foreign.forks.single().destinationTabId)
    }

    @Test
    fun `profile deletion move preserves fork when both tabs move together`() {
        val forked = createFork()

        val moved = CandyTrailForkRules.reconcile(
            trail = forked,
            originTab = tab(ORIGIN_TAB_ID, "fallback"),
            openTabs = listOf(
                tab(ORIGIN_TAB_ID, "fallback"),
                tab(DESTINATION_TAB_ID, "fallback"),
            ),
            reconciledAt = 40L,
        )

        assertEquals(CandyTrailForkLifecycle.Open, moved.forks.single().lifecycle)
        assertEquals(DESTINATION_TAB_ID, moved.forks.single().destinationTabId)
        assertEquals("fallback", moved.forks.single().profileId)
    }

    @Test
    fun `retention keeps open forks before newest closed history`() {
        var current = trail()
        repeat(4) { index ->
            val destination = tab("00000000-0000-0000-0000-00000000001${index + 1}", PROFILE_ID)
            current = CandyTrailForkRules.create(
                current,
                tab(ORIGIN_TAB_ID, PROFILE_ID),
                "n0",
                destination,
                index.toLong(),
                maxForks = 10,
            )!!
            if (index != 1) {
                current = CandyTrailForkRules.closeDestination(
                    current,
                    destination.id,
                    10L + index,
                )
            }
        }

        val retained = CandyTrailForkRules.normalized(current, maxForks = 2)

        assertEquals(2, retained.forks.size)
        assertTrue(retained.forks.any { it.lifecycle == CandyTrailForkLifecycle.Open })
        assertTrue(retained.forks.any { it.id == "f3" })
    }

    @Test
    fun `normalization removes malformed relationships and repairs closed lifecycle`() {
        val normalized = CandyTrailForkRules.normalized(
            trail().copy(
                forks = listOf(
                    fork(id = "valid", destinationTabId = null),
                    fork(id = "missing-node", originNodeId = "missing"),
                    fork(id = "foreign-origin", originTabId = "foreign"),
                ),
            ),
        )

        assertEquals(listOf("valid"), normalized.forks.map(CandyTrailFork::id))
        assertEquals(CandyTrailForkLifecycle.Closed, normalized.forks.single().lifecycle)
        assertNull(normalized.forks.single().destinationTabId)
    }

    @Test
    fun `node retention protects origin of an open fork`() {
        var current = CandyTrailRules.recordNavigation(
            null,
            ORIGIN_TAB_ID,
            "https://root.example",
            "Root",
            1L,
        )
        current = CandyTrailRules.recordNavigation(
            current,
            ORIGIN_TAB_ID,
            "https://fork.example",
            "Fork",
            2L,
        )
        current = CandyTrailForkRules.create(
            current,
            tab(ORIGIN_TAB_ID, PROFILE_ID),
            "n1",
            tab(DESTINATION_TAB_ID, PROFILE_ID),
            3L,
        )!!
        current = CandyTrailRules.selectNode(current, "n0", 4L)!!
        current = CandyTrailRules.recordNavigation(
            current,
            ORIGIN_TAB_ID,
            "https://active.example",
            "Active",
            5L,
        )

        val retained = CandyTrailRules.retain(current, maxNodes = 3)

        assertTrue(retained.nodes.any { it.id == "n1" })
        assertEquals("n2", retained.currentNodeId)
        assertEquals("n1", retained.forks.single().originNodeId)
    }

    private fun createFork(): CandyTrail = CandyTrailForkRules.create(
        trail(),
        tab(ORIGIN_TAB_ID, PROFILE_ID),
        "n0",
        tab(DESTINATION_TAB_ID, PROFILE_ID),
        10L,
    )!!

    private fun trail() = CandyTrail(
        tabId = ORIGIN_TAB_ID,
        nodes = listOf(
            CandyTrailNode(
                id = "n0",
                parentId = null,
                url = "https://origin.example",
                title = "Origin",
                visitedAt = 1L,
            ),
        ),
        currentNodeId = "n0",
        nextOrdinal = 1L,
    )

    private fun fork(
        id: String,
        originTabId: String = ORIGIN_TAB_ID,
        originNodeId: String = "n0",
        destinationTabId: String? = DESTINATION_TAB_ID,
    ) = CandyTrailFork(
        id = id,
        originTabId = originTabId,
        originNodeId = originNodeId,
        destinationTabId = destinationTabId,
        profileId = PROFILE_ID,
        isIncognito = false,
        url = "https://origin.example",
        title = "Origin",
        createdAt = 1L,
        updatedAt = 1L,
        lifecycle = CandyTrailForkLifecycle.Open,
    )

    private fun tab(id: String, profileId: String, isIncognito: Boolean = false) =
        CandyTrailForkTab(id, profileId, isIncognito)

    private companion object {
        const val ORIGIN_TAB_ID = "00000000-0000-0000-0000-000000000001"
        const val DESTINATION_TAB_ID = "00000000-0000-0000-0000-000000000002"
        const val REOPENED_TAB_ID = "00000000-0000-0000-0000-000000000003"
        const val PROFILE_ID = "profile"
    }
}
