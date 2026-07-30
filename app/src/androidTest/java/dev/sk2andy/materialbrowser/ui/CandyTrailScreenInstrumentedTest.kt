package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailFork
import dev.sk2andy.materialbrowser.browser.CandyTrailForkLifecycle
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CandyTrailScreenInstrumentedTest {
    @Test
    fun graphActionsDispatchNodeForkAndOpenClosedEndpoints() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val tab = BrowserTab(
            id = "tab-actions",
            lastAccessedAt = 1L,
            title = "Action tab",
            url = "https://origin.example",
        )
        val openFork = CandyTrailFork(
            id = "f0",
            originTabId = tab.id,
            originNodeId = "n0",
            destinationTabId = "destination",
            profileId = tab.profileId,
            isIncognito = false,
            url = "https://fork.example",
            title = "Fork destination",
            createdAt = 2L,
            updatedAt = 2L,
            lifecycle = CandyTrailForkLifecycle.Open,
        )
        val baseTrail = CandyTrail(
            tabId = tab.id,
            nodes = listOf(
                node("n0", null, "Origin page", "https://origin.example", 1L),
            ),
            currentNodeId = "n0",
            nextOrdinal = 1L,
            forks = listOf(openFork),
            nextForkOrdinal = 1L,
        )
        val forkedNodeId = AtomicReference<String?>()
        val selectedForkCount = AtomicInteger()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity

        fun render(trail: CandyTrail) {
            instrumentation.runOnMainSync {
                activity.setContent {
                    MaterialBrowserTheme {
                        CandyTrailScreen(
                            tab = tab,
                            trail = trail,
                            favicon = null,
                            forkFavicons = emptyMap(),
                            sourceBounds = null,
                            predictiveBackProgress = 0f,
                            predictiveBackEdgeSign = 1,
                            onOpenTabActions = {},
                            onSelectNode = {},
                            onForkNode = { nodeId ->
                                forkedNodeId.set(nodeId)
                                true
                            },
                            onSelectFork = {
                                selectedForkCount.incrementAndGet()
                                true
                            },
                            onDismiss = {},
                        )
                    }
                }
            }
            instrumentation.waitForIdleSync()
        }

        try {
            render(baseTrail)
            assertTrue(
                clickNode(activity.getString(R.string.cd_candy_trail_node_actions, "Origin page")),
            )
            assertTrue(clickNode(activity.getString(R.string.action_fork_from_here)))
            assertTrue(awaitValue { forkedNodeId.get() == "n0" })

            render(baseTrail)
            assertTrue(clickNode("Fork destination"))
            assertTrue(awaitValue { selectedForkCount.get() == 1 })

            render(
                baseTrail.copy(
                    forks = listOf(
                        openFork.copy(
                            destinationTabId = null,
                            lifecycle = CandyTrailForkLifecycle.Closed,
                        ),
                    ),
                ),
            )
            assertTrue(clickNode("Fork destination"))
            assertTrue(awaitValue { selectedForkCount.get() == 2 })
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun graphRendersCurrentNodeAndFallbackFavicon() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val tab = BrowserTab(
            id = "tab",
            lastAccessedAt = 1L,
            title = "Current tab",
            url = "https://b.example",
        )
        val trail = CandyTrail(
            tabId = tab.id,
            nodes = List(64) { index ->
                node(
                    id = "n$index",
                    parentId = if (index == 0) null else "n${index - 1}",
                    title = "Page $index",
                    url = "https://page$index.example",
                    at = index.toLong(),
                )
            },
            currentNodeId = "n63",
            nextOrdinal = 64L,
            forks = listOf(
                CandyTrailFork(
                    id = "f0",
                    originTabId = tab.id,
                    originNodeId = "n63",
                    destinationTabId = "destination",
                    profileId = tab.profileId,
                    isIncognito = false,
                    url = "https://fork.example",
                    title = "Fork destination",
                    createdAt = 65L,
                    updatedAt = 65L,
                    lifecycle = CandyTrailForkLifecycle.Open,
                ),
            ),
        )

        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    MaterialBrowserTheme {
                        CandyTrailScreen(
                            tab = tab,
                            trail = trail,
                            favicon = null,
                            forkFavicons = emptyMap(),
                            sourceBounds = null,
                            predictiveBackProgress = 0f,
                            predictiveBackEdgeSign = 1,
                            onOpenTabActions = {},
                            onSelectNode = {},
                            onForkNode = { false },
                            onSelectFork = { false },
                            onDismiss = {},
                        )
                    }
                }
            }
            instrumentation.waitForIdleSync()
            assertTrue("Current node is outside the initial viewport", currentNodeIsVisible())
            assertTrue("Fork endpoint lacks accessibility semantics", nodeExists("Fork destination"))
            instrumentation.runOnMainSync {
                val root = activity.window.decorView
                assertTrue(root.width > 0 && root.height > 0)
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                assertTrue(bitmap.hasVisualVariation())
                bitmap.recycle()
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    private fun currentNodeIsVisible(): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(40) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val pending = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            if (root != null) pending += root
            val matchingNodes = buildList {
                while (pending.isNotEmpty()) {
                    val node = pending.removeFirst()
                    if (
                        node.text?.contains("Page 63") == true ||
                        node.contentDescription?.contains("Page 63") == true
                    ) {
                        add(node)
                    }
                    repeat(node.childCount) { childIndex ->
                        node.getChild(childIndex)?.let(pending::addLast)
                    }
                }
            }
            val visible = matchingNodes.any { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.width() > 0 && bounds.height() > 0 &&
                    bounds.left < instrumentation.targetContext.resources.displayMetrics.widthPixels &&
                    bounds.top < instrumentation.targetContext.resources.displayMetrics.heightPixels &&
                    bounds.right > 0 && bounds.bottom > 0
            }
            if (visible) return true
            SystemClock.sleep(50)
        }
        return false
    }

    private fun nodeExists(text: String): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(40) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val pending = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            if (root != null) pending += root
            while (pending.isNotEmpty()) {
                val node = pending.removeFirst()
                if (node.text?.contains(text) == true || node.contentDescription?.contains(text) == true) {
                    return true
                }
                repeat(node.childCount) { childIndex ->
                    node.getChild(childIndex)?.let(pending::addLast)
                }
            }
            SystemClock.sleep(50)
        }
        return false
    }

    private fun clickNode(text: String): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(40) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val pending = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            if (root != null) pending += root
            while (pending.isNotEmpty()) {
                val node = pending.removeFirst()
                if (node.text?.contains(text) == true || node.contentDescription?.contains(text) == true) {
                    var clickableNode: android.view.accessibility.AccessibilityNodeInfo? = node
                    while (clickableNode != null && !clickableNode.isClickable) {
                        clickableNode = clickableNode.parent
                    }
                    if (clickableNode?.performAction(
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,
                        ) == true
                    ) {
                        instrumentation.waitForIdleSync()
                        return true
                    }
                }
                repeat(node.childCount) { childIndex ->
                    node.getChild(childIndex)?.let(pending::addLast)
                }
            }
            SystemClock.sleep(50)
        }
        return false
    }

    private fun awaitValue(predicate: () -> Boolean): Boolean {
        repeat(40) {
            if (predicate()) return true
            SystemClock.sleep(50)
        }
        return false
    }

    private fun Bitmap.hasVisualVariation(): Boolean {
        val colors = mutableSetOf<Int>()
        val xStep = (width / 12).coerceAtLeast(1)
        val yStep = (height / 20).coerceAtLeast(1)
        for (x in 0 until width step xStep) {
            for (y in 0 until height step yStep) colors += getPixel(x, y)
        }
        return colors.size >= 4
    }

    private fun node(id: String, parentId: String?, title: String, url: String, at: Long) =
        CandyTrailNode(id, parentId, url, title, at)
}
