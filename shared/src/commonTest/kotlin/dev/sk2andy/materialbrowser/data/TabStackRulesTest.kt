package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.TabStack
import dev.sk2andy.materialbrowser.browser.TabStackColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabStackRulesTest {
    private val tabs = listOf(
        BrowserTab(id = "one", lastAccessedAt = 1L),
        BrowserTab(id = "two", lastAccessedAt = 2L),
        BrowserTab(id = "work", lastAccessedAt = 3L, profileId = "work"),
        BrowserTab(id = "private", lastAccessedAt = 4L, isIncognito = true),
        BrowserTab(id = "three", lastAccessedAt = 5L),
        BrowserTab(id = "four", lastAccessedAt = 6L),
    )

    @Test
    fun `create normalizes name and moves tab out of previous stack`() {
        val existing = stack(id = "old", tabIds = listOf("one", "two"))

        val result = TabStackRules.create(
            stacks = listOf(existing),
            tabs = tabs,
            tabIds = listOf("one", "two"),
            stackId = "new",
            name = "  Shopping   ideas  ",
            color = TabStackColor.Cherry,
        )!!

        assertEquals(1, result.size)
        assertEquals("Shopping ideas", result.single().name)
    }

    @Test
    fun `add rejects profile and privacy boundary crossings`() {
        val regularStack = stack(tabIds = listOf("one", "two"))

        assertNull(TabStackRules.addTab(listOf(regularStack), tabs, "work", regularStack.id))
        assertNull(TabStackRules.addTab(listOf(regularStack), tabs, "private", regularStack.id))
    }

    @Test
    fun `sanitizing drops missing duplicate and mixed privacy members`() {
        val result = TabStackRules.sanitized(
            stacks = listOf(
                stack(id = "first", tabIds = listOf("one", "two", "private", "missing")),
                stack(id = "second", tabIds = listOf("one", "two")),
            ),
            tabs = tabs,
        )

        assertEquals(listOf("one", "two"), result.single().tabIds)
    }

    @Test
    fun `collapsed stack keeps trigger identity while cover remains configured`() {
        val stack = stack(
            tabIds = listOf("one", "two"),
            previewTabId = "one",
            collapsedAnchorTabId = "two",
            isCollapsed = true,
        )

        val visible = TabStackRules.visibleTabs(tabs.take(2), listOf(stack))

        assertEquals(listOf("two"), visible.map(BrowserTab::id))
        assertEquals("one", stack.previewTabId)
        assertEquals(
            "two",
            TabStackRules.visibleTabId("one", tabs.take(2), listOf(stack)),
        )
    }

    @Test
    fun `collapse stores trigger as anchor and expand retains it`() {
        val stack = stack(tabIds = listOf("one", "two"))

        val collapsed = TabStackRules.toggleCollapsed(
            stacks = listOf(stack),
            stackId = stack.id,
            triggerTabId = "two",
        )!!.single()
        val expanded = TabStackRules.toggleCollapsed(
            stacks = listOf(collapsed),
            stackId = stack.id,
            triggerTabId = "one",
        )!!.single()

        assertEquals(true, collapsed.isCollapsed)
        assertEquals("two", collapsed.collapsedAnchorTabId)
        assertEquals(false, expanded.isCollapsed)
        assertEquals("two", expanded.collapsedAnchorTabId)
        assertNull(
            TabStackRules.toggleCollapsed(
                stacks = listOf(stack),
                stackId = stack.id,
                triggerTabId = "three",
            ),
        )
    }

    @Test
    fun `expanded stack keeps flat tab order`() {
        val stack = stack(tabIds = listOf("two", "one"))

        val visible = TabStackRules.visibleTabs(tabs.take(3), listOf(stack))

        assertEquals(listOf("one", "two", "work"), visible.map(BrowserTab::id))
    }

    @Test
    fun `persistent stacks reject private membership without leaking metadata`() {
        val mixed = stack(tabIds = listOf("one", "private"))

        assertEquals(emptyList<TabStack>(), TabStackRules.persistent(listOf(mixed), tabs))
    }

    @Test
    fun `update keeps identity and collapse while deselected members become unstacked`() {
        val original = stack(
            id = "research",
            tabIds = listOf("one", "two", "three", "four"),
            isCollapsed = true,
        )

        val result = TabStackRules.update(
            stacks = listOf(original),
            tabs = tabs,
            stackId = original.id,
            tabIds = listOf("one", "two"),
            name = "Edited",
            color = TabStackColor.Blueberry,
        )!!

        assertEquals("research", result.single().id)
        assertEquals(listOf("one", "two"), result.single().tabIds)
        assertEquals("Edited", result.single().name)
        assertEquals(TabStackColor.Blueberry, result.single().color)
        assertEquals(true, result.single().isCollapsed)
    }

    @Test
    fun `create rejects a new stack at the stack limit`() {
        val existing = List(TabStackRules.MAX_STACKS) { index ->
            stack(
                id = "stack-$index",
                tabIds = listOf("stack-$index-one", "stack-$index-two"),
            )
        }
        val allTabs = tabs + existing.flatMap { stack ->
            stack.tabIds.map { tabId -> BrowserTab(id = tabId, lastAccessedAt = 1L) }
        }

        val result = TabStackRules.create(
            stacks = existing,
            tabs = allTabs,
            tabIds = listOf("one", "two"),
            stackId = "one-too-many",
            name = "Overflow",
            color = TabStackColor.Grape,
        )

        assertNull(result)
    }

    @Test
    fun `create rejects preview outside selected members`() {
        assertNull(
            TabStackRules.create(
                stacks = emptyList(),
                tabs = tabs,
                tabIds = listOf("one", "two"),
                stackId = "research",
                name = "Research",
                color = TabStackColor.Grape,
                previewTabId = "three",
            ),
        )
    }

    @Test
    fun `sanitize and removal keep a valid deterministic preview`() {
        val sanitized = TabStackRules.sanitized(
            stacks = listOf(
                stack(
                    tabIds = listOf("one", "two", "three"),
                    previewTabId = "missing",
                    collapsedAnchorTabId = "missing",
                ),
            ),
            tabs = tabs,
        ).single()

        assertEquals("one", sanitized.previewTabId)
        assertEquals("one", sanitized.collapsedAnchorTabId)
        assertEquals(
            "two",
            TabStackRules.removeTab(listOf(sanitized), "one").single().previewTabId,
        )
        assertEquals(
            "two",
            TabStackRules.removeTab(listOf(sanitized), "one")
                .single()
                .collapsedAnchorTabId,
        )
    }

    @Test
    fun `preview mutation accepts members and rejects outsiders`() {
        val stack = stack(tabIds = listOf("one", "two"), previewTabId = "one")

        assertEquals(
            "two",
            TabStackRules.setPreviewTab(listOf(stack), stack.id, "two")
                ?.single()
                ?.previewTabId,
        )
        assertNull(TabStackRules.setPreviewTab(listOf(stack), stack.id, "three"))
    }

    @Test
    fun `snooze undo preserves newer metadata and only restores membership`() {
        val original = stack(
            tabIds = listOf("one", "two", "three"),
            previewTabId = "two",
        )
        val afterSnooze = original.copy(
            tabIds = listOf("one", "three"),
            previewTabId = "one",
        )
        val edited = afterSnooze.copy(
            name = "Edited",
            color = TabStackColor.Cherry,
            previewTabId = "three",
            isCollapsed = true,
        )

        val result = TabStackRules.restoreSnoozedMember(
            stacks = listOf(edited),
            tabs = tabs,
            restoredTabId = "two",
            originalStack = original,
            stackAfterSnooze = afterSnooze,
        ).single()

        assertEquals(listOf("one", "two", "three"), result.tabIds)
        assertEquals("Edited", result.name)
        assertEquals(TabStackColor.Cherry, result.color)
        assertEquals("three", result.previewTabId)
        assertEquals(true, result.isCollapsed)
    }

    private fun stack(
        id: String = "stack",
        profileId: String = "candy",
        tabIds: List<String>,
        previewTabId: String? = null,
        collapsedAnchorTabId: String? = null,
        isCollapsed: Boolean = false,
    ) = TabStack(
        id = id,
        profileId = profileId,
        name = "Ideas",
        color = TabStackColor.Grape,
        tabIds = tabIds,
        previewTabId = previewTabId,
        collapsedAnchorTabId = collapsedAnchorTabId,
        isCollapsed = isCollapsed,
    )
}
